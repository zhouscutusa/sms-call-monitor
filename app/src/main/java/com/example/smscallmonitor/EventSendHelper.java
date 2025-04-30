package com.example.smscallmonitor;

import android.Manifest;
import android.annotation.SuppressLint; // 需要SuppressLint
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.SystemClock; // 引入 SystemClock 用于计时
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Collections; // 引入 Collections
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors; // 需要 Java 8+

// 辅助类，封装合并发送的核心逻辑
public class EventSendHelper {

    private static final String TAG = "EventSendHelper"; // 日志 TAG
    // SIM 卡颜色数组
    private static final String[] SIM_COLORS = {
            "#1E90FF", // DodgerBlue
            "#7d3c98", // SeaGreen
            "#DAA520", // GoldenRod
            "#9400D3", // DarkViolet
            "#FF8C00", // DarkOrange
            "#4682B4", // SteelBlue
            "#f39c12", // SaddleBrown
            "#DB7093", // PaleVioletRed
            "#5F9EA0", // CadetBlue
            "#708090"  // SlateGray
    };

    // --- 发送状态枚举 ---
    public enum SendStatus {
        SEND_SUCCESS,          // 发送成功
        SEND_FAILED_NETWORK,   // 因网络问题失败 (包括无网络、连接超时)
        SEND_FAILED_OTHER      // 因其他问题失败 (如认证、格式化、邮件服务器错误等)
    }
    // --- 枚举结束 ---


