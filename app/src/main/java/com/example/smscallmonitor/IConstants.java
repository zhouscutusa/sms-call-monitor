package com.example.smscallmonitor;

import java.util.concurrent.TimeUnit;

public interface IConstants {
    static final int WIFI_OFF_ALARM_REQUEST_CODE = 99;
    static final int PERMISSION_REQUEST_CODE = 100;
    static final long WIFI_OFF_DELAY_MS = TimeUnit.MINUTES.toMillis(3); // 5分钟 Wi-Fi 关闭延迟
    // 等待网络连接的超时和间隔
    static final long WIFI_CONNECT_TIMEOUT_MS = 35000; // 总共等待 Wi-Fi 连接的最长时间 (35 秒)
    static final long NETWORK_CHECK_INTERVAL_MS = 1500; // 每次检查网络状态的间隔 (1.5 秒)

    static final long MISSED_CALL_DEBOUNCE_MS = 10000; // 未接来电去抖动时间

    static final int SCHEDULED_WORK_INTERVAL_MINUTE = 30;// 每隔30分钟(最小为15分钟)尝试把之前没发送出去的任务发送一下

    // --- SharedPreferences 相关常量 ---
    String PREFS_NAME = "AppPreferences"; // SharedPreferences 文件名
    String KEY_EMAIL_RECIPIENT = "email_recipient"; // 收件箱地址
    String KEY_EMAIL_ENABLED = "email_enabled"; // 邮件通知开关的键
    String KEY_GV_RECIPIENT = "gv_recipient"; // Google Voice 地址
    String KEY_GV_ENABLED = "gv_enabled"; // Google Voice通知开关的键
    String KEY_SERVICE_RUNNING_STATUS = "service_running_status"; // 运行状态标记

    // 本地广播 Action
    String ACTION_SERVICE_STATUS_CHANGED = "com.example.smscallmonitor.SERVICE_STATUS_CHANGED";
    // 本地广播 Extra Key (用于传递状态值)
    String EXTRA_SERVICE_RUNNING_STATUS = "is_service_running";
}
