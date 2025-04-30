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

    private EditText googleVoiceEditText;
    private SwitchCompat googleVoiceNotificationSwitch;
    private Button saveSettingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Log.d(TAG, "onCreate called");

        // 初始化视图
        recipientEditText = findViewById(R.id.recipientEditText);
        emailNotificationSwitch = findViewById(R.id.emailNotificationSwitch);
        googleVoiceEditText = findViewById(R.id.googleVoiceEditText);
        googleVoiceNotificationSwitch = findViewById(R.id.googleVoiceNotificationSwitch);
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

        recipientEditText.setText(prefs.getString(IConstants.KEY_EMAIL_RECIPIENT, ""));
        emailNotificationSwitch.setChecked(prefs.getBoolean(IConstants.KEY_EMAIL_ENABLED, false));
        googleVoiceEditText.setText(prefs.getString(IConstants.KEY_GV_RECIPIENT, ""));
        googleVoiceNotificationSwitch.setChecked(prefs.getBoolean(IConstants.KEY_GV_ENABLED, false));
    }

    // 保存设置
    private void saveSettings() {
        SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(IConstants.KEY_EMAIL_RECIPIENT, recipientEditText.getText().toString().trim());
        editor.putBoolean(IConstants.KEY_EMAIL_ENABLED, emailNotificationSwitch.isChecked());
        editor.putString(IConstants.KEY_GV_RECIPIENT, googleVoiceEditText.getText().toString().trim());
        editor.putBoolean(IConstants.KEY_GV_ENABLED, googleVoiceNotificationSwitch.isChecked());

        editor.apply(); // apply() 是异步的，比 commit() 更推荐
    }
}