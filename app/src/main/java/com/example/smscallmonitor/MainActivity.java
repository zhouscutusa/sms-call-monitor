package com.example.smscallmonitor;

import android.Manifest;
import android.app.AlarmManager; // 需要 AlarmManager
import android.app.PendingIntent; // 需要 PendingIntent
import android.content.Context; // 需要 Context
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

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private boolean serviceRunning = false; // 简单状态标记

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate called");

        requestRequiredPermissions();

        Button toggleButton = findViewById(R.id.toggleServiceButton);
        toggleButton.setText("启动监控服务"); // 设置初始文本

        toggleButton.setOnClickListener(v -> {
            Log.d(TAG, "Toggle button clicked. Current serviceRunning state: " + serviceRunning);
            Intent intent = new Intent(this, MonitorService.class);
            if (!serviceRunning) {
                Log.d(TAG, "Starting service with ACTION_START_MONITORING");
                intent.setAction(MonitorService.ACTION_START_MONITORING);
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(this, intent);
                    } else {
                        startService(intent);
                    }
                    toggleButton.setText("停止监控服务");
                    Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show();
                    serviceRunning = true;
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
                    toggleButton.setText("启动监控服务");
                    Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show();
                    serviceRunning = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping MonitorService: " + e.getMessage(), e);
                    Toast.makeText(this, "停止监控服务失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
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
}