package com.example.smscallmonitor;
import android.content.*;
import android.os.Bundle;
import android.telephony.SmsMessage;
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

                // Combine all message parts
                for (Object pdu : pdus) {
                    SmsMessage message;
                    if (format != null) {
                        // For newer Android versions
                        message = SmsMessage.createFromPdu((byte[]) pdu, format);
                    } else {
                        // For older Android versions
                        message = SmsMessage.createFromPdu((byte[]) pdu);
                    }

                    // Get sender from the first part
                    if (sender == null) {
                        sender = message.getDisplayOriginatingAddress();
                    }

                    // Append this part's content
                    fullMessageContent.append(message.getMessageBody());
                }

                // Only process if we have a valid sender and content
                if (sender != null && fullMessageContent.length() > 0) {
                    Intent serviceIntent = new Intent(context, MonitorService.class);
                    serviceIntent.setAction(MonitorService.ACTION_PROCESS_SMS);
                    serviceIntent.putExtra("sender", sender);
                    serviceIntent.putExtra("content", fullMessageContent.toString());
                    ContextCompat.startForegroundService(context, serviceIntent);
                }
            }
        }
    }
}