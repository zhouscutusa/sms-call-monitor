package com.example.smscallmonitor;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony; // Import Telephony
import android.telephony.SmsMessage;
// import android.telephony.SubscriptionInfo; // No longer needed here
import android.telephony.SubscriptionManager;
import android.util.Log; // Import Log
import androidx.core.content.ContextCompat;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver"; // Added TAG

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check action first for clarity
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Log.d(TAG, "SMS Received"); // Log receipt
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            // Check for null pdus array explicitly
            if (pdus == null) {
                Log.e(TAG, "pdus array is null in received SMS intent.");
                return;
            }

            String format = bundle.getString("format");

            StringBuilder fullMessageContent = new StringBuilder();
            String sender = null;
            SmsMessage message = null; // Hold the first message to get sender

            // 获取SIM卡信息 - Get subId directly from intent extras
            int subId = bundle.getInt("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            // Fallback for older devices if 'subscription' extra isn't present
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                subId = bundle.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                Log.d(TAG, "Used fallback 'android.telephony.extra.SUBSCRIPTION_INDEX' for subId: " + subId);
            }
            Log.d(TAG, "Received SMS on SubId: " + subId); // Log the subId


            // 合并所有消息部分
            for (Object pdu : pdus) {
                byte[] pduBytes = (byte[]) pdu; // Cast pdu object to byte array
                if (pduBytes == null) {
                    Log.w(TAG, "Skipping null PDU byte array.");
                    continue; // Skip null PDU
                }

                SmsMessage currentMessage;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Use format if available (API 23+)
                        currentMessage = SmsMessage.createFromPdu(pduBytes, format);
                    } else {
                        currentMessage = SmsMessage.createFromPdu(pduBytes);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error creating SmsMessage from PDU: " + e.getMessage());
                    continue; // Skip this PDU if invalid
                }


                // 从第一部分获取发件人
                if (message == null) {
                    message = currentMessage;
                    sender = message.getDisplayOriginatingAddress(); // Use DisplayOriginatingAddress
                }

                // 添加该部分的内容 (Check for null message body)
                String bodyPart = currentMessage.getMessageBody();
                if (bodyPart != null) {
                    fullMessageContent.append(bodyPart);
                }
            }

            // 仅当有有效的发件人和内容时处理
            if (sender != null && fullMessageContent.length() > 0) {
                Log.d(TAG, "Processing SMS from: " + sender + " on SubId: " + subId); // Log details
                Intent serviceIntent = new Intent(context, MonitorService.class);
                serviceIntent.setAction(MonitorService.ACTION_PROCESS_SMS);
                serviceIntent.putExtra("sender", sender);
                serviceIntent.putExtra("content", fullMessageContent.toString());
                // Pass the raw subId to the service
                serviceIntent.putExtra("subId", subId);
                // Remove passing simInfo string: serviceIntent.putExtra("simInfo", simInfo);

                // Use startForegroundService for Android O+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.w(TAG, "SMS sender or message content is null/empty. Cannot process.");
            }
        } else {
            Log.w(TAG, "SMS received intent bundle is null.");
        }
    }

    // REMOVED: getSimInfo method. This is now handled by MonitorService.
    /*
    private String getSimInfo(Context context, int subId) {
        // ... implementation removed ...
    }
    */
}