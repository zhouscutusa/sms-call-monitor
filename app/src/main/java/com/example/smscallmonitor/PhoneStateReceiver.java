package com.example.smscallmonitor;
import android.content.*;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.Map;

public class PhoneStateReceiver extends BroadcastReceiver {
    private static String lastNumber = null;
    private static boolean isCallAnswered = false;
    private static final Map<String, Long> processedNumbers = new HashMap<>();
    private static int lastSubId = -1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        // 获取SIM卡ID
        int subId = intent.getIntExtra("subscription", -1);
        // 对于某些设备，可能需要使用其他Extra键获取subId
        if (subId == -1) {
            subId = intent.getIntExtra("phone", -1);
        }

        android.util.Log.d("PhoneStateReceiver", "State: " + state + ", Number: " + incomingNumber + ", SubId: " + subId);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            lastNumber = incomingNumber;
            lastSubId = subId;
            isCallAnswered = false;
            android.util.Log.d("PhoneStateReceiver", "Incoming call: " + lastNumber + " on SIM: " + subId);

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

                    String simInfo = getSimInfo(context, lastSubId);
                    Intent serviceIntent = new Intent(context, MonitorService.class);
                    serviceIntent.setAction(MonitorService.ACTION_PROCESS_CALL);
                    serviceIntent.putExtra("incomingNumber", lastNumber);
                    serviceIntent.putExtra("simInfo", simInfo);
                    ContextCompat.startForegroundService(context, serviceIntent);
                } else {
                    android.util.Log.d("PhoneStateReceiver", "Skipped duplicate missed call: " + lastNumber);
                }
            }

            // 重置
            lastNumber = null;
            lastSubId = -1;
            isCallAnswered = false;
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