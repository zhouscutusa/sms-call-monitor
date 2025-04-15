package com.example.smscallmonitor;

import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class EmailSender {
    public static void send(String subject, String body) {
        final String user = "发送邮件@gmail.com";
        final String password = "应用密码";

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password);
                    }
                });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user));
            message.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse("接收邮箱1@gmail.com,接收邮箱2@gmail.com,接收邮箱3@gmail.com"));
            message.setSubject(subject);
//            message.setText(body);
            // html email body:
            message.setContent(body, "text/html;charset=UTF-8");

            Transport.send(message);
            System.out.println("✅ 邮件发送成功！");
        } catch (MessagingException e) {
            System.err.println("❌ 邮件发送失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
}
