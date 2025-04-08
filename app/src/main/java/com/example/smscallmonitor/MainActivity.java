// MainActivity.java
package com.example.smscallmonitor;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private boolean serviceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String[] permissions = {
                Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_SMS,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.ACCESS_WIFI_STATE
        };
        ActivityCompat.requestPermissions(this, permissions, 1);

        Button toggleButton = findViewById(R.id.toggleServiceButton);
        toggleButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MonitorService.class);
            if (!serviceRunning) {
                ContextCompat.startForegroundService(this, intent);
                Toast.makeText(this, "监控服务已启动", Toast.LENGTH_SHORT).show();
            } else {
                stopService(intent);
                Toast.makeText(this, "监控服务已停止", Toast.LENGTH_SHORT).show();
            }
            serviceRunning = !serviceRunning;
        });
    }
}
