package com.example.smscallmonitor;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface PendingEventDao {

    @Insert
    void insert(PendingEvent event); // 插入新事件

    // 查询所有待处理的事件，按时间戳升序排列
    @Query("SELECT * FROM pending_events WHERE status = :statusPending ORDER BY eventTimestamp ASC")
    List<PendingEvent> getAllPendingEvents(String statusPending);

    // 获取待处理事件的数量 (用于 Wi-Fi 关闭逻辑)
    @Query("SELECT COUNT(*) FROM pending_events WHERE status = :statusPending")
    int getPendingEventCount(String statusPending);

    // 通过 ID 列表删除事件 (发送成功后调用)
    @Query("DELETE FROM pending_events WHERE id IN (:ids)")
    void deleteEventsByIds(List<Integer> ids);

    // 更新指定 ID 事件的尝试信息 (发送失败后调用)
    @Query("UPDATE pending_events SET attemptTimestamp = :attemptTime, retryCount = retryCount + 1 WHERE id IN (:ids)")
    void updateEventsAttemptInfoByIds(List<Integer> ids, long attemptTime);

}