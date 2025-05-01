package com.example.smscallmonitor;

import android.content.Context; // 需要 Context
import android.content.SharedPreferences; // 需要 SharedPreferences
import android.util.Log; // 使用 Android Log

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

// 负责发送电子邮件的工具类
public class EmailSender {

    private static final String TAG = "EmailSender"; // 日志 TAG
    // 硬编码的发件人凭证，极不安全，仅用于示例！实际应用必须安全存储或让用户输入
    private static final String SENDER_USER = "发送邮件@gmail.com"; // 发件人 Gmail 邮箱
    private static final String SENDER_PASSWORD = "应用密码"; // 发件人 Gmail 应用专用密码

    /**
     * 发送邮件的方法
     * @param subject 邮件主题
     * @param body 邮件正文 (HTML 格式)
     * @return true 如果邮件发送尝试成功 (不保证送达)，false 如果在发送过程中发生错误
     */
    public static boolean sendEmail(Context context, String subject, String body) {
        if(!SettingsValues.isEmailNotificationEnabled(context)) {
            return true;
        }
        String recipients = SettingsValues.getEmailRecipients(context);
        if(null == recipients || "".equals(recipients) || null == body || "".equals(body)) {
            return true;
        }
        return send(subject, body, recipients);
    }
    /**
     * 发送邮件到GoogleVoice的方法
     * @param body 邮件正文（纯文本）
     * @return true 如果邮件发送尝试成功 (不保证送达)，false 如果在发送过程中发生错误
     */
    public static boolean sendGv(Context context, String body) {
        if(!SettingsValues.isGVNotificationEnabled(context)) {
            return true;
        }
        String subject = "Re: New text message from";
        String recipients = SettingsValues.getGVRecipients(context);
        if(null == recipients || "".equals(recipients) || null == body || "".equals(body)) {
            return true;
        }
        return send(subject, body, recipients);
    }

    /**
     * 发送邮件的方法
     * @param subject 邮件主题
     * @param body 邮件正文
     * @param recipient 收件人
     * @return true 如果邮件发送尝试成功 (不保证送达)，false 如果在发送过程中发生错误
     */
    public static boolean send(String subject, String body, String recipient) {
        // SMTP 服务器配置 (Gmail)
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true"); // 需要认证
        props.put("mail.smtp.starttls.enable", "true"); // 启用 STARTTLS 加密
        props.put("mail.smtp.host", "smtp.gmail.com"); // Gmail SMTP 服务器地址
        props.put("mail.smtp.port", "587"); // Gmail SMTP TLS 端口
        // 添加超时设置，防止长时间阻塞 (单位：毫秒)
        props.put("mail.smtp.connectiontimeout", "20000"); // 连接超时 20 秒
        props.put("mail.smtp.timeout", "20000");           // 读取/写入超时 20 秒
        Log.d(TAG, "SMTP Properties configured for gmail."); // 配置完成日志

        // 创建邮件 Session，提供认证信息
        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() { // 匿名内部类实现认证器
                    protected PasswordAuthentication getPasswordAuthentication() {
                        // 返回发件人用户名和密码 (或应用专用密码)
                        return new PasswordAuthentication(SENDER_USER, SENDER_PASSWORD);
                    }
                });

        try {
            // 创建 MimeMessage 对象
            Message message = new MimeMessage(session);
            // 设置发件人地址
            message.setFrom(new InternetAddress(SENDER_USER));
            // 设置收件人地址 (可以有多个，用逗号分隔)
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(recipient));
            // 设置邮件主题
            message.setSubject(subject);
            // 设置邮件内容为 HTML 格式，并指定 UTF-8 编码
            message.setContent(body, "text/html;charset=UTF-8");

            // 记录尝试发送邮件的日志
            Log.d(TAG, "Attempting to send email via Transport.send(). Subject: " + subject);
            // 执行发送操作
            Transport.send(message);
            // 如果 Transport.send() 没有抛出异常，认为发送尝试成功
            Log.i(TAG, "✅ Email sent successfully (Transport.send completed)! Subject: " + subject); // 记录成功日志
            return true; // 返回 true 表示发送尝试成功

        } catch (AuthenticationFailedException e) {
            // 处理认证失败异常 (通常是用户名或密码错误)
            Log.e(TAG, "❌ Email sending failed: Authentication Error - Check sender credentials or App Password settings. " + e.getMessage());
            return false; // 返回 false 表示失败
        } catch (MessagingException e) {
            // 处理其他邮件相关的异常 (网络问题、地址格式错误、服务器拒绝等)
            Log.e(TAG, "❌ Email sending failed: Messaging Error - " + e.getMessage());
            // 可以检查 e.getCause() 来获取更底层的异常信息，例如网络异常
            Throwable cause = e.getCause();
            if (cause != null) {
                Log.e(TAG, "   Cause: " + cause.getClass().getName() + " - " + cause.getMessage());
            }
            return false; // 返回 false 表示失败
        } catch (Exception e) {
            // 捕获其他所有未预料到的异常，确保程序不会崩溃
            Log.e(TAG, "❌ Unexpected error during email sending: " + e.getMessage(), e); // 记录完整异常堆栈
            return false; // 返回 false 表示失败
        }
    } // send 方法结束

} // EmailSender 类结束