package com.example.smscallmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat; // 确保使用正确的 Switch 类

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private EditText recipientEditText;
    private SwitchCompat emailNotificationSwitch;
    private Button saveSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Log.d(TAG, "onCreate called");

        // 初始化视图
        recipientEditText = findViewById(R.id.recipientEditText);
        emailNotificationSwitch = findViewById(R.id.emailNotificationSwitch);
        saveSettingsButton = findViewById(R.id.saveSettingsButton);

        // 加载已保存的设置
        loadSettings();

        // 设置保存按钮的点击事件
        saveSettingsButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(SettingsActivity.this, "设置已保存", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Settings saved by user.");
            finish(); // 保存后关闭设置页面
        });
    }

    // 加载设置
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String emailRecipient = prefs.getString(IConstants.KEY_EMAIL_RECIPIENT, ""); // 提供默认值
        boolean emailEnabled = prefs.getBoolean(IConstants.KEY_EMAIL_ENABLED, false); // 提供默认值

        recipientEditText.setText(emailRecipient);
        emailNotificationSwitch.setChecked(emailEnabled);

        Log.d(TAG, "Settings loaded: EmailRecipients=" + emailRecipient + ", EmailEnabled=" + emailEnabled);
    }

    // 保存设置
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        String emailRecipient = recipientEditText.getText().toString().trim();
        boolean emailEnabled = emailNotificationSwitch.isChecked();

        editor.putString(IConstants.KEY_EMAIL_RECIPIENT, emailRecipient);
        editor.putBoolean(IConstants.KEY_EMAIL_ENABLED, emailEnabled);

        editor.apply(); // apply() 是异步的，比 commit() 更推荐

        Log.d(TAG, "Saving settings: EmailRecipients=" + emailRecipient + ", EmailEnabled=" + emailEnabled);
    }
}