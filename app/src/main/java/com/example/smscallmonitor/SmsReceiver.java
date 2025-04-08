package com.example.smscallmonitor;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import androidx.core.content.ContextCompat;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            String format = bundle.getString("format");

            if (pdus != null) {
                StringBuilder fullMessageContent = new StringBuilder();
                String sender = null;

                // 获取SIM卡信息
                int subId = intent.getIntExtra("subscription", -1);
                String simInfo = getSimInfo(context, subId);

                // 合并所有消息部分
                for (Object pdu : pdus) {
                    SmsMessage message;
                    if (format != null) {
                        message = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        message = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    // 从第一部分获取发件人
                    if (sender == null) {
                        sender = message.getDisplayOriginatingAddress();
                    }

                    // 添加该部分的内容
                    fullMessageContent.append(message.getMessageBody());
                }

                // 仅当有有效的发件人和内容时处理
                if (sender != null && fullMessageContent.length() > 0) {
                    Intent serviceIntent = new Intent(context, MonitorService.class);
                    serviceIntent.setAction(MonitorService.ACTION_PROCESS_SMS);
                    serviceIntent.putExtra("sender", sender);
                    serviceIntent.putExtra("content", fullMessageContent.toString());
                    serviceIntent.putExtra("simInfo", simInfo);
                    ContextCompat.startForegroundService(context, serviceIntent);
                }
            }
        }
    }

    // 获取SIM卡信息 - 针对API 28 (Android 9)优化
    private String getSimInfo(Context context, int subId) {
        try {
            SubscriptionManager subscriptionManager = context.getSystemService(SubscriptionManager.class);
            if (subscriptionManager != null && subId != -1) {
                SubscriptionInfo subscriptionInfo = null;
                try {
                    subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subId);
                } catch (SecurityException e) {
                    // 处理权限被拒绝的情况
                    return "SIM卡" + (subId + 1);
                }

                if (subscriptionInfo != null) {
                    int slotIndex = subscriptionInfo.getSimSlotIndex();
                    CharSequence displayName = subscriptionInfo.getDisplayName();
                    return "SIM" + (slotIndex + 1) + " (" + displayName + ")";
                }
            }
        } catch (Exception e) {
            // 捕获任何可能的异常，确保不会导致接收器崩溃
            e.printStackTrace();
        }

        // 如果无法获取详细信息，至少返回子ID
        return subId != -1 ? "SIM" + (subId + 1) : "未知SIM卡";
    }
}