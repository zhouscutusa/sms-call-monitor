package com.example.smscallmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*; // 引入网络相关类
import android.net.wifi.WifiManager; // 引入 WifiManager
import android.os.*;
// 移除 Handler 和 Looper
import android.provider.Telephony; // 引入 Telephony 用于 Action
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.work.ExistingPeriodicWorkPolicy; // 引入 WorkManager 类
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService; // 引入 ExecutorService
import java.util.concurrent.Executors;     // 引入 Executors
import java.util.concurrent.TimeUnit;

// 主要的服务类，负责监听短信、来电，管理 Wi-Fi 和触发邮件发送
public class MonitorService extends Service {
    // --- 保留原始常量和 TAG ---
    private static final String TAG = "MonitorService";
    public static final String ACTION_START_MONITORING = "com.example.smscallmonitor.action.START_MONITORING";

    // --- 保留 AlarmManager 相关变量 ---
    private AlarmManager alarmManager;
    private PendingIntent wifiOffPendingIntent;

    // --- 保留 Listener 和 CallSession 相关变量 ---
    private SubscriptionManager subscriptionManager;
    private Map<Integer, SimPhoneStateListener> phoneStateListeners = new HashMap<>();
    private Map<Integer, TelephonyManager> telephonyManagers = new HashMap<>();
    private final Map<Integer, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, Long> processedMissedCalls = new ConcurrentHashMap<>();
    private static class CallSession { String incomingNumber; boolean isRinging = false; boolean isOffhook = false; long ringStartTime = 0; }
    // 移除主线程 Handler

    // --- 修改: 需要 WifiManager 和 ConnectivityManager 用于立即发送尝试 ---
    private PendingEventDao pendingEventDao;      // 数据库访问对象
    private ExecutorService databaseExecutor;     // 后台数据库操作线程池
    private WifiManager wifiManager;              // Wi-Fi 管理器实例
    private ConnectivityManager connectivityManager; // 网络连接管理器实例
    // --- 修改结束 ---


    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        // 更新日志，指明当前使用的是混合发送逻辑
        Log.d(TAG, ">>> MonitorService onCreate (Immediate Send Attempt + Periodic Retry - Final)"); // 更新日志
        subscriptionManager = (SubscriptionManager) getApplicationContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        // --- 初始化 AlarmManager (保持不变) ---
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, WifiOffReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        wifiOffPendingIntent = PendingIntent.getBroadcast(this, IConstants.WIFI_OFF_ALARM_REQUEST_CODE, intent, flags);
        Log.d(TAG, ">>> AlarmManager and PendingIntent initialized.");

        // --- 修改: 初始化数据库、线程池、网络管理器和安排 Worker ---
        Log.d(TAG, ">>> Initializing Database, Executor, Network Managers, and Scheduling Worker...");
        pendingEventDao = AppDatabase.getDatabase(this).pendingEventDao();
        databaseExecutor = Executors.newSingleThreadExecutor();
        // *** 需要初始化 WifiManager 和 ConnectivityManager ***
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        // *** 初始化结束 ***
        schedulePeriodicConsolidatedSendWork(); // 只安排周期性 Worker
        Log.d(TAG, ">>> Database, Executor, Network Managers, and Worker Scheduling initialized.");
        // --- 修改结束 ---

        // --- 移除 Debounce Runnable 的初始化 ---

        // --- 创建通知渠道和前台服务通知 (保持不变) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("channel", "监控服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
        Notification notification = new NotificationCompat.Builder(this, "channel")
                .setContentTitle("短信来电监控中").setSmallIcon(R.mipmap.ic_launcher).build();
        startForeground(1, notification);

        // --- 注册 SIM 卡变化监听器 (修改: 直接调用) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (subscriptionManager != null) {
                    Log.d(TAG, ">>> Adding OnSubscriptionsChangedListener");
                    subscriptionManager.addOnSubscriptionsChangedListener(subscriptionsChangedListener);
                } else {
                    Log.e(TAG, ">>> SubscriptionManager is null in onCreate...");
                }
            } else {
                Log.w(TAG, ">>> READ_PHONE_STATE permission not granted in onCreate...");
            }
        }
        Log.d(TAG, ">>> MonitorService onCreate completed.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, ">>> MonitorService onStartCommand");
        if (intent != null) {
            String action = intent.getAction();
            Log.d(TAG, ">>> Action received: " + action);

            if (ACTION_START_MONITORING.equals(action)) {
                Log.i(TAG,">>> onStartCommand: Handling ACTION_START_MONITORING - Calling startMonitoring()...");
                startMonitoring();
                Log.d(TAG, ">>> Scheduling initial Wi-Fi off alarm on service start.");
                EventSendHelper.resetWifiOffSchedule(alarmManager, wifiOffPendingIntent);
            } else if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(action)) {
                Log.d(TAG,">>> onStartCommand: Handling SMS_RECEIVED_ACTION (from SmsReceiver)");
                handleSmsReceivedIntent(intent); // 处理短信事件
            } else {
                Log.w(TAG, ">>> onStartCommand received unhandled action: " + action);
            }
        } else {
            Log.d(TAG, ">>> Service restarted with null intent (flags=" + flags + ", startId=" + startId + ")");
        }
        return START_STICKY;
    }

    // --- 处理来自 SmsReceiver 的 Intent 的方法 (修改: 调用新的处理逻辑) ---
    private void handleSmsReceivedIntent(Intent intent) {
        String sender = intent.getStringExtra("sender");
        String content = intent.getStringExtra("content");
        int subId = intent.getIntExtra("subId", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        long timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis());

        if (sender != null && content != null) {
            String simInfo = getSimInfo(this, subId);
            Log.d(TAG, ">>> Processing SMS event on " + simInfo + " from " + sender);
            PendingEvent newSmsEvent = new PendingEvent("SMS", sender, content, timestamp, simInfo, subId);
            // 调用新的处理方法，尝试立即发送，失败则保存
            handleNewEvent(newSmsEvent);
        } else {
            Log.w(TAG, ">>> Received SMS intent with null sender or content. Cannot process.");
        }
    }

    // --- startMonitoring 方法保持不变 ---
    private void startMonitoring() {
        Log.i(TAG, ">>> startMonitoring called.");
        stopMonitoring();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, ">>> READ_PHONE_STATE permission not granted in startMonitoring. Cannot monitor calls.");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) {
            List<SubscriptionInfo> activeSubscriptions = null;
            try {
                activeSubscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                Log.d(TAG, ">>> Fetched active subscriptions.");
            } catch (SecurityException e) { Log.e(TAG, ">>> SecurityException getting active subscriptions: " + e.getMessage()); return; }

            if (activeSubscriptions != null && !activeSubscriptions.isEmpty()) {
                Log.i(TAG, ">>> Found " + activeSubscriptions.size() + " active subscriptions.");
                for (SubscriptionInfo subInfo : activeSubscriptions) {
                    int subId = subInfo.getSubscriptionId();
                    CharSequence displayNameCs = subInfo.getDisplayName();
                    String displayName = (displayNameCs != null ? displayNameCs.toString() : "SIM Slot " + subInfo.getSimSlotIndex());
                    Log.d(TAG, ">>> Setting up listener for SubId: " + subId + " (Name: " + displayName + ")");
                    TelephonyManager tm = null;
                    try { tm = ((TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE)).createForSubscriptionId(subId); }
                    catch (Exception e) { Log.e(TAG, ">>> Exception creating TelephonyManager for subId " + subId + ": " + e.getMessage()); }
                    if (tm != null) {
                        SimPhoneStateListener listener = new SimPhoneStateListener(subId, displayName); // **创建 Listener 实例**
                        try {
                            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
                            phoneStateListeners.put(subId, listener);
                            telephonyManagers.put(subId, tm);
                            Log.i(TAG, ">>> Successfully registered listener for SubId: " + subId);
                        } catch (Exception e) { Log.e(TAG, ">>> Exception registering listener for subId " + subId + ": " + e.getMessage()); }
                    } else { Log.e(TAG, ">>> Failed to create TelephonyManager for SubId: " + subId); }
                }
            } else { Log.w(TAG, ">>> No active subscriptions found or accessible in startMonitoring."); }
        } else { Log.w(TAG, ">>> SubscriptionManager not available or permission denied in startMonitoring."); }
    }

    // --- stopMonitoring 方法保持不变 ---
    private void stopMonitoring() {
        Log.d(TAG, ">>> Stopping phone state monitoring...");
        if (!telephonyManagers.isEmpty()) {
            for (Map.Entry<Integer, TelephonyManager> entry : telephonyManagers.entrySet()) {
                int subId = entry.getKey();
                TelephonyManager tm = entry.getValue();
                SimPhoneStateListener listener = phoneStateListeners.get(subId);
                if (tm != null && listener != null) {
                    try { tm.listen(listener, PhoneStateListener.LISTEN_NONE); Log.d(TAG, ">>> Unregistered listener for SubId: " + subId); }
                    catch (Exception e) { Log.e(TAG,">>> Exception unregistering listener for subId " + subId + ": " + e.getMessage());}
                }
            }
            telephonyManagers.clear();
            phoneStateListeners.clear();
            activeCalls.clear();
            processedMissedCalls.clear();
            Log.d(TAG, ">>> Phone state monitoring stopped and resources cleared.");
        } else {
            Log.d(TAG, ">>> No active phone state listeners to stop.");
        }
    }

    // --- subscriptionsChangedListener (修改: 移除 Handler.post) ---
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionsChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override public void onSubscriptionsChanged() {
                    Log.i(TAG, ">>> Subscription change detected! Restarting phone state monitoring.");
                    startMonitoring();
                }
            };

    // --- handleMissedCall 方法修改 ---
    public void handleMissedCall(String incomingNumber, int subId, String simDisplayName) {
        Log.i(TAG, ">>> handleMissedCall triggered for SubId: " + subId + ", Number: " + incomingNumber + ", Name: " + simDisplayName);
        String callKey = subId + "_" + incomingNumber;
        long now = System.currentTimeMillis();
        Long lastProcessedTime = processedMissedCalls.getOrDefault(callKey, 0L);

        if (now - lastProcessedTime > IConstants.MISSED_CALL_DEBOUNCE_MS) {
            processedMissedCalls.put(callKey, now);
            Log.i(TAG, ">>> Confirmed Missed Call (passed debounce) on " + simDisplayName + " from: " + incomingNumber);

            // --- 修改: 调用新的处理逻辑 ---
            String simInfo = getSimInfo(this, subId);
            PendingEvent newCallEvent = new PendingEvent("CALL", incomingNumber, null, now, simInfo, subId);
            handleNewEvent(newCallEvent); // 调用新的处理方法
            Log.d(TAG, ">>> Missed call event processed for " + incomingNumber); // 更新日志
            // --- 修改结束 ---

        } else {
            Log.d(TAG, ">>> Debounced duplicate missed call event for key: " + callKey);
        }
    }

    // --- SimPhoneStateListener 内部类 (确保定义和调用正确) ---
    private class SimPhoneStateListener extends PhoneStateListener {
        private final int subId; // 监听器关联的 Subscription ID
        private final String simDisplayName; // **用于日志和传递的 SIM 显示名称**

        // 构造函数
        SimPhoneStateListener(int subId, String displayName){
            super();
            this.subId = subId;
            this.simDisplayName = displayName; // **初始化成员变量**
            activeCalls.putIfAbsent(subId, new CallSession());
            Log.d(TAG, ">>> SimPhoneStateListener created for SubId: " + this.subId + " Name: " + this.simDisplayName);
        }

        // 当通话状态改变时被系统调用
        @Override
        public void onCallStateChanged(int state, String number) {
            // **使用 this. 来引用成员变量，更清晰**
            Log.i(TAG, ">>> SimPhoneStateListener.onCallStateChanged triggered! SubId: " + this.subId + " ("+ this.simDisplayName +"), State: " + stateToString(state) + ", Number: " + number);
            String incomingNumber = number;
            Log.d(TAG, ">>> Listener for SubId: " + this.subId + " - State changed: " + stateToString(state) + ", Incoming Number: " + incomingNumber);
            CallSession session = activeCalls.get(this.subId);
            if (session == null) {
                session = new CallSession();
                activeCalls.put(this.subId, session);
                Log.w(TAG, ">>> CallSession was null for subId " + this.subId + ", created new.");
            }
            if (incomingNumber != null && !incomingNumber.isEmpty() && !incomingNumber.equals(session.incomingNumber)) {
                session.incomingNumber = incomingNumber;
                Log.d(TAG, ">>> Updating number for subId " + this.subId + " to: " + incomingNumber);
            }

            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    session.isRinging = true;
                    session.isOffhook = false;
                    session.ringStartTime = System.currentTimeMillis();
                    Log.d(TAG, ">>> RINGING on " + this.simDisplayName + ", Number known: " + session.incomingNumber);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (session.isRinging || session.ringStartTime > 0) {
                        session.isOffhook = true;
                        Log.d(TAG, ">>> OFFHOOK on " + this.simDisplayName + " - Call answered.");
                    } else {
                        Log.d(TAG, ">>> OFFHOOK on " + this.simDisplayName + " - Not ringing on this SIM (Outgoing call or other state).");
                    }
                    session.isRinging = false;
                    session.ringStartTime = 0;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    Log.d(TAG, ">>> IDLE on " + this.simDisplayName + " - Check Missed Call: RingingStarted=" + (session.ringStartTime > 0) + ", Offhook=" + session.isOffhook + ", Number=" + session.incomingNumber);
                    if (session.ringStartTime > 0 && !session.isOffhook && session.incomingNumber != null && !session.incomingNumber.isEmpty()) {
                        Log.i(TAG, ">>> Missed call condition met for SubId: " + this.subId + ". Calling handleMissedCall...");
                        // **确保这里传递的是 this.simDisplayName**
                        handleMissedCall(session.incomingNumber, this.subId, this.simDisplayName); // *** 调用外部类的 handleMissedCall ***
                    } else {
                        Log.d(TAG, ">>> IDLE on " + this.simDisplayName + " - Not a missed call.");
                    }
                    activeCalls.put(this.subId, new CallSession());
                    Log.d(TAG,">>> Reset call session for subId: " + this.subId);
                    break;
            }
        }

        private String stateToString(int state) {
            switch (state) {
                case TelephonyManager.CALL_STATE_IDLE: return "IDLE";
                case TelephonyManager.CALL_STATE_RINGING: return "RINGING";
                case TelephonyManager.CALL_STATE_OFFHOOK: return "OFFHOOK";
                default: return "UNKNOWN_" + state;
            }
        }
    } // SimPhoneStateListener 内部类结束


    // --- 修改: handleNewEvent 方法，实现立即发送失败后暂存逻辑 ---
    /**
     * 处理新的事件（SMS 或 CALL）。
     * 尝试立即发送，如果失败则保存到数据库。
     * @param event 新的事件对象
     */
    private void handleNewEvent(PendingEvent event) {
        Log.d(TAG, ">>> Handling new event: Type=" + event.eventType + ", From=" + event.senderNumber);
        // 1. 重置 Wi-Fi 关闭计时器 (确保有时间尝试发送)
        EventSendHelper.resetWifiOffSchedule(alarmManager, wifiOffPendingIntent);
        Log.d(TAG, ">>> Wi-Fi off schedule reset for new event.");

        // 2. 使用后台线程尝试立即发送
        databaseExecutor.execute(() -> { // 确保网络和邮件操作不在主线程
            EventSendHelper.SendStatus sendStatus = EventSendHelper.SendStatus.SEND_FAILED_OTHER; // 默认状态为失败
            try {
                // 调用 EventSendHelper 尝试立即发送单个事件，并获取返回状态
                Log.d(TAG, ">>> Attempting immediate send via EventSendHelper..."); // 添加日志
                sendStatus = EventSendHelper.trySendSingleEventImmediately(
                        getApplicationContext(),
                        event,
                        wifiManager, // 传递 MonitorService 的成员变量
                        connectivityManager // 传递 MonitorService 的成员变量
                );
            } catch (Exception e) {
                Log.e(TAG, ">>> Exception during immediate send attempt: " + e.getMessage(), e);
                // 即使发送尝试本身抛出异常，也视为发送失败 (状态保持默认的 SEND_FAILED_OTHER)
            }

            // 3. 如果立即发送失败 (任何类型的失败)，则保存到数据库
            if (sendStatus != EventSendHelper.SendStatus.SEND_SUCCESS) {
                Log.w(TAG, ">>> Immediate send failed (Status: " + sendStatus + ") for event: Type=" + event.eventType + ", From=" + event.senderNumber + ". Saving to database for later retry.");
                try {
                    Log.d(TAG, ">>> Inserting failed event into database..."); // 添加日志
                    pendingEventDao.insert(event); // 将事件插入数据库
                    Log.i(TAG, ">>> Successfully saved failed event to DB.");
                } catch (Exception dbException) {
                    Log.e(TAG, ">>> CRITICAL: Failed to save event to DB after send failure: " + dbException.getMessage(), dbException);
                    // 这种情况比较严重，事件可能会丢失，需要关注此日志
                }
            } else {
                Log.i(TAG, ">>> Event sent immediately successfully: Type=" + event.eventType + ", From=" + event.senderNumber);
                // 发送成功，不需要进行数据库插入操作
                // 发送成功，则尝试把数据库里有的记录也发了
                EventSendHelper.performConsolidatedSend(getApplicationContext(), pendingEventDao, wifiManager, connectivityManager);
            }
        }); // databaseExecutor.execute 结束
    }
    // --- 修改结束 ---



    // --- WorkManager 相关方法 (保持不变 - 只安排周期性任务) ---
    private void schedulePeriodicConsolidatedSendWork() {
        Log.d(TAG, ">>> Scheduling Periodic Consolidated Send Work...");
        int scheduledInterval = IConstants.SCHEDULED_WORK_INTERVAL_MINUTE;
        PeriodicWorkRequest retryWorkRequest =
                new PeriodicWorkRequest.Builder(ConsolidatedSendWorker.class, scheduledInterval, TimeUnit.MINUTES)
                        .build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "ConsolidatedSendWork", // 周期性任务的唯一名称
                ExistingPeriodicWorkPolicy.KEEP,
                retryWorkRequest);
        Log.i(TAG, ">>> Enqueued periodic consolidated send work (every " + scheduledInterval + " minutes).");
    }

    private void cancelPeriodicConsolidatedSendWork() {
        try {
            WorkManager.getInstance(this).cancelUniqueWork("ConsolidatedSendWork");
            Log.i(TAG, ">>> Cancelled periodic consolidated send work.");
        } catch (Exception e) {
            Log.e(TAG, ">>> Error cancelling periodic consolidated send work: " + e.getMessage());
        }
    }
    // --- 修改结束 ---

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // --- onDestroy 方法修改 ---
    @Override
    public void onDestroy() {
        Log.d(TAG, ">>> MonitorService onDestroy");
        stopMonitoring();
        EventSendHelper.cancelWifiOffAlarm(alarmManager, wifiOffPendingIntent);

        // --- 移除 SIM 卡变化监听器 (保持不变) ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) {
            try {
                subscriptionManager.removeOnSubscriptionsChangedListener(subscriptionsChangedListener);
                Log.d(TAG,">>> Removed subscription listener.");
            } catch (Exception e) { Log.e(TAG, ">>> Error removing subscription listener: " + e.getMessage()); }
        }

        // --- 关闭数据库操作的线程池 (保持不变) ---
        if (databaseExecutor != null && !databaseExecutor.isShutdown()) {
            try {
                databaseExecutor.shutdown();
                Log.d(TAG, ">>> Database executor shutdown requested.");
            } catch (Exception e) {
                Log.e(TAG, ">>> Error shutting down database executor: " + e.getMessage());
            }
        }

        // --- 移除 Debounce Handler 的清理 ---

        // --- 取消周期性任务 (保持不变) ---
        cancelPeriodicConsolidatedSendWork();
        // --- 移除取消一次性任务的代码 ---


        stopForeground(true);
        super.onDestroy();
        Log.d(TAG, ">>> MonitorService onDestroy completed.");
    }

    // --- getSimInfo 方法保持不变 ---
    private String getSimInfo(Context context, int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return "未知SIM卡";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, ">>> getSimInfo: No READ_PHONE_STATE permission.");
            SimPhoneStateListener l = phoneStateListeners.get(subId);
            return (l != null) ? l.simDisplayName + " (ID: " + subId + ")" : "SIM ID " + subId;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager sm = this.subscriptionManager;
                if (sm == null) {
                    sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                }
                if (sm != null) {
                    SubscriptionInfo si = null;
                    try {
                        si = sm.getActiveSubscriptionInfo(subId);
                    } catch (SecurityException e) {
                        Log.e(TAG,">>> SecEx getting SubInfo "+subId+": "+e.getMessage());
                    }
                    if (si != null) {
                        CharSequence cs = si.getDisplayName();
                        String dn = (cs!=null && cs.length() > 0 ? cs.toString() : "SIM "+(si.getSimSlotIndex()+1));
                        return dn+" (ID: "+subId+")";
                    } else {
                        List<SubscriptionInfo> asl = null;
                        try { asl = sm.getActiveSubscriptionInfoList(); } catch (SecurityException ignored) {}
                        if (asl != null) {
                            for(SubscriptionInfo i : asl) {
                                if (i.getSubscriptionId() == subId){
                                    CharSequence cs=i.getDisplayName();
                                    String dn=(cs!=null && cs.length() > 0 ? cs.toString() : "SIM "+(i.getSimSlotIndex()+1));
                                    return dn+" (ID: "+subId+")";
                                }
                            }
                        }
                        Log.w(TAG, ">>> Could not find active SubInfo for subId: " + subId + " using SubscriptionManager.");
                    }
                } else {
                    Log.w(TAG, ">>> getSimInfo: SubscriptionManager instance is null.");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, ">>> General exception in getSimInfo: " + e.getMessage());
        }
        SimPhoneStateListener l = phoneStateListeners.get(subId);
        Log.w(TAG, ">>> Falling back to listener display name for subId: " + subId);
        return (l != null) ? l.simDisplayName + " (ID: " + subId + ")" : "SIM ID " + subId;
    }
} // MonitorService 类结束