package com.example.smscallmonitor;
import android.content.*;
import android.os.Build;
import android.util.Log; // Import Log
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver"; // Added TAG

    @Override
    public void onReceive(Context context, Intent intent) {
        // Check the action matches BOOT_COMPLETED
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed. Starting MonitorService."); // Log action

            Intent serviceIntent = new Intent(context, MonitorService.class);
            // Add action to tell the service to start monitoring
            serviceIntent.setAction(MonitorService.ACTION_START_MONITORING);

            // Use startForegroundService for Android O+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } else {
            Log.w(TAG, "Received unexpected intent action: " + intent.getAction());
        }
    }
}