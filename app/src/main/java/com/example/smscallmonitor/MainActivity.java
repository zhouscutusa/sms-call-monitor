package com.example.smscallmonitor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private boolean serviceRunning = false;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 请求所需权限
        requestRequiredPermissions();

        Button toggleButton = findViewById(R.id.toggleServiceButton);
        toggleButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MonitorService.class);
            if (!serviceRunning) {
                ContextCompat.startForegroundService(this, intent);
                toggleButton.setText("停止监控服务");
                Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show();
            } else {
                stopService(intent);
                toggleButton.setText("启动监控服务");
                Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show();
            }
            serviceRunning = !serviceRunning;
        });
    }

    private void requestRequiredPermissions() {
        String[] permissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_PHONE_NUMBERS,  // Android 8.0+
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.FOREGROUND_SERVICE  // Android 9.0+
        };

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "所有权限已授予，应用可以正常工作", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "部分权限被拒绝，应用可能无法正常工作", Toast.LENGTH_LONG).show();
            }
        }
    }
}