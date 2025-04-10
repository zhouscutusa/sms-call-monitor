package com.example.smscallmonitor;
import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import androidx.core.app.ActivityCompat;
// import androidx.core.content.ContextCompat; // No longer starting service from here for calls

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhoneStateReceiver extends BroadcastReceiver {
    private static String lastNumber = null;
    private static boolean isCallAnswered = false;
    // Removed processedNumbers map as logic is moved to service
    // private static final Map<String, Long> processedNumbers = new HashMap<>();
    private static int lastSubId = -1; // Keep for logging purposes if desired

    @Override
    public void onReceive(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

        // Get SIM ID using multiple possible extras (This logic remains unreliable here)
        int subId = -1;

        // Try all possible intent extras that might contain the subscription ID
        subId = intent.getIntExtra("subscription", -1);
        if (subId == -1) subId = intent.getIntExtra("phone", -1);
        if (subId == -1) subId = intent.getIntExtra("slot", -1);
        if (subId == -1) subId = intent.getIntExtra("simId", -1);
        if (subId == -1) subId = intent.getIntExtra("sim_id", -1);

        // Keep logging as requested
        List<String> extraList = new ArrayList<>();
        if(intent.getExtras() != null) { // Check for null extras
            extraList.addAll(intent.getExtras().keySet());
            android.util.Log.d("PhoneStateReceiver", "======intent.getExtras().keySet(): " + intent.getExtras().keySet());
        } else {
            android.util.Log.d("PhoneStateReceiver", "======intent.getExtras() is null");
        }
        android.util.Log.d("PhoneStateReceiver", "======extraList: " + extraList);

        android.util.Log.d("PhoneStateReceiver", "======getIntExtra - subscription: " + intent.getIntExtra("subscription", -1)); // Log the standard one too
        android.util.Log.d("PhoneStateReceiver", "======getIntExtra - phone: " + intent.getIntExtra("phone", -1));
        android.util.Log.d("PhoneStateReceiver", "======getIntExtra - slot: " + intent.getIntExtra("slot", -1));
        android.util.Log.d("PhoneStateReceiver", "======getIntExtra - simId: " + intent.getIntExtra("simId", -1));
        android.util.Log.d("PhoneStateReceiver", "======getIntExtra - sim_id: " + intent.getIntExtra("sim_id", -1));

        // For some devices, if still not found, try using TelephonyManager (This logic remains unreliable here)
        if (subId == -1) {
            // Check for required permissions before proceeding
            if (hasPhoneStatePermission(context)) {
                try {
                    TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && tm != null) { // Check tm not null
                        // On Android 8.0+, get the phone count and try to determine which one is ringing
                        int phoneCount = tm.getPhoneCount();
                        android.util.Log.d("PhoneStateReceiver", "======Phone count: " + phoneCount); // Log phone count
                        if (phoneCount > 1) {
                            // For dual SIM devices, try to access slot-specific managers
                            for (int i = 0; i < phoneCount; i++) {
                                // Removed the check for N as createForSubscriptionId exists since N
                                try {
                                    // Check permission before creating for subId
                                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                                        android.util.Log.w("PhoneStateReceiver", "No permission to createForSubscriptionId for slot " + i);
                                        continue; // Skip if no permission
                                    }
                                    TelephonyManager slotSpecificTm = tm.createForSubscriptionId(i); // Using slot index 'i' as potential subId guess (often incorrect)
                                    if (null != slotSpecificTm) {
                                        android.util.Log.d("PhoneStateReceiver", "======Attempting check for slot index: " + i);
                                        int specificState = slotSpecificTm.getCallState(); // Get state for this specific manager
                                        android.util.Log.d("PhoneStateReceiver", "======Slot Index: " + i + ", slotSpecificTm.getCallState(): " + specificState);
                                        // Keep other logs if needed for debugging
                                        // android.util.Log.d("PhoneStateReceiver", "======i: " + i + ", slotSpecificTm.getSimCarrierId(): " + slotSpecificTm.getSimCarrierId());
                                        // android.util.Log.d("PhoneStateReceiver", "======i: " + i + ", slotSpecificTm.getSimCarrierIdName(): " + slotSpecificTm.getSimCarrierIdName());
                                        // android.util.Log.d("PhoneStateReceiver", "======i: " + i + ", slotSpecificTm.getSimState(): " + slotSpecificTm.getSimState());

                                        // Problem: Both might report RINGING. This assignment will likely be wrong.
                                        if (specificState == TelephonyManager.CALL_STATE_RINGING) {
                                            android.util.Log.d("PhoneStateReceiver", "======Slot Index: " + i + " reports RINGING. Setting subId guess to: " + i);
                                            subId = i; // This guess based on slot index is often wrong for actual SubId
                                            // Do NOT break; let it potentially overwrite with the last ringing one (still wrong)
                                        }
                                    } else {
                                        android.util.Log.d("PhoneStateReceiver", "======Slot Index: " + i + ", slotSpecificTm is null");
                                    }
                                } catch (SecurityException se) {
                                    android.util.Log.e("PhoneStateReceiver", "Security exception when accessing specific slot " + i + ": " + se.getMessage());
                                } catch (Exception e) { // Catch general exceptions too
                                    android.util.Log.e("PhoneStateReceiver", "Exception when accessing specific slot " + i + ": " + e.getMessage());
                                }
                            }
                        } else if (phoneCount == 1) {
                            // If only one SIM, try getting default subId
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                SubscriptionManager sm = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                                if (sm != null) {
                                    List<SubscriptionInfo> activeSubs = sm.getActiveSubscriptionInfoList();
                                    if (activeSubs != null && !activeSubs.isEmpty()) {
                                        subId = activeSubs.get(0).getSubscriptionId();
                                        android.util.Log.d("PhoneStateReceiver", "======Single SIM phone, guessing subId: " + subId);
                                    }
                                }
                            }
                        }
                    }
                } catch (SecurityException se) {
                    android.util.Log.e("PhoneStateReceiver", "Security exception: " + se.getMessage());
                } catch (Exception e) {
                    android.util.Log.e("PhoneStateReceiver", "Error getting SIM info: " + e.getMessage());
                }
            } else {
                android.util.Log.w("PhoneStateReceiver", "READ_PHONE_STATE permission not granted");
            }
        }

        // If still not found and device is dual SIM, try using SubscriptionManager (This is just a guess)
        if (subId == -1 && hasPhoneStatePermission(context)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE); // Correct cast
                    if (subscriptionManager != null) {
                        // Check permission again before accessing list
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            List<SubscriptionInfo> activeSubscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                            if (activeSubscriptions != null && !activeSubscriptions.isEmpty()) {
                                // If we can't determine exactly which SIM is receiving the call,
                                // this fallback guess is unreliable for ringing state.
                                // subId = activeSubscriptions.get(0).getSubscriptionId(); // Don't guess here, listener will handle it.
                                android.util.Log.d("PhoneStateReceiver", "======SubscriptionManager found active SIMs, but cannot determine ringing SIM here.");

                                // Log the available SIMs for debugging (keep this)
                                for (SubscriptionInfo info : activeSubscriptions) {
                                    android.util.Log.d("PhoneStateReceiver",
                                            "Available SIM: slot=" + info.getSimSlotIndex() +
                                                    ", id=" + info.getSubscriptionId() +
                                                    ", name=" + info.getDisplayName());
                                }
                            }
                        } else {
                            android.util.Log.w("PhoneStateReceiver", "READ_PHONE_STATE permission not granted for SubscriptionManager");
                        }
                    }
                }
            } catch (SecurityException se) {
                android.util.Log.e("PhoneStateReceiver", "Permission denied accessing SubscriptionManager: " + se.getMessage());
            } catch (Exception e) {
                android.util.Log.e("PhoneStateReceiver", "Error accessing SubscriptionManager: " + e.getMessage());
            }
        }

        // Log the final determined state (subId might still be -1 or an incorrect guess)
        android.util.Log.d("PhoneStateReceiver", "State: " + state + ", Number: " + incomingNumber + ", Final SubId Guess: " + subId);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            // Store number and guessed subId for potential use in IDLE state (though IDLE logic is removed)
            lastNumber = incomingNumber;
            lastSubId = subId; // Store the potentially incorrect guess
            isCallAnswered = false;
            android.util.Log.d("PhoneStateReceiver", "RINGING state detected. Number: " + lastNumber + " (SubId Guess: " + lastSubId + ")");

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            isCallAnswered = true;
            android.util.Log.d("PhoneStateReceiver", "OFFHOOK state detected.");

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            android.util.Log.d("PhoneStateReceiver", "IDLE state detected. Was Call Answered (based on receiver state): " + isCallAnswered);

            // **************************************************************************
            // REMOVED: Logic to detect missed call and start service from receiver.
            // This is now handled reliably by the PhoneStateListener in MonitorService.
            // **************************************************************************
            /*
            if (!isCallAnswered && lastNumber != null) {
                long now = System.currentTimeMillis();
                // Debouncing logic removed - service handles it
                // Long lastProcessed = processedNumbers.getOrDefault(lastNumber, 0L);
                // if (now - lastProcessed > 10_000) {
                    // processedNumbers.put(lastNumber, now);
                    android.util.Log.d("PhoneStateReceiver", "Missed call detected in Receiver for number: " + lastNumber + " (SIM guess: " + lastSubId + ")");
                    android.util.Log.d("PhoneStateReceiver", "--> Service start from receiver is REMOVED. Service listener should handle this.");

                    // String simInfo = getSimInfo(context, lastSubId); // Get potentially incorrect info
                    // Intent serviceIntent = new Intent(context, MonitorService.class);
                    // serviceIntent.setAction(MonitorService.ACTION_PROCESS_CALL);
                    // serviceIntent.putExtra("incomingNumber", lastNumber);
                    // serviceIntent.putExtra("simInfo", simInfo); // Pass potentially incorrect info
                    // ContextCompat.startForegroundService(context, serviceIntent); // DO NOT START SERVICE FROM HERE
                // } else {
                //    android.util.Log.d("PhoneStateReceiver", "Skipped duplicate missed call in receiver: " + lastNumber);
                // }
            }
            */

            // Reset receiver's static state variables
            lastNumber = null;
            lastSubId = -1;
            isCallAnswered = false;
        }
        // Optional: Start the service if it's not running? This can be complex.
        // Usually handled by BOOT_COMPLETED and keeping service sticky/foreground.
    }

    // Check if we have the required permissions (Keep this helper)
    private boolean hasPhoneStatePermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    // Get SIM card info - kept for potential debugging/logging, but not used to start service.
    // Note: This method might still be inaccurate if the subId passed to it is wrong.
    private String getSimInfo(Context context, int subId) {
        if (subId == -1) return "未知SIM卡 (SubId: -1)"; // More specific unknown

        // If we don't have permission, return a basic identifier
        if (!hasPhoneStatePermission(context)) {
            android.util.Log.w("PhoneStateReceiver", "getSimInfo: No READ_PHONE_STATE permission.");
            return "无权限获取SIM信息 (ID: " + subId +")";
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager subscriptionManager = (SubscriptionManager) context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE); // Use correct cast
                if (subscriptionManager != null) {
                    SubscriptionInfo subscriptionInfo = null;
                    try {
                        // Explicit permission check before accessing SubscriptionManager APIs
                        // Already checked above, but double check doesn't hurt
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            subscriptionInfo = subscriptionManager.getActiveSubscriptionInfo(subId);
                        } else {
                            // Should not happen if hasPhoneStatePermission passed, but handle defensively
                            return "无权限获取SIM信息 (ID: " + subId +")";
                        }
                    } catch (SecurityException e) {
                        android.util.Log.e("PhoneStateReceiver", "Security exception in getSimInfo lookup: " + e.getMessage());
                        return "安全异常获取SIM信息 (ID: " + subId + ")";
                    }

                    if (subscriptionInfo != null) {
                        int slotIndex = subscriptionInfo.getSimSlotIndex();
                        CharSequence displayNameCs = subscriptionInfo.getDisplayName();
                        String displayName = (displayNameCs != null ? displayNameCs.toString() : "N/A");
                        return "SIM" + (slotIndex + 1) + " (" + displayName + ", ID:" + subId + ")";
                    } else {
                        // Try finding by slot index if subId is maybe a slot index? (Less likely)
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                            List<SubscriptionInfo> subscriptions = subscriptionManager.getActiveSubscriptionInfoList();
                            if (subscriptions != null) {
                                for (SubscriptionInfo info : subscriptions) {
                                    // Check if the passed 'subId' matches a slot index
                                    if (info.getSimSlotIndex() == subId) {
                                        CharSequence dn = info.getDisplayName();
                                        return "SIM" + (info.getSimSlotIndex() + 1) + " (" + (dn != null ? dn.toString() : "N/A") +
                                                ", ID:" + info.getSubscriptionId() + ", Matched Slot)";
                                    }
                                }
                            }
                        }
                        android.util.Log.w("PhoneStateReceiver", "getSimInfo: No SubscriptionInfo found for subId: " + subId);
                        return "未知SIM卡 (ID: " + subId + ")"; // Return ID if no info found
                    }
                } else {
                    android.util.Log.w("PhoneStateReceiver", "getSimInfo: SubscriptionManager is null");
                }
            } else {
                // Pre-Lollipop MR1 - Very basic guess
                return "SIM ID " + subId + " (Legacy Android)";
            }
        } catch (Exception e) {
            android.util.Log.e("PhoneStateReceiver", "General exception in getSimInfo: " + e.getMessage());
            e.printStackTrace(); // Log stack trace for debugging
        }

        // Fallback if errors occurred
        return "错误获取SIM信息 (ID: " + subId +")";
    }
}