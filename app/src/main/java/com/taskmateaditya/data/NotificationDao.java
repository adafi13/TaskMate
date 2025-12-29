package com.taskmateaditya.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NotificationDao {
    @Insert
    void insert(NotificationLog log);

    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    LiveData<List<NotificationLog>> getAllNotifications();

    @Query("DELETE FROM notification_logs")
    void clearAll();
}