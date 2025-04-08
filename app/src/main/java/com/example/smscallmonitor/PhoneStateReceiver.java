package com.example.smscallmonitor;
import android.content.*;
import android.telephony.TelephonyManager;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class PhoneStateReceiver extends BroadcastReceiver {
    private static String lastNumber = null;
    private static boolean isCallAnswered = false;
    private static final Map<String, Long> processedNumbers = new HashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        android.util.Log.d("PhoneStateReceiver", "State: " + state + ", Number: " + incomingNumber);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            lastNumber = incomingNumber;
            isCallAnswered = false;
            android.util.Log.d("PhoneStateReceiver", "Incoming call: " + lastNumber);

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            isCallAnswered = true;
            android.util.Log.d("PhoneStateReceiver", "Call answered");

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            android.util.Log.d("PhoneStateReceiver", "Call ended. isCallAnswered = " + isCallAnswered);

            if (!isCallAnswered && lastNumber != null) {
                long now = System.currentTimeMillis();
                Long lastProcessed = processedNumbers.getOrDefault(lastNumber, 0L);

                if (now - lastProcessed > 10_000) {
                    processedNumbers.put(lastNumber, now);
                    android.util.Log.d("PhoneStateReceiver", "Missed call detected, sending to service: " + lastNumber);

                    Intent serviceIntent = new Intent(context, MonitorService.class);
                    serviceIntent.setAction(MonitorService.ACTION_PROCESS_CALL);
                    serviceIntent.putExtra("incomingNumber", lastNumber);
                    ContextCompat.startForegroundService(context, serviceIntent);
                } else {
                    android.util.Log.d("PhoneStateReceiver", "Skipped duplicate missed call: " + lastNumber);
                }
            }

            // Reset
            lastNumber = null;
            isCallAnswered = false;
        }
    }
}
