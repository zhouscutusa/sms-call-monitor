package com.example.smscallmonitor;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_events") // 表名: 待处理事件
public class PendingEvent {

    @PrimaryKey(autoGenerate = true)
    public int id; // 主键, 自增

    public String eventType; // 事件类型: "SMS" 或 "CALL"
    public String senderNumber; // 发件人/来电号码
    public String messageContent; // 短信内容 (来电时为 null 或特定标记)
    public long eventTimestamp; // 事件发生的时间戳 (毫秒)
    public String simInfo; // SIM 卡信息 (例如 "SIM1 (Operator, ID:1)")
    public int subId; // Subscription ID
    public String status; // 状态: "PENDING" (待发送)
    public long attemptTimestamp; // 上次尝试发送包含此事件的邮件的时间戳
    public int retryCount; // 包含此事件的邮件被尝试发送的次数

    // --- 状态常量 ---
    public static final String STATUS_PENDING = "PENDING";
    // public static final String STATUS_SENT = "SENT"; // 如果需要标记已发送而不是删除

    // --- 构造函数 (Room 需要一个无参构造) ---
    public PendingEvent() {}

    // --- 便捷构造函数 ---
    public PendingEvent(String eventType, String senderNumber, String messageContent,
                        long eventTimestamp, String simInfo, int subId) {
        this.eventType = eventType;
        this.senderNumber = senderNumber;
        this.messageContent = messageContent;
        this.eventTimestamp = eventTimestamp;
        this.simInfo = simInfo;
        this.subId = subId;
        this.status = STATUS_PENDING; // 初始状态为待处理
        this.attemptTimestamp = 0;
        this.retryCount = 0;
    }
}