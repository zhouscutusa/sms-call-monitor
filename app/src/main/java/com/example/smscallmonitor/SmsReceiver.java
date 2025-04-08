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
            if (pdus != null) {
                for (Object pdu : pdus) {
                    SmsMessage message = SmsMessage.createFromPdu((byte[]) pdu);
                    String sender = message.getDisplayOriginatingAddress();
                    String content = message.getMessageBody();
                    Intent serviceIntent = new Intent(context, MonitorService.class);
                    serviceIntent.setAction(MonitorService.ACTION_PROCESS_SMS);
                    serviceIntent.putExtra("sender", sender);
                    serviceIntent.putExtra("content", content);
                    ContextCompat.startForegroundService(context, serviceIntent);
                }
            }
        }
    }
}
