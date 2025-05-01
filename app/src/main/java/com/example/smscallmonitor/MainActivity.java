package com.example.smscallmonitor;

import android.Manifest;
import android.app.AlarmManager; // 需要 AlarmManager
import android.app.PendingIntent; // 需要 PendingIntent
import android.content.Context; // 需要 Context
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private boolean serviceRunning = false; // 简单状态标记
    private Button toggleButton;
    private Button settingsButton;

    // ***用于接收服务状态变化的广播接收器 ***
    private BroadcastReceiver serviceStatusReceiver;
    private IntentFilter serviceStatusFilter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate called");

        requestRequiredPermissions();

        toggleButton = findViewById(R.id.toggleServiceButton);
        settingsButton = findViewById(R.id.settingsButton); // 获取设置按钮

        toggleButton.setOnClickListener(v -> {
            boolean isActuallyRunning = isServiceRunningFromPrefs(this);
            Log.d(TAG, "Toggle button clicked. Current serviceRunning state: " + isActuallyRunning);
            Intent intent = new Intent(this, MonitorService.class);
            if (!isActuallyRunning) {
                Log.d(TAG, "Starting service with ACTION_START_MONITORING");
                intent.setAction(MonitorService.ACTION_START_MONITORING);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(this, intent);
                    } else {
                        startService(intent);
                    }
                    Toast.makeText(this, "监控服务启动请求已发送", Toast.LENGTH_SHORT).show(); // 可以保留 Toast
                } catch (Exception e) {
                    Log.e(TAG, "Error starting MonitorService: " + e.getMessage(), e);
                    Toast.makeText(this, "启动监控服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Log.d(TAG, "Stopping service");
                try {
                    stopService(intent);
                    // 取消闹钟
                    cancelWifiOffAlarmFromActivity(this);
                    Toast.makeText(this, "监控服务停止请求已发送", Toast.LENGTH_SHORT).show(); // 可以保留 Toast
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping MonitorService: " + e.getMessage(), e);
                    Toast.makeText(this, "停止监控服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });

        // --- 设置按钮的点击事件 ---
        settingsButton.setOnClickListener(v -> {
            Log.d(TAG, "Settings button clicked.");
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        // *** 初始化广播接收器和过滤器 ***
        serviceStatusFilter = new IntentFilter(IConstants.ACTION_SERVICE_STATUS_CHANGED);
        serviceStatusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Received local broadcast: " + intent.getAction());
                if (IConstants.ACTION_SERVICE_STATUS_CHANGED.equals(intent.getAction())) {
                    // 广播携带了最新的服务运行状态，但我们仍然选择从 Prefs 读取以保持一致性
                    updateButtonStateBasedOnPrefs(); // 从 Prefs 读取并更新
                    Log.d(TAG, "Button state updated after receiving broadcast.");
                }
            }
        };
    }

    // *** onResume 方法，用于在 Activity 返回前台时更新按钮状态和注册接收器 ***
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called - updating button state from Prefs and registering receiver.");
        // 1. 每次 Activity 可见时，根据 SharedPreferences 更新按钮状态 (处理 Activity 生命周期变化)
        updateButtonStateBasedOnPrefs();

        // 2. 注册广播接收器，监听服务状态变化 (处理服务运行时状态变化)
        if (serviceStatusReceiver != null && serviceStatusFilter != null) {
            LocalBroadcastManager.getInstance(this).registerReceiver(serviceStatusReceiver, serviceStatusFilter);
            Log.d(TAG, "Service status receiver registered.");
        } else {
            Log.e(TAG, "Service status receiver or filter is null in onResume!");
        }
    }

    // *** onPause 方法，用于在 Activity 离开前台时取消注册接收器 ***
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called - unregistering receiver.");
        // 取消注册广播接收器，防止内存泄漏
        if (serviceStatusReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceStatusReceiver);
            Log.d(TAG, "Service status receiver unregistered.");
        }
    }

    private void requestRequiredPermissions() {
        Log.d(TAG, "Checking required permissions...");
        List<String> requiredPermissionsList = new ArrayList<>();
        requiredPermissionsList.add(Manifest.permission.RECEIVE_SMS);
        requiredPermissionsList.add(Manifest.permission.READ_SMS);
        requiredPermissionsList.add(Manifest.permission.READ_PHONE_STATE);
        requiredPermissionsList.add(Manifest.permission.READ_CALL_LOG);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { requiredPermissionsList.add(Manifest.permission.READ_PHONE_NUMBERS); }
        requiredPermissionsList.add(Manifest.permission.INTERNET);
        requiredPermissionsList.add(Manifest.permission.ACCESS_NETWORK_STATE);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { requiredPermissionsList.add(Manifest.permission.CHANGE_WIFI_STATE); }
        requiredPermissionsList.add(Manifest.permission.ACCESS_WIFI_STATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { requiredPermissionsList.add(Manifest.permission.FOREGROUND_SERVICE); }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { requiredPermissionsList.add(Manifest.permission.SCHEDULE_EXACT_ALARM); } // Needed if using setExact() and targeting 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { requiredPermissionsList.add(Manifest.permission.POST_NOTIFICATIONS); }

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : requiredPermissionsList) {
            if (permission != null && ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
                Log.w(TAG, "Permission needed: " + permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            Log.d(TAG, "Requesting permissions: " + permissionsToRequest);
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), IConstants.PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "All required permissions are already granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if (requestCode == IConstants.PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.w(TAG, "Permission denied: " + permissions[i]);
                } else {
                    Log.d(TAG, "Permission granted: " + permissions[i]);
                }
            }
            if (allGranted) { Log.d(TAG, "All requested permissions granted."); Toast.makeText(this, "所有权限已授予", Toast.LENGTH_SHORT).show(); }
            else { Log.e(TAG, "Some permissions were denied."); Toast.makeText(this, "部分权限被拒绝，应用可能无法正常工作", Toast.LENGTH_LONG).show(); }
        }
    }

    // 修改方法：只取消闹钟
    private void cancelWifiOffAlarmFromActivity(Context context) {
        Log.d(TAG, ">>> Cancelling Wi-Fi off alarm from Activity.");
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WifiOffReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent wifiOffPendingIntent = PendingIntent.getBroadcast(context, IConstants.WIFI_OFF_ALARM_REQUEST_CODE, intent, flags);
        EventSendHelper.cancelWifiOffAlarm(alarmManager, wifiOffPendingIntent);
    }

    // *** 辅助方法，从 SharedPreferences 读取服务状态 ***
    private boolean isServiceRunningFromPrefs(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(IConstants.KEY_SERVICE_RUNNING_STATUS, false); // 默认返回 false
    }

    // *** 辅助方法，根据 SharedPreferences 更新按钮文本 ***
    private void updateButtonStateBasedOnPrefs() {
        if (toggleButton == null) return; // 防止在 onCreate 完成前调用时出错
        boolean isRunning = isServiceRunningFromPrefs(this);
        Log.d(TAG, "Updating button text based on Prefs state: " + isRunning);
        if (isRunning) {
            toggleButton.setText("停止监控服务");
        } else {
            toggleButton.setText("启动监控服务");
        }
    }
}