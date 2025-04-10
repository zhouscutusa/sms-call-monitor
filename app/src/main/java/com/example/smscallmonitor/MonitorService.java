package com.example.smscallmonitor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit; // 用于时间转换

public class MonitorService extends Service {
    private static final String TAG = "MonitorService";
    public static final String ACTION_START_MONITORING = "com.example.smscallmonitor.action.START_MONITORING";
    public static final String ACTION_PROCESS_SMS = "PROCESS_SMS";

    private static final long WIFI_OFF_DELAY_MS = TimeUnit.MINUTES.toMillis(5); // 5分钟
    private static final int WAIT_WIFI_TIME = 35 * 1000;
    private static final long MISSED_CALL_DEBOUNCE_MS = 10000;

    // AlarmManager 相关
    private AlarmManager alarmManager;
    private PendingIntent wifiOffPendingIntent;
    public static final int WIFI_OFF_ALARM_REQUEST_CODE = 99;
    public static final String WIFI_OFF_ACTION = "com.example.smscallmonitor.action.TURN_WIFI_OFF"; // Action for PendingIntent

    // Listener Management 和 CallSession
    private SubscriptionManager subscriptionManager;
    private Map<Integer, SimPhoneStateListener> phoneStateListeners = new HashMap<>();
    private Map<Integer, TelephonyManager> telephonyManagers = new HashMap<>();
    private final Map<Integer, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Map<String, Long> processedMissedCalls = new ConcurrentHashMap<>();
    private static class CallSession {
        String incomingNumber; boolean isRinging = false; boolean isOffhook = false; long ringStartTime = 0;
    }
    // Handler for subscription changes posting (can use default Handler if preferred)
    private Handler handler = new Handler(Looper.getMainLooper());


    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, ">>> MonitorService onCreate (Aggressive Wifi Mode)");
        subscriptionManager = (SubscriptionManager) getApplicationContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        // 初始化 AlarmManager 和 PendingIntent
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, WifiOffReceiver.class);
        // intent.setAction(WIFI_OFF_ACTION); // Action is optional here
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        wifiOffPendingIntent = PendingIntent.getBroadcast(this, WIFI_OFF_ALARM_REQUEST_CODE, intent, flags);
        Log.d(TAG, ">>> AlarmManager and PendingIntent initialized.");

        // 创建 Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("channel", "监控服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        // 创建 Notification
        Notification notification = new NotificationCompat.Builder(this, "channel")
                .setContentTitle("短信来电监控中").setSmallIcon(R.mipmap.ic_launcher).build();
        startForeground(1, notification);

        // 注册 OnSubscriptionsChangedListener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                if (subscriptionManager != null) { Log.d(TAG, ">>> Adding OnSubscriptionsChangedListener"); subscriptionManager.addOnSubscriptionsChangedListener(subscriptionsChangedListener); }
                else { Log.e(TAG, ">>> SubscriptionManager is null in onCreate..."); }
            } else { Log.w(TAG, ">>> READ_PHONE_STATE permission not granted in onCreate..."); }
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
                // 可选: 服务启动时也设置一个初始的关闭闹钟
                Log.d(TAG, ">>> Scheduling initial Wi-Fi off alarm on service start.");
                resetWifiOffSchedule();
            } else if (ACTION_PROCESS_SMS.equals(action)) {
                Log.d(TAG,">>> onStartCommand: Handling ACTION_PROCESS_SMS");
                String sender = intent.getStringExtra("sender"); String content = intent.getStringExtra("content");
                int subId = intent.getIntExtra("subId", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                String simInfo = getSimInfo(this, subId);
                Log.d(TAG, ">>> Processing SMS on " + simInfo + " from " + sender);
                sendNotification("短信 (" + simInfo + ")", sender + ": " + content);
            } else {
                Log.w(TAG, ">>> onStartCommand received unhandled action: " + action);
            }
        } else {
            Log.d(TAG, ">>> Service restarted with null intent (flags=" + flags + ", startId=" + startId + ")");
            // 当服务被系统重启时，可以考虑也重置一次闹钟
            // resetWifiOffSchedule();
        }
        return START_STICKY;
    }

    // --- startMonitoring ---
    private void startMonitoring() {
        Log.i(TAG, ">>> startMonitoring called.");
        stopMonitoring(); // 先停止旧的监听
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
                    Log.d(TAG, ">>>   - SubId: " + subInfo.getSubscriptionId() + ", Slot: " + subInfo.getSimSlotIndex() + ", Name: " + subInfo.getDisplayName());
                    int subId = subInfo.getSubscriptionId();
                    CharSequence displayNameCs = subInfo.getDisplayName();
                    String displayName = (displayNameCs != null ? displayNameCs.toString() : "SIM Slot " + subInfo.getSimSlotIndex());
                    Log.d(TAG, ">>> Setting up listener for SubId: " + subId + "...");

                    TelephonyManager tm = null;
                    try { tm = ((TelephonyManager) getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE)).createForSubscriptionId(subId); }
                    catch (Exception e) { Log.e(TAG, ">>> Exception creating TelephonyManager for subId " + subId + ": " + e.getMessage()); }

                    if (tm != null) {
                        SimPhoneStateListener listener = new SimPhoneStateListener(subId, displayName);
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

    // --- stopMonitoring ---
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
            telephonyManagers.clear(); phoneStateListeners.clear(); activeCalls.clear(); processedMissedCalls.clear();
        }
    }

    // --- subscriptionsChangedListener ---
    private final SubscriptionManager.OnSubscriptionsChangedListener subscriptionsChangedListener =
            new SubscriptionManager.OnSubscriptionsChangedListener() {
                @Override public void onSubscriptionsChanged() {
                    handler.post(() -> { Log.i(TAG, ">>> Subscription change detected! Restarting monitoring."); startMonitoring(); });
                }
            };

    // --- handleMissedCall ---
    public void handleMissedCall(String incomingNumber, int subId, String simDisplayName) {
        Log.i(TAG, ">>> handleMissedCall triggered for SubId: " + subId + ", Number: " + incomingNumber + ", Name: " + simDisplayName);
        String callKey = subId + "_" + incomingNumber; long now = System.currentTimeMillis();
        Long lastProcessedTime = processedMissedCalls.getOrDefault(callKey, 0L);
        if (now - lastProcessedTime > MISSED_CALL_DEBOUNCE_MS) {
            processedMissedCalls.put(callKey, now);
            Log.i(TAG, ">>> Confirmed Missed Call (passed debounce) on " + simDisplayName + " from: " + incomingNumber);
            sendNotification("未接来电 (" + simDisplayName + ")", "号码: " + incomingNumber);
        } else { Log.d(TAG, ">>> Debounced duplicate missed call event for key: " + callKey); }
    }

    // --- SimPhoneStateListener ---
    private class SimPhoneStateListener extends PhoneStateListener {
        private final int subId; private final String simDisplayName;
        SimPhoneStateListener(int subId, String displayName){
            super(); this.subId = subId; this.simDisplayName = displayName;
            activeCalls.putIfAbsent(subId, new CallSession());
            Log.d(TAG, ">>> SimPhoneStateListener created for SubId: " + subId + " Name: " + simDisplayName);
        }
        @Override public void onCallStateChanged(int state, String number) {
            Log.i(TAG, ">>> SimPhoneStateListener.onCallStateChanged triggered! SubId: " + subId + " ("+ simDisplayName +"), State: " + stateToString(state) + ", Number: " + number);
            String incomingNumber = number;
            Log.d(TAG, ">>> Listener for SubId: " + subId + " - State changed: " + stateToString(state) + ", Incoming Number: " + incomingNumber);
            CallSession session = activeCalls.get(subId);
            if (session == null) { session = new CallSession(); activeCalls.put(subId, session); Log.w(TAG, ">>> CallSession was null for subId " + subId + ", created new.");}
            if (incomingNumber != null && !incomingNumber.isEmpty() && !incomingNumber.equals(session.incomingNumber)) { session.incomingNumber = incomingNumber; Log.d(TAG, ">>> Updating number for subId " + subId + " to: " + incomingNumber);}
            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING: session.isRinging = true; session.isOffhook = false; session.ringStartTime = System.currentTimeMillis(); Log.d(TAG, ">>> RINGING on " + simDisplayName + ", Number known: " + session.incomingNumber); break;
                case TelephonyManager.CALL_STATE_OFFHOOK: if (session.isRinging || session.ringStartTime > 0) { session.isOffhook = true; Log.d(TAG, ">>> OFFHOOK on " + simDisplayName + " - Call answered.");} else { Log.d(TAG, ">>> OFFHOOK on " + simDisplayName + " - Not ringing on this SIM."); } session.isRinging = false; session.ringStartTime = 0; break;
                case TelephonyManager.CALL_STATE_IDLE: Log.d(TAG, ">>> IDLE on " + simDisplayName + " - Check Missed Call: RingingStarted=" + (session.ringStartTime > 0) + ", Offhook=" + session.isOffhook + ", Number=" + session.incomingNumber); if (session.ringStartTime > 0 && !session.isOffhook && session.incomingNumber != null && !session.incomingNumber.isEmpty()) { Log.i(TAG, ">>> Missed call condition met for SubId: " + subId + ". Calling handleMissedCall..."); handleMissedCall(session.incomingNumber, subId, simDisplayName); } else { Log.d(TAG, ">>> IDLE on " + simDisplayName + " - Not a missed call."); } activeCalls.put(subId, new CallSession()); Log.d(TAG,">>> Reset call session for subId: " + subId); break;
            }
        }
        private String stateToString(int state) {
            switch (state) { case TelephonyManager.CALL_STATE_IDLE: return "IDLE"; case TelephonyManager.CALL_STATE_RINGING: return "RINGING"; case TelephonyManager.CALL_STATE_OFFHOOK: return "OFFHOOK"; default: return "UNKNOWN_" + state; }
        }
    }

    // --- sendNotification (Aggressive Mode) ---
    private void sendNotification(String subject, String body) {
        Log.i(TAG, ">>> sendNotification called (Aggressive Wifi Mode). Subject: " + subject);

        // 每次事件发生，都重置Wi-Fi关闭计划
        Log.d(TAG, ">>> Resetting Wi-Fi off schedule due to new event.");
        resetWifiOffSchedule(); // 无论Wi-Fi是否打开，都重置计时器

        new Thread(() -> {
            try {
                Log.i(TAG, ">>> Background thread in sendNotification started.");
                WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

                // 尝试打开Wi-Fi以发送邮件
                if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                    Log.d(TAG, ">>> Wi-Fi is disabled. Checking CHANGE_WIFI_STATE permission...");
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, ">>> CHANGE_WIFI_STATE permission is GRANTED.");
                            try {
                                Log.d(TAG, ">>> Attempting to call wifiManager.setWifiEnabled(true)...");
                                wifiManager.setWifiEnabled(true);
                                Log.d(TAG, ">>> Call to wifiManager.setWifiEnabled(true) completed.");
                            } catch (Exception e) { Log.e(TAG,"Error enabling wifi: "+e.getMessage());}
                        } else { Log.w(TAG, ">>> CHANGE_WIFI_STATE permission is DENIED."); }
                    } else { Log.w(TAG, ">>> Cannot enable Wi-Fi on Android 10+."); }
                } else if (wifiManager != null) { Log.d(TAG, ">>> Wi-Fi is already enabled."); }
                else { Log.e(TAG, ">>> WifiManager is null."); }

                // 发送邮件
                Log.d(TAG, ">>> Waiting for network connection...");
                if (waitUntilConnected()) {
                    Log.i(TAG, ">>> Network connected. Sending email using EmailSender.send()...");
                    try { EmailSender.send(subject, body); }
                    catch (Exception e) { Log.e(TAG, ">>> Exception caught calling EmailSender.send: " + e.getMessage(), e); System.err.println("❌ 邮件发送时发生意外错误：" + e.getMessage());}
                } else { Log.e(TAG, ">>> Network connection timeout. Email not sent."); System.out.println("❌ 超时：网络未连接，无法发送邮件"); }
            } catch (Throwable t) {
                Log.e(TAG, ">>> !!! UNCAUGHT ERROR IN sendNotification THREAD !!! <<<", t);
            }
        }).start();
    }

    // --- resetWifiOffSchedule ---
    private void resetWifiOffSchedule() {
        Log.d(TAG, ">>> Resetting Wi-Fi turn-off schedule (Cancelling and rescheduling alarm).");
        cancelWifiOffAlarm(); // 先取消旧的
        scheduleWifiOffAlarm(); // 再设置新的
    }

    // --- scheduleWifiOffAlarm ---
    private void scheduleWifiOffAlarm() {
        if (alarmManager != null && wifiOffPendingIntent != null) {
            long triggerAtMillis = System.currentTimeMillis() + WIFI_OFF_DELAY_MS;
            try {
                // 使用 set 以允许系统优化，对于非精确关闭场景足够
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, wifiOffPendingIntent);
                Log.i(TAG, ">>> Scheduled Wi-Fi off alarm for " + WIFI_OFF_DELAY_MS + "ms from now.");
            } catch (SecurityException se) { Log.e(TAG, ">>> SecurityException scheduling Wi-Fi off alarm: " + se.getMessage());}
            catch (Exception e) { Log.e(TAG, ">>> Error scheduling Wi-Fi off alarm: " + e.getMessage());}
        } else { Log.e(TAG, ">>> Cannot schedule Wi-Fi off alarm: AlarmManager or PendingIntent is null."); }
    }

    // --- cancelWifiOffAlarm ---
    private void cancelWifiOffAlarm() {
        if (alarmManager != null && wifiOffPendingIntent != null) {
            Log.d(TAG, ">>> Cancelling scheduled Wi-Fi off alarm.");
            alarmManager.cancel(wifiOffPendingIntent);
        } else { Log.w(TAG, ">>> Cannot cancel Wi-Fi off alarm: AlarmManager or PendingIntent is null."); }
    }

    // --- waitUntilConnected ---
    private boolean waitUntilConnected() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) { Log.e(TAG, ">>> ConnectivityManager is null."); return false; }
        long startTime = System.currentTimeMillis();
        int checkCount = 0;
        while (System.currentTimeMillis() - startTime < WAIT_WIFI_TIME) {
            checkCount++; boolean connected = false;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Network an = cm.getActiveNetwork();
                    if (an != null) { NetworkCapabilities caps = cm.getNetworkCapabilities(an); boolean v = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED); Log.d(TAG, ">>> Check #" + checkCount + ": ActiveNetwork=" + an + ", Validated=" + v); if (v) connected = true; }
                    else { Log.d(TAG, ">>> Check #" + checkCount + ": ActiveNetwork is null"); }
                } else { @SuppressLint("MissingPermission") NetworkInfo ni = cm.getActiveNetworkInfo(); boolean lc = ni != null && ni.isConnected(); Log.d(TAG, ">>> Check #" + checkCount + ": Legacy Connected=" + lc + (ni != null ? ", Type=" + ni.getTypeName() : "")); if (lc) connected = true; }
            } catch (Exception e) { Log.e(TAG, ">>> Error checking network: " + e.getMessage()); }
            if(connected) { Log.d(TAG, ">>> waitUntilConnected returning true."); return true; }
            try { Thread.sleep(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        Log.w(TAG, ">>> Wait for connection timed out."); return false;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // --- onDestroy ---
    @Override
    public void onDestroy() {
        Log.d(TAG, ">>> MonitorService onDestroy");
        stopMonitoring();

        // 取消计划中的闹钟
        cancelWifiOffAlarm();

        // 移除 subscription change listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && subscriptionManager != null) { try { subscriptionManager.removeOnSubscriptionsChangedListener(subscriptionsChangedListener); Log.d(TAG,">>> Removed subscription listener.");} catch (Exception e) { Log.e(TAG, ">>> Error removing subscription listener: " + e.getMessage()); } }

        stopForeground(true);
        super.onDestroy();
    }

    // --- getSimInfo ---
    private String getSimInfo(Context context, int subId) {
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return "未知SIM卡";
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) { Log.w(TAG, ">>> getSimInfo: No READ_PHONE_STATE permission."); SimPhoneStateListener l = phoneStateListeners.get(subId); return (l != null) ? l.simDisplayName + " (ID: " + subId + ")" : "SIM ID " + subId; }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager sm = this.subscriptionManager; if (sm == null) { sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE); }
                if (sm != null) {
                    SubscriptionInfo si = null; try { si = sm.getActiveSubscriptionInfo(subId); } catch (SecurityException e) { Log.e(TAG,">>> SecEx getting SubInfo "+subId+": "+e.getMessage());}
                    if (si != null) { CharSequence cs = si.getDisplayName(); String dn = (cs!=null?cs.toString():"SIM "+si.getSimSlotIndex()); return dn+" (ID: "+subId+")"; }
                    else { List<SubscriptionInfo> asl = sm.getActiveSubscriptionInfoList(); if (asl != null) for(SubscriptionInfo i : asl) if (i.getSubscriptionId()==subId){ CharSequence cs=i.getDisplayName(); String dn=(cs!=null?cs.toString():"SIM "+i.getSimSlotIndex()); return dn+" (ID: "+subId+")"; } Log.w(TAG, ">>> Could not find active SubInfo for subId: " + subId); }
                } else { Log.w(TAG, ">>> getSimInfo: SubscriptionManager instance is null."); }
            }
        } catch (Exception e) { Log.e(TAG, ">>> General exception in getSimInfo: " + e.getMessage()); }
        SimPhoneStateListener l = phoneStateListeners.get(subId); return (l != null) ? l.simDisplayName + " (ID: " + subId + ")" : "SIM ID " + subId;
    }
}