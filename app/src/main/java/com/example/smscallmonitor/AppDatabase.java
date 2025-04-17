package com.example.smscallmonitor;

import android.content.Context;
import android.util.Log; // 引入 Log
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
// 不需要迁移，因为这是第一个 Room 版本

// 定义数据库实体和版本号 (版本号从 1 开始)
@Database(entities = {PendingEvent.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract PendingEventDao pendingEventDao(); // 提供 PendingEventDao

    private static volatile AppDatabase INSTANCE;

    // 获取数据库实例的静态方法
    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) { // 同步锁，防止多线程问题
                if (INSTANCE == null) {
                    Log.d("AppDatabase", ">>> Creating new database instance..."); // 添加日志
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "event_monitor_database") // 数据库文件名
                            // 因为是版本 1，不需要迁移
                            // 如果以后升级结构，再添加 .addMigrations(...)
                            // 或者在开发时使用 .fallbackToDestructiveMigration() (会丢失数据)
                            .build();
                    Log.d("AppDatabase", ">>> Database instance created."); // 添加日志
                }
            }
        }
        return INSTANCE;
    }
}