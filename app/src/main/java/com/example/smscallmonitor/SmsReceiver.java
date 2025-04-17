package com.example.smscallmonitor;
import android.content.*;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony; // Import Telephony for Actions
import android.telephony.SmsMessage;
// import android.telephony.SubscriptionInfo; // 不再需要
import android.telephony.SubscriptionManager;
import android.util.Log; // Import Log
import androidx.core.content.ContextCompat;

// 广播接收器，负责监听系统发出的短信接收广播
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver"; // 日志 TAG

    @Override
    public void onReceive(Context context, Intent intent) {
        // 首先检查接收到的 Intent 的 Action 是否是短信接收 Action
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            // 如果不是，记录警告并直接返回，不处理
            Log.w(TAG,"Received intent with unexpected action: " + intent.getAction());
            return;
        }

        Log.d(TAG, "SMS Received"); // 记录收到短信事件
        Bundle bundle = intent.getExtras(); // 获取 Intent 中的 Bundle 数据
        if (bundle != null) {
            // 从 Bundle 中提取 PDU (Protocol Data Unit) 数据，这是短信的原始格式
            Object[] pdus = (Object[]) bundle.get("pdus");
            // 显式检查 pdus 数组是否为 null，防止 NullPointerException
            if (pdus == null) {
                Log.e(TAG, "pdus array is null in received SMS intent.");
                return; // 如果为 null，无法处理，直接返回
            }

            String format = bundle.getString("format"); // 获取短信的编码格式 (例如 "3gpp" 或 "3gpp2")

            StringBuilder fullMessageContent = new StringBuilder(); // 用于拼接可能分段的长短信内容
            String sender = null; // 发件人号码
            SmsMessage message = null; // 用于存储从 PDU 解析出的第一条 SmsMessage 对象
            long timestamp = 0; // 新增: 用于存储短信的时间戳 (毫秒)

            // 获取接收短信的 SIM 卡 Subscription ID
            // 主要尝试 "subscription" 这个 key
            int subId = bundle.getInt("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
            // 如果主 key 不存在，尝试备用 key (适用于某些旧设备或系统)
            if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                subId = bundle.getInt("android.telephony.extra.SUBSCRIPTION_INDEX", SubscriptionManager.INVALID_SUBSCRIPTION_ID);
                Log.d(TAG, "Used fallback 'android.telephony.extra.SUBSCRIPTION_INDEX' for subId: " + subId); // 记录使用了备用 key
            }
            Log.d(TAG, "Received SMS on SubId: " + subId); // 记录获取到的 SubId


            // 遍历所有的 PDU 数据 (一条长短信可能包含多个 PDU)
            for (Object pdu : pdus) {
                byte[] pduBytes = (byte[]) pdu; // 将 PDU 对象转换为字节数组
                if (pduBytes == null) {
                    Log.w(TAG, "Skipping null PDU byte array."); // 跳过空的 PDU
                    continue;
                }

                SmsMessage currentMessage; // 当前 PDU 解析出的 SmsMessage 对象
                try {
                    // 根据 Android 版本选择合适的创建 SmsMessage 的方法
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6.0 (API 23) 及以上，使用带 format 的方法
                        currentMessage = SmsMessage.createFromPdu(pduBytes, format);
                    } else { // 旧版本，使用不带 format 的方法
                        currentMessage = SmsMessage.createFromPdu(pduBytes);
                    }
                } catch (Exception e) {
                    // 处理解析 PDU 时可能发生的异常
                    Log.e(TAG, "Error creating SmsMessage from PDU: " + e.getMessage());
                    continue; // 跳过这个无法解析的 PDU
                }


                // 从第一个有效的 PDU 中获取发件人和时间戳
                // 确保 message 只被赋值一次，并且 currentMessage 不为 null
                if (message == null && currentMessage != null) {
                    message = currentMessage; // 保存第一个消息对象
                    sender = message.getDisplayOriginatingAddress(); // 获取显示的发件人号码
                    timestamp = message.getTimestampMillis(); // 获取短信中心的时间戳
                    Log.d(TAG,"Extracted sender: " + sender + ", timestamp: " + timestamp); // 记录提取到的信息
                }

                // 拼接短信内容
                // 再次检查 currentMessage 是否为 null (理论上如果上面不为 null 这里应该也不为 null)
                if (currentMessage != null) {
                    String bodyPart = currentMessage.getMessageBody(); // 获取当前 PDU 的消息体部分
                    if (bodyPart != null) {
                        fullMessageContent.append(bodyPart); // 追加到完整内容中
                    } else {
                        Log.w(TAG,"SMS message body part is null for a PDU."); // 记录某个 PDU 的消息体为空
                    }
                }
            } // PDU 循环结束

            // 只有在成功获取到发件人号码并且有短信内容时，才继续处理
            if (sender != null && fullMessageContent.length() > 0) {
                // 记录将要处理的完整短信信息
                Log.d(TAG, "Processing complete SMS from: " + sender + " on SubId: " + subId + " with content length: " + fullMessageContent.length());

                // --- 修改: 构建启动 MonitorService 的 Intent ---
                Intent serviceIntent = new Intent(context, MonitorService.class);
                // 设置 Action 为短信接收 Action，让 MonitorService 知道事件类型
                serviceIntent.setAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
                // 将提取到的短信数据放入 Intent 的 extras 中，传递给 Service
                serviceIntent.putExtra("sender", sender); // 发件人
                serviceIntent.putExtra("content", fullMessageContent.toString()); // 完整内容
                serviceIntent.putExtra("subId", subId); // SIM 卡 ID
                serviceIntent.putExtra("timestamp", timestamp); // 短信时间戳
                // 不再需要传递 simInfo 字符串，MonitorService 会根据 subId 自行获取
                Log.d(TAG,"Prepared service intent with SMS data to start MonitorService."); // 记录 Intent 准备完成
                // --- 修改结束 ---

                // 根据 Android 版本选择合适的启动服务方式
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0 (API 26) 及以上
                    Log.d(TAG,"Starting foreground service for SMS processing."); // 记录启动前台服务
                    // 使用 startForegroundService 启动，Service 内部需要在 5 秒内调用 startForeground
                    ContextCompat.startForegroundService(context, serviceIntent);
                } else { // Android 8.0 以下
                    Log.d(TAG,"Starting background service for SMS processing."); // 记录启动后台服务
                    context.startService(serviceIntent); // 直接启动服务
                }
            } else {
                // 如果遍历完所有 PDU 后，仍然缺少发件人或内容，则记录警告并不处理
                Log.w(TAG, "SMS sender or message content is null/empty after processing all PDUs. Cannot process.");
            }
        } else {
            // 如果 Intent 中的 Bundle 为 null，记录警告
            Log.w(TAG, "SMS received intent bundle is null.");
        }
    } // onReceive 方法结束
} // SmsReceiver 类结束