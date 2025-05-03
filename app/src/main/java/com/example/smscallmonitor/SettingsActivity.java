package com.example.smscallmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat; // 确保使用正确的 Switch 类

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    private EditText recipientEditText;
    private SwitchCompat emailNotificationSwitch;

    private EditText googleVoiceEditText;
    private SwitchCompat googleVoiceNotificationSwitch;

    private EditText senderEditText;
    private TextInputEditText passwordEditText;
    private TextInputLayout passwordInputLayout;

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
        senderEditText = findViewById(R.id.senderEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);

        // *** 为密码输入框添加文本变化监听器 ***
        passwordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要实现
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 不需要实现
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 在文本改变后检查是否为空
                if (passwordInputLayout != null) {
                    // 如果输入框有文本，启用密码可见性切换；否则禁用
                    passwordInputLayout.setPasswordVisibilityToggleEnabled(s != null && s.length() > 0);
                }
            }
        });

        // 加载已保存的设置
        loadSettings();

        // 设置保存按钮的点击事件
        saveSettingsButton.setOnClickListener(v -> {
            // 在点击保存时调用验证方法
            if(validateAndSaveSettings()) {
                Toast.makeText(SettingsActivity.this, "设置已保存", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Settings saved by user.");
                finish(); // 保存后关闭设置页面
            }
            // 如果验证失败，validateAndSaveSettings 会返回 false，Toast 和 finish 不会执行
        });
    }

    // 加载设置
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);

        recipientEditText.setText(prefs.getString(IConstants.KEY_EMAIL_RECIPIENT, ""));
        emailNotificationSwitch.setChecked(prefs.getBoolean(IConstants.KEY_EMAIL_ENABLED, false));
        googleVoiceEditText.setText(prefs.getString(IConstants.KEY_GV_RECIPIENT, ""));
        googleVoiceNotificationSwitch.setChecked(prefs.getBoolean(IConstants.KEY_GV_ENABLED, false));
        senderEditText.setText(prefs.getString(IConstants.KEY_EMAIL_SENDER, ""));
        // passwordEditText.setText(prefs.getString(IConstants.KEY_SEND_PASSWORD, ""));
        passwordEditText.setText(""); // 清空输入框
        passwordEditText.setHint("留空表示不修改密码"); // 可以设置一个提示
        // 初始状态下禁用密码可见性切换图标 ***
        if (passwordInputLayout != null) {
            passwordInputLayout.setPasswordVisibilityToggleEnabled(false);
        }
    }

    // *** 校验并保存设置，将保存逻辑封装到验证方法中 ***
    /**
     * 验证输入项，如果验证通过则保存设置。
     * @return true 如果验证通过并保存成功，false 如果验证失败。
     */
    private boolean validateAndSaveSettings() {
        boolean emailEnabled = emailNotificationSwitch.isChecked();
        String recipients = recipientEditText.getText().toString().trim();
        if(emailEnabled && (null == recipients || recipients.isEmpty())) {
            // 收件箱为空，提示用户
            Toast.makeText(this, "接收邮件地址不能为空", Toast.LENGTH_SHORT).show();
            recipientEditText.setError("接收邮件地址不能为空");
            // 需要获取父级的 TextInputLayout 来设置错误
            // ((TextInputLayout) passwordEditText.getParent().getParent()).setError("密码不能为空"); // 示例，可能需要根据实际布局调整
            Log.w(TAG, "Validation failed: Email recipient is empty.");
            return false; // 验证失败
        }

        boolean gvEnabled = googleVoiceNotificationSwitch.isChecked();
        String gvRecipients = googleVoiceEditText.getText().toString().trim();
        if(gvEnabled && (null == gvRecipients || gvRecipients.isEmpty())) {
            // Google Voice 收件箱为空，提示用户
            Toast.makeText(this, "GV电话地址不能为空", Toast.LENGTH_SHORT).show();
            googleVoiceEditText.setError("GV电话地址不能为空");
            Log.w(TAG, "Validation failed: Google Voice recipient is empty.");
            return false; // 验证失败
        }

        // 清除之前的错误提示
        if (passwordInputLayout != null) {
            passwordInputLayout.setError(null);
            passwordInputLayout.setErrorEnabled(false);
        }

        // 获取密码输入框的内容并去除首尾空格
        String emailSender = senderEditText.getText().toString().trim();
        // 获取密码输入框的内容并去除首尾空格
        String enteredPassword = passwordEditText.getText().toString().trim();
        boolean passwdChanged = false;
        if(emailEnabled || gvEnabled) {
            // 验证密码是否为空
            if (null == emailSender || emailSender.isEmpty()) {
                // 发件箱为空，提示用户
                Toast.makeText(this, "发送邮箱不能为空！", Toast.LENGTH_SHORT).show();
                senderEditText.setError("发送邮箱不能为空");
                Log.w(TAG, "Validation failed: Email sender is empty.");
                return false; // 验证失败
            }
            // 验证密码是否为空
            if (null == enteredPassword || enteredPassword.isEmpty()) {
                SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
                String savedPassword = prefs.getString(IConstants.KEY_SEND_PASSWORD, "");
                if(null == savedPassword || savedPassword.isEmpty()) {
                    // 密码为空，提示用户
                    Toast.makeText(this, "发送密码不能为空！", Toast.LENGTH_SHORT).show();
                    if (passwordInputLayout != null) {
                        passwordInputLayout.setError("密码不能为空");
                        passwordInputLayout.setErrorEnabled(true); // 确保错误提示是启用的
                    }
                    Log.w(TAG, "Validation failed: Password is empty.");
                    return false; // 验证失败
                }
            } else {
                passwdChanged = true;
            }
        }

        // 3. 如果校验通过，则执行保存逻辑
        SharedPreferences prefs = getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(IConstants.KEY_EMAIL_RECIPIENT, recipients);
        editor.putBoolean(IConstants.KEY_EMAIL_ENABLED, emailEnabled);
        editor.putString(IConstants.KEY_GV_RECIPIENT, gvRecipients);
        editor.putBoolean(IConstants.KEY_GV_ENABLED, gvEnabled);
        editor.putString(IConstants.KEY_EMAIL_SENDER, emailSender);
        if(passwdChanged) {
            editor.putString(IConstants.KEY_SEND_PASSWORD, enteredPassword);
        }

        editor.apply(); // apply() 是异步的，比 commit() 更推荐

        Log.d(TAG, "Settings validated and saved.");
        return true; // 验证通过并保存成功
    }
}