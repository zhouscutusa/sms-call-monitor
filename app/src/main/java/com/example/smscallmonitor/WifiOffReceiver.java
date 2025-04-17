package com.example.smscallmonitor;

import android.Manifest; // 需要 Manifest 权限检查
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager; // 需要 PackageManager 检查权限
import android.net.wifi.WifiManager; // 需要 WifiManager 控制 Wi-Fi
import android.os.Build; // 需要 Build 判断 Android 版本
import android.util.Log; // 需要 Log 记录日志

import java.util.concurrent.ExecutorService; // 引入 ExecutorService 用于后台执行
import java.util.concurrent.Executors;     // 引入 Executors 创建线程池

// 广播接收器，负责接收由 MonitorService 设置的定时关闭 Wi-Fi 的闹钟广播
public class WifiOffReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiOffReceiver"; // 日志 TAG

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, ">>> WifiOffReceiver triggered (Aggressive Mode).");// 记录接收器被触发
        // --- 开始执行原始的关闭 Wi-Fi 逻辑 ---
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        // 检查 WifiManager 是否成功获取，并且当前 Wi-Fi 是否已开启
        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            Log.i(TAG, ">>> Wi-Fi is currently enabled. Attempting to disable..."); // 保留原始日志
            try {
                // 关键：只在 Android 10 (API 29) 之前的版本尝试关闭 Wi-Fi
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // 检查是否有修改 Wi-Fi 状态的权限
                    if (context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                        // 有权限，执行关闭操作
                        wifiManager.setWifiEnabled(false);
                        Log.i(TAG, ">>> Wi-Fi disabled successfully by receiver."); // 保留原始日志
                    } else {
                        // 没有权限，记录警告
                        Log.w(TAG, ">>> CHANGE_WIFI_STATE permission missing in receiver. Cannot disable Wi-Fi."); // 保留原始日志
                    }
                } else {
                    // Android 10 及以上版本，记录无法通过程序关闭
                    Log.w(TAG, ">>> Receiver running on Android 10+, cannot disable Wi-Fi programmatically."); // 保留原始日志
                }
            } catch (SecurityException se) { // 单独捕获安全异常
                Log.e(TAG, ">>> SecurityException disabling Wi-Fi in receiver: " + se.getMessage());
            } catch (Exception e) { // 捕获其他可能的异常
                Log.e(TAG, ">>> Error disabling Wi-Fi in receiver: " + e.getMessage()); // 保留原始日志
            }
        } else if (wifiManager != null) {
            // WifiManager 获取成功，但 Wi-Fi 已经是关闭状态
            Log.d(TAG, ">>> Wi-Fi is already disabled. No action needed."); // 保留原始日志
        } else {
            // WifiManager 获取失败
            Log.e(TAG, ">>> WifiManager is null in receiver."); // 保留原始日志
        }
    } // onReceive 方法结束
} // WifiOffReceiver 类结束