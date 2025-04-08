package com.example.smscallmonitor;

import android.annotation.SuppressLint;
import android.app.*;
import android.content.*;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import androidx.core.app.NotificationCompat;

public class MonitorService extends Service {
    public static final String ACTION_PROCESS_SMS = "PROCESS_SMS";
    public static final String ACTION_PROCESS_CALL = "PROCESS_CALL";

    private static final int IDLE_TIME = 5 * 60 * 1000;  // 5分钟（300000毫秒）
    private static final int WAIT_WIFI_TIME = 35 * 1000;  // 等待Wi-Fi连接35秒
    private Handler handler = new Handler();
    private boolean isWifiEnabled = false;  // 跟踪Wi-Fi状态

    // 定义关闭Wi-Fi的任务
    private Runnable wifiOffRunnable = new Runnable() {
        @Override
        public void run() {
            if (isWifiEnabled) {
                WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(false);  // 关闭Wi-Fi
                    isWifiEnabled = false;
                }
            }
        }
    };

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel("channel", "监控服务", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        Notification notification = new NotificationCompat.Builder(this, "channel")
                .setContentTitle("短信来电监控中")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build();
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        String simInfo = intent.getStringExtra("simInfo");
        if (simInfo == null) simInfo = "未知SIM卡";

        if (ACTION_PROCESS_SMS.equals(action)) {
            String sender = intent.getStringExtra("sender");
            String content = intent.getStringExtra("content");
            sendNotification("短信 (" + simInfo + ")", sender + ": " + content);
        } else if (ACTION_PROCESS_CALL.equals(action)) {
            String number = intent.getStringExtra("incomingNumber");
            sendNotification("未接来电 (" + simInfo + ")", "号码: " + number);
        }
        return START_NOT_STICKY;
    }

    // 发送通知，并启动Wi-Fi处理和定时器
    private void sendNotification(String subject, String body) {
        new Thread(() -> {
            WifiManager wifiManager = (WifiManager) getSystemService(WIFI_SERVICE);
            if (wifiManager != null && !wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);  // 打开Wi-Fi
                isWifiEnabled = true;
            }

            resetTimer();  // 重置定时器

            if (waitUntilConnected()) {
                EmailSender.send(subject, body);
            } else {
                System.out.println("❌ 超时：网络未连接，无法发送邮件");
            }
        }).start();
    }

    // 重置定时器
    private void resetTimer() {
        handler.removeCallbacks(wifiOffRunnable);  // 移除之前的任务
        handler.postDelayed(wifiOffRunnable, IDLE_TIME);  // 设置新的定时任务
    }

    // 等待Wi-Fi连接，最多等35秒
    private boolean waitUntilConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        for (int i = 0; i < WAIT_WIFI_TIME/1000; i++) {  // 最多等35秒
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null && activeNetwork.isConnected()) {
                return true;
            }
            try {
                Thread.sleep(1000);  // 每秒检查一次
            } catch (InterruptedException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(wifiOffRunnable);  // 释放定时器
    }
}