    // --- 尝试立即发送单个事件的方法 (带状态返回) ---
    /**
     * 尝试立即发送单个新事件的邮件，并返回详细状态。
     * **此方法不操作数据库。**
     * @param context 应用上下文
     * @param event 要尝试发送的新事件
     * @param wifiManager Wi-Fi 管理器
     * @param connManager 网络连接管理器
     * @return SendStatus 枚举，指示发送结果
     */
    public static SendStatus trySendSingleEventImmediately(Context context, PendingEvent event, WifiManager wifiManager, ConnectivityManager connManager) {
        Log.i(TAG, ">>> [Immediate Attempt] Starting for event: Type=" + event.eventType + ", From=" + event.senderNumber);

        // 1. 确保网络连接 (使用轮询等待)
        if (!isNetworkAvailable(connManager)) {
            Log.w(TAG, ">>> [Immediate Attempt] Network initially unavailable. Attempting to enable Wi-Fi...");
            if (!ensureWifiEnabled(context, wifiManager)) {
                Log.e(TAG, ">>> [Immediate Attempt] Failed to enable Wi-Fi. Returning SEND_FAILED_NETWORK.");
                return SendStatus.SEND_FAILED_NETWORK; // 无法开启 Wi-Fi，网络失败
            }
            // 轮询等待网络连接
            Log.d(TAG, ">>> [Immediate Attempt] Waiting for network connection (up to " + IConstants.WIFI_CONNECT_TIMEOUT_MS + "ms)...");
            long startTime = SystemClock.elapsedRealtime();
            boolean connected = false;
            while (SystemClock.elapsedRealtime() - startTime < IConstants.WIFI_CONNECT_TIMEOUT_MS) {
                if (isNetworkAvailable(connManager)) {
                    connected = true;
                    Log.i(TAG, ">>> [Immediate Attempt] Network became available after " + (SystemClock.elapsedRealtime() - startTime) + "ms.");
                    break;
                }
                try {
                    Log.d(TAG, ">>> [Immediate Attempt] Network check pending, sleeping for " + IConstants.NETWORK_CHECK_INTERVAL_MS + "ms..."); // 添加等待日志
                    Thread.sleep(IConstants.NETWORK_CHECK_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt(); // 重新设置中断状态
                    Log.w(TAG, ">>> [Immediate Attempt] Network wait loop interrupted. Returning SEND_FAILED_NETWORK.");
                    return SendStatus.SEND_FAILED_NETWORK; // 等待被中断，网络失败
                }
            }
            if (!connected) {
                Log.e(TAG, ">>> [Immediate Attempt] Network did not become available within timeout. Returning SEND_FAILED_NETWORK.");
                return SendStatus.SEND_FAILED_NETWORK; // 超时未连接，网络失败
            }
        } else {
            Log.i(TAG, ">>> [Immediate Attempt] Network already available.");
        }

        // 2. 格式化单个事件的邮件内容
        //    - 复用多表格的格式化方法，传入只包含单个事件的列表
        Log.d(TAG, ">>> [Immediate Attempt] Formatting single event email body...");
        String singleEventBody = formatConsolidatedEmailBodyMultipleTablesInlineStyles(Collections.singletonList(event));
        String singleEventPlainText = formatConsolidatedPlainText(Collections.singletonList(event));
        if (singleEventBody == null || singleEventBody.contains("内部错误")) { // 检查是否为空或包含错误信息
            Log.e(TAG, ">>> [Immediate Attempt] Failed to format single event email body. Returning SEND_FAILED_OTHER.");
            return SendStatus.SEND_FAILED_OTHER; // 格式化失败
        }

        // 3. 生成邮件主题
        String subject = "短信/来电通知"; // 单个事件的主题

        // 4. 尝试发送邮件
        Log.d(TAG, ">>> [Immediate Attempt] Sending single event email...");
        boolean sent;
        try {
            sent = EmailSender.sendEmail(subject, singleEventBody);
            EmailSender.sendGv(singleEventPlainText);
        } catch (Exception e) {
            // 捕获 EmailSender.send() 可能抛出的未明确处理的异常
            Log.e(TAG, ">>> [Immediate Attempt] Exception during EmailSender.send(): " + e.getMessage(), e);
            sent = false;
        }


        // 5. 根据发送结果返回状态
        if (sent) {
            Log.i(TAG, ">>> [Immediate Attempt] Successfully sent single event email. Returning SEND_SUCCESS.");
            return SendStatus.SEND_SUCCESS;
        } else {
            Log.w(TAG, ">>> [Immediate Attempt] Failed to send single event email. Returning SEND_FAILED_OTHER.");
            // 将所有 EmailSender 返回 false 的情况归为 SEND_FAILED_OTHER
            // 周期性任务会处理这些失败
            return SendStatus.SEND_FAILED_OTHER;
        }
    }
    // --- 尝试立即发送单个事件的方法结束 ---


    /**
     * [周期性任务使用] 执行合并发送的核心逻辑。查询数据库中所有 PENDING 事件并合并发送。
     * @param context 应用上下文
     * @param dao 数据库访问对象 (PendingEventDao)
     * @param wifiManager Wi-Fi 管理器
     * @param connManager 网络连接管理器
     * @return true 如果发送成功或没有事件需要发送； false 如果发送失败需要重试
     */
    public static boolean performConsolidatedSend(Context context, PendingEventDao dao, WifiManager wifiManager, ConnectivityManager connManager) {
        // 这个方法用于周期性 Worker，查询并合并发送所有失败暂存的事件
        List<PendingEvent> eventsToSend;
        try {
            Log.d(TAG, ">>> [Periodic] Querying pending events from database..."); // 添加日志区分
            eventsToSend = dao.getAllPendingEvents(PendingEvent.STATUS_PENDING);
        } catch (Exception e) {
            Log.e(TAG, ">>> [Periodic] Error querying pending events from DB: " + e.getMessage());
            return false;
        }

        if (eventsToSend == null || eventsToSend.isEmpty()) {
            Log.i(TAG, ">>> [Periodic] No pending events found to send.");
            return true; // 数据库为空，任务成功完成
        }

        Log.i(TAG, ">>> [Periodic] Found " + eventsToSend.size() + " pending event(s) to consolidate and send.");

        // 步骤 1: 确保网络连接
        if (!isNetworkAvailable(connManager)) {
            Log.w(TAG, ">>> [Periodic] Network not available. Attempting to enable Wi-Fi...");
            if (!ensureWifiEnabled(context, wifiManager)) {
                Log.e(TAG, ">>> [Periodic] Failed to enable Wi-Fi. Send attempt failed.");
                return false;
            }
            Log.d(TAG, ">>> [Periodic] Waiting for network connection (up to " + IConstants.WIFI_CONNECT_TIMEOUT_MS + "ms)...");
            long startTime = SystemClock.elapsedRealtime();
            boolean connected = false;
            while (SystemClock.elapsedRealtime() - startTime < IConstants.WIFI_CONNECT_TIMEOUT_MS) {
                if (isNetworkAvailable(connManager)) { connected = true; Log.i(TAG, ">>> [Periodic] Network became available."); break; }
                try { Thread.sleep(IConstants.NETWORK_CHECK_INTERVAL_MS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return false;}
            }
            if (!connected) { Log.e(TAG, ">>> [Periodic] Network did not become available within timeout. Send attempt failed."); return false; }
        } else {
            Log.i(TAG, ">>> [Periodic] Network is already available.");
        }

        // 步骤 2: 格式化合并邮件
        String subject = "短信/来电报告 (补发)"; // 周期性补发的邮件主题
        Log.d(TAG, ">>> [Periodic] Formatting consolidated email body...");
        String consolidatedBody = formatConsolidatedEmailBodyMultipleTablesInlineStyles(eventsToSend);
        String singleEventPlainText = formatConsolidatedPlainText(eventsToSend);
        if (consolidatedBody == null || consolidatedBody.contains("内部错误")) { // 检查格式化结果
            Log.e(TAG, ">>> [Periodic] Failed to format consolidated email body.");
            // 格式化失败通常是程序逻辑问题，可能需要更新重试计数但不是网络问题
            // 这里简单返回 false，让 Worker 重试
            try {
                // 更新尝试信息，避免无限次因格式化失败而重试
                List<Integer> eventIds = eventsToSend.stream().map(event -> event.id).collect(Collectors.toList());
                dao.updateEventsAttemptInfoByIds(eventIds, System.currentTimeMillis());
            } catch (Exception e) { Log.e(TAG, "[Periodic] Error updating attempt info after format failure: " + e); }
            return false;
        }

        // 步骤 3: 尝试发送邮件
        Log.d(TAG, ">>> [Periodic] Attempting to send consolidated email...");
        boolean sent = EmailSender.sendEmail(subject, consolidatedBody);
        EmailSender.sendGv(singleEventPlainText);

        // 步骤 4: 根据发送结果更新数据库
        List<Integer> eventIds = eventsToSend.stream().map(event -> event.id).collect(Collectors.toList());
        if (sent) {
            Log.i(TAG, ">>> [Periodic] Consolidated email sent successfully for " + eventIds.size() + " events.");
            try {
                Log.d(TAG, ">>> [Periodic] Deleting sent events from DB: IDs " + eventIds);
                dao.deleteEventsByIds(eventIds); // 发送成功，删除
            } catch (Exception e) { Log.e(TAG, ">>> [Periodic] Error deleting sent events from DB: " + e.getMessage()); }
            return true; // 任务成功
        } else {
            Log.w(TAG, ">>> [Periodic] Failed to send consolidated email for " + eventIds.size() + " events.");
            try {
                Log.d(TAG, ">>> [Periodic] Updating attempt info for failed events in DB: IDs " + eventIds);
                dao.updateEventsAttemptInfoByIds(eventIds, System.currentTimeMillis()); // 发送失败，更新尝试信息
            } catch (Exception e) { Log.e(TAG, ">>> [Periodic] Error updating attempt info for failed events in DB: " + e.getMessage()); }
            return false; // 任务失败，需要重试
        }
    } // performConsolidatedSend 方法结束


    /**
     * [最终内联样式版本 + SIM颜色] 将事件列表格式化为单个 HTML 邮件正文，每个事件一个独立的、两列的表格。
     * @param events 待格式化的事件列表 (可以包含一个或多个事件)
     * @return 格式化后的 HTML 字符串, 或包含错误信息的 HTML， 或在极少数情况下为 null
     */
    private static String formatConsolidatedEmailBodyMultipleTablesInlineStyles(List<PendingEvent> events) {
        // (这个方法的内部逻辑保持不变)
        if (events == null || events.isEmpty()) {
            Log.w(TAG, ">>> formatConsolidatedEmailBodyMultipleTablesInlineStyles called with empty or null list.");
            return "<h3>本次报告无待处理事件。</h3>";
        }

        try { // 添加 try-catch 块捕获格式化过程中的潜在异常
            StringBuilder htmlBody = new StringBuilder();
            htmlBody.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>事件报告</title></head>");
            htmlBody.append("<body style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Fira Sans', 'Droid Sans', 'Helvetica Neue', sans-serif; line-height: 1.5; color: #333333; margin: 0; padding: 10px; background-color: #f4f4f4;\">");

            // 根据事件数量调整主标题
            if (events.size() == 1) {
                PendingEvent singleEvent = events.get(0);
                String eventTypeDisplay = "SMS".equalsIgnoreCase(singleEvent.eventType) ? "短信" : ("CALL".equalsIgnoreCase(singleEvent.eventType) ? "未接来电" : "事件");
                htmlBody.append("<h2 style=\"color: #222222; margin-bottom: 8px; font-size: 1.4em;\">").append(eventTypeDisplay).append(" 通知</h2>");
            } else {
                htmlBody.append("<h2 style=\"color: #222222; margin-bottom: 8px; font-size: 1.4em;\">短信/来电 事件报告 (").append(events.size()).append("条)</h2>"); // 在标题中显示事件数量
            }
            htmlBody.append("<p style=\"color: #666666; font-size: 0.9em; margin-top: 0; margin-bottom: 15px;\">报告生成时间: ").append(TimeUtil.getCurrentFormattedTime()).append("</p>");
            htmlBody.append("<hr style=\"border: none; border-top: 1px solid #cccccc; margin: 25px 0;\">");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            for (int i = 0; i < events.size(); i++) {
                PendingEvent event = events.get(i);
                Log.d(TAG, ">>> Formatting event " + (i+1) + "/" + events.size() + ": Type=" + event.eventType + ", From=" + event.senderNumber);

                htmlBody.append("<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse: collapse; width: 100%; max-width: 600px; margin-bottom: 25px; border: 1px solid #cccccc; background-color: #ffffff; border-radius: 5px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); overflow: hidden;\">");
                htmlBody.append("<tbody>");

                addRowInlineStyle(htmlBody, "事件时间", sdf.format(new Date(event.eventTimestamp)), null);

                String typeStr, typeStyle;
                if ("SMS".equalsIgnoreCase(event.eventType)) { typeStr = "收到短信"; typeStyle = "color: #008000; font-weight: bold;"; }
                else if ("CALL".equalsIgnoreCase(event.eventType)) { typeStr = "未接来电"; typeStyle = "color: #cc0000; font-weight: bold;"; }
                else { typeStr = "未知类型"; typeStyle = "color: #555555; font-weight: bold;"; }
                addRowInlineStyle(htmlBody, "类型", typeStr, typeStyle);

                addRowInlineStyle(htmlBody, "号码", escapeHtml(event.senderNumber), null);

                String contentStr;
                if ("SMS".equalsIgnoreCase(event.eventType)) { contentStr = (event.messageContent != null) ? escapeHtml(event.messageContent) : "(内容为空)"; }
                else { contentStr = "(此类型无内容)"; }
                addRowInlineStyle(htmlBody, "内容/详情", contentStr, null);

                String simDisplay = (event.simInfo != null ? escapeHtml(event.simInfo) : "未知SIM") + " (ID:" + event.subId + ")";
                int colorIndex = Math.abs(event.subId) % SIM_COLORS.length;
                String simColor = SIM_COLORS[colorIndex];
                String simDataStyle = "color: " + simColor + "; font-weight: bold;";
                addRowInlineStyle(htmlBody, "SIM卡", simDisplay, simDataStyle);

                htmlBody.append("</tbody>");
                htmlBody.append("</table>");
            }

            htmlBody.append("</body></html>");
            Log.d(TAG, ">>> Finished formatting email body with multiple tables (Inline Styles + SIM Colors).");

            return htmlBody.toString();

        } catch (Exception e) {
            Log.e(TAG, ">>> Exception during formatting email body: " + e.getMessage(), e);
            return null; // 格式化异常返回 null
        }
    }


    /**
     * 格式化为文本内容
     */
    private static String formatConsolidatedPlainText(List<PendingEvent> events) {
        if (events == null || events.isEmpty()) {
            Log.w(TAG, ">>> formatConsolidatedPlainText called with empty or null list.");
            return "本次报告无待处理事件。";
        }

        try { // 添加 try-catch 块捕获格式化过程中的潜在异常
            StringBuilder htmlBody = new StringBuilder();
            String newMessage = "  ////  ";
            String newLine = "  | ";

            // 根据事件数量调整主标题
            if (events.size() == 1) {
                PendingEvent singleEvent = events.get(0);
                String eventTypeDisplay = "SMS".equalsIgnoreCase(singleEvent.eventType) ? "短信" : ("CALL".equalsIgnoreCase(singleEvent.eventType) ? "未接来电" : "事件");
                htmlBody.append("").append(eventTypeDisplay).append(newMessage);
            } else {
                htmlBody.append("短信/来电 事件报告 (").append(events.size()).append("条)").append(newMessage); // 在标题中显示事件数量
            }
//            htmlBody.append("报告生成时间: ").append(TimeUtil.getCurrentFormattedTime()).append(newLine);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

            for (int i = 0; i < events.size(); i++) {
                PendingEvent event = events.get(i);

                htmlBody.append(i + 1).append(".时间: ").append(sdf.format(new Date(event.eventTimestamp))).append(newLine);

                String typeStr;
                if ("SMS".equalsIgnoreCase(event.eventType)) { typeStr = "收到短信"; }
                else if ("CALL".equalsIgnoreCase(event.eventType)) { typeStr = "未接来电"; }
                else { typeStr = "未知类型"; }
                htmlBody.append("类型: ").append(typeStr).append(newLine);

                htmlBody.append("号码: ").append(event.senderNumber).append(newLine);

                String contentStr;
                if ("SMS".equalsIgnoreCase(event.eventType)) { contentStr = (event.messageContent != null) ? escapeHtml(event.messageContent) : "(内容为空)"; }
                else { contentStr = "(无)"; }
//                addRowInlineStyle(htmlBody, "内容/详情", contentStr, null);
                htmlBody.append("内容: ").append(contentStr).append(newLine);

                String simDisplay = (event.simInfo != null ? escapeHtml(event.simInfo) : "未知SIM") + " (ID:" + event.subId + ")";
//                int colorIndex = Math.abs(event.subId) % SIM_COLORS.length;
//                String simColor = SIM_COLORS[colorIndex];
//                String simDataStyle = "color: " + simColor + "; font-weight: bold;";
//                addRowInlineStyle(htmlBody, "SIM卡", simDisplay, simDataStyle);
                htmlBody.append("SIM卡: ").append(simDisplay).append(newMessage);

            }

            return htmlBody.toString();

        } catch (Exception e) {
            Log.e(TAG, ">>> Exception during formatting email body: " + e.getMessage(), e);
            return null; // 格式化异常返回 null
        }
    }

    /**
     * [内联样式版本] 辅助方法，用于向 StringBuilder 添加表格行 (<tr><td>标题</td><td>数据</td></tr>)
     * @param sb StringBuilder 对象
     * @param header 标题文字
     * @param data 数据文字
     * @param dataInlineStyle 应用于数据单元格的内联样式字符串 (例如 "color: red; font-weight: bold;" 或 null)
     */
    private static void addRowInlineStyle(StringBuilder sb, String header, String data, String dataInlineStyle) {
        // (此方法保持不变)
        sb.append("<tr>");
        sb.append("<td style=\"padding: 10px 15px; font-weight: bold; background-color: #f9f9f9; width: 90px; border-bottom: 1px solid #eeeeee; border-right: 1px solid #eeeeee; color: #555555; vertical-align: top;\">").append(header).append("</td>");
        String baseDataStyle = "padding: 10px 15px; vertical-align: top; border-bottom: 1px solid #eeeeee;";
        String finalDataStyle = baseDataStyle + (dataInlineStyle != null ? dataInlineStyle : "");
        sb.append("<td style=\"").append(finalDataStyle).append("\">").append(data).append("</td>");
        sb.append("</tr>");
    }


    /**
     * 对字符串进行 HTML 转义，替换特殊字符为 HTML 实体。
     * @param text 需要转义的原始文本
     * @return 转义后的 HTML 安全文本
     */
    private static String escapeHtml(String text) {
        if (text == null) return ""; // 处理 null 输入
        // 替换 HTML 特殊字符
        // 注意替换顺序，先替换 & 符号
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }


    // --- 网络检查辅助方法 (保持不变) ---
    private static boolean isNetworkAvailable(ConnectivityManager cm) {
        // (此方法保持不变)
        if (cm == null) { Log.e(TAG, "isNetworkAvailable: ConnectivityManager is null."); return false; }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork == null) {Log.d(TAG, "isNetworkAvailable (M+): No active network."); return false;}
                NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                boolean connected = capabilities != null &&
                        (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                         capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                         capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                return connected;
            } else {
                @SuppressLint("MissingPermission")
                android.net.NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
                boolean connected = activeNetworkInfo != null && activeNetworkInfo.isConnected();
                return connected;
            }
        } catch (Exception e) {
            Log.e(TAG, "isNetworkAvailable: Error checking network state: " + e.getMessage());
            return false;
        }
    }

    // 确保 Wi-Fi 开启的辅助方法 (保持不变)
    private static boolean ensureWifiEnabled(Context context, WifiManager wm) {
        // (此方法保持不变)
        if (wm == null) { Log.e(TAG, "ensureWifiEnabled: WifiManager is null."); return false; }
        if (wm.isWifiEnabled()) { Log.d(TAG,"ensureWifiEnabled: Wi-Fi is already enabled."); return true; }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (context.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "ensureWifiEnabled: CHANGE_WIFI_STATE permission not granted.");
                return false;
            }
            Log.d(TAG, "ensureWifiEnabled: Attempting to enable Wi-Fi...");
            try {
                boolean success = wm.setWifiEnabled(true);
                Log.d(TAG, "ensureWifiEnabled: wifiManager.setWifiEnabled(true) returned: " + success);
                return success;
            } catch (SecurityException se){
                 Log.e(TAG, "ensureWifiEnabled: SecurityException enabling Wi-Fi: " + se.getMessage());
                 return false;
            } catch (Exception e) {
                Log.e(TAG, "ensureWifiEnabled: Error enabling Wi-Fi: " + e.getMessage(), e);
                return false;
            }
        } else {
            Log.w(TAG, "ensureWifiEnabled: Cannot programmatically enable Wi-Fi on Android 10+.");
            return false;
        }
    }

    // --- resetWifiOffSchedule, scheduleWifiOffAlarm, cancelWifiOffAlarm 方法保持不变 ---
    protected static void resetWifiOffSchedule(AlarmManager alarmManager, PendingIntent wifiOffPendingIntent) {
        Log.d(TAG, ">>> Resetting Wi-Fi turn-off schedule (Cancelling and rescheduling alarm).");
        cancelWifiOffAlarm(alarmManager, wifiOffPendingIntent);
        scheduleWifiOffAlarm(alarmManager, wifiOffPendingIntent);
    }
    private static void scheduleWifiOffAlarm(AlarmManager alarmManager, PendingIntent wifiOffPendingIntent) {
        if (alarmManager != null && wifiOffPendingIntent != null) {
            long triggerAtMillis = System.currentTimeMillis() + IConstants.WIFI_OFF_DELAY_MS;
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, wifiOffPendingIntent);
                Log.i(TAG, ">>> Scheduled Wi-Fi off alarm for " + IConstants.WIFI_OFF_DELAY_MS + "ms from now.");
            } catch (SecurityException se) {
                Log.e(TAG, ">>> SecurityException scheduling Wi-Fi off alarm: " + se.getMessage());
            } catch (Exception e) {
                Log.e(TAG, ">>> Error scheduling Wi-Fi off alarm: " + e.getMessage());
            }
        } else {
            Log.e(TAG, ">>> Cannot schedule Wi-Fi off alarm: AlarmManager or PendingIntent is null.");
        }
    }
    protected static void cancelWifiOffAlarm(AlarmManager alarmManager, PendingIntent wifiOffPendingIntent) {
        if (alarmManager != null && wifiOffPendingIntent != null) {
            Log.d(TAG, ">>> Cancelling scheduled Wi-Fi off alarm.");
            alarmManager.cancel(wifiOffPendingIntent);
        } else {
            Log.w(TAG, ">>> Cannot cancel Wi-Fi off alarm: AlarmManager or PendingIntent is null.");
        }
    }


} // EventSendHelper 类结束