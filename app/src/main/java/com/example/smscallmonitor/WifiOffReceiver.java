package com.example.smscallmonitor;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class WifiOffReceiver extends BroadcastReceiver {

    private static final String TAG = "WifiOffReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, ">>> WifiOffReceiver triggered (Aggressive Mode).");

        // 直接尝试关闭 Wi-Fi (如果它当前是开启的)
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            Log.i(TAG, ">>> Wi-Fi is currently enabled. Attempting to disable (Aggressive Mode)...");
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // 仅在 Android 10 之前尝试
                    if (context.checkSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
                        wifiManager.setWifiEnabled(false);
                        Log.i(TAG, ">>> Wi-Fi disabled successfully by receiver (Aggressive Mode).");
                    } else {
                        Log.w(TAG, ">>> CHANGE_WIFI_STATE permission missing in receiver. Cannot disable Wi-Fi.");
                    }
                } else {
                    Log.w(TAG, ">>> Receiver running on Android 10+, cannot disable Wi-Fi programmatically.");
                }
            } catch (Exception e) {
                Log.e(TAG, ">>> Error disabling Wi-Fi in receiver: " + e.getMessage());
            }
        } else if (wifiManager != null) {
            Log.d(TAG, ">>> Wi-Fi is already disabled. No action needed.");
        } else {
            Log.e(TAG, ">>> WifiManager is null in receiver.");
        }
    }
}