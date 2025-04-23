package com.example.smscallmonitor;

import android.Manifest; // 需要 Manifest 权限检查
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager; // 需要 PackageManager
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build; // 需要 Build 版本判断
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

// Worker 类，用于周期性执行合并发送任务
public class ConsolidatedSendWorker extends Worker {

    private static final String TAG = "ConsolidatedSendWorker"; // 日志 TAG
    private PendingEventDao pendingEventDao; // 数据库访问对象
    private WifiManager wifiManager; // Wi-Fi 管理器
    private ConnectivityManager connectivityManager; // 网络连接管理器
    private Context applicationContext; // 应用上下文

    // --- 保留 AlarmManager 相关变量 ---
    private AlarmManager alarmManager;
    private PendingIntent wifiOffPendingIntent;

    public ConsolidatedSendWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.applicationContext = context.getApplicationContext(); // 保存上下文
        // 初始化 DAO 和管理器
        pendingEventDao = AppDatabase.getDatabase(applicationContext).pendingEventDao();
        wifiManager = (WifiManager) applicationContext.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Log.d(TAG, ">>> ConsolidatedSendWorker initialized."); // 初始化日志

        // --- 初始化 AlarmManager (保持不变) ---
        // 1. 获取 AlarmManager
        alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.e(TAG, ">>> Cannot schedule Wi-Fi off: AlarmManager service not available.");
            return;
        }

        // 2. 创建指向 WifiOffReceiver 的 Intent
        Intent intent = new Intent(applicationContext, WifiOffReceiver.class);
        // WifiOffReceiver 需要知道是哪个 Action 触发了它吗？如果需要，可以添加 Action。
        // intent.setAction("com.example.smscallmonitor.ACTION_TURN_OFF_WIFI_ALARM");

        // 3. 创建 PendingIntent (复用 MonitorService 的 Request Code 和 Flags)
        // 确保 MonitorService.WIFI_OFF_ALARM_REQUEST_CODE 是 public static final 或有其他方式访问
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        wifiOffPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                IConstants.WIFI_OFF_ALARM_REQUEST_CODE, // *** 使用 MonitorService 的常量 ***
                intent,
                flags);
    }

    @NonNull
    @Override
    public Result doWork() { // Worker 的核心工作方法
        Log.i(TAG, ">>> ConsolidatedSendWorker started work execution."); // 开始工作日志

        // 调用封装好的核心发送逻辑
        boolean success = EventSendHelper.performConsolidatedSend(applicationContext, pendingEventDao, wifiManager, connectivityManager);
        EventSendHelper.resetWifiOffSchedule(alarmManager, wifiOffPendingIntent);

        // 根据发送逻辑的返回值，决定 Worker 的结果
        if (success) {
            Log.i(TAG, ">>> Consolidated send attempt finished successfully (or no events). Worker returning SUCCESS.");
            return Result.success(); // 返回成功状态
        } else {
            Log.w(TAG, ">>> Consolidated send attempt finished failed. Worker returning SUCCESS to avoid RETRY.");
            return Result.success();
//            return Result.retry(); // 告知 WorkManager 需要稍后重试
        }
    }
}