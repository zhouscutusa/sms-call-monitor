package com.example.smscallmonitor;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsValues {

    // ---：获取设置 ---

    // 获取邮件收件地址
    public static String getEmailRecipients(Context context, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        // 提供一个空字符串作为默认值
        return prefs.getString(IConstants.KEY_EMAIL_RECIPIENT, defaultValue);
    }

    // 获取邮件通知是否启用
    public static boolean isEmailNotificationEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        // 提供 false 作为默认值
        return prefs.getBoolean(IConstants.KEY_EMAIL_ENABLED, false);
    }

    // 获取GoogleVoice电话地址
    public static String getGVRecipients(Context context, String defaultValue) {
        SharedPreferences prefs = context.getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        // 提供一个空字符串作为默认值
        return prefs.getString(IConstants.KEY_GV_RECIPIENT, defaultValue);
    }

    // 获取短信通知是否启用
    public static boolean isGVNotificationEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(IConstants.PREFS_NAME, Context.MODE_PRIVATE);
        // 提供 false 作为默认值
        return prefs.getBoolean(IConstants.KEY_GV_ENABLED, false);
    }

}
