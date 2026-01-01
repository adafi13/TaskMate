package com.taskmateaditya.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notification_logs")
public class NotificationLog {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String message;
    public long timestamp;
    public int type;

    public String taskId;


    public NotificationLog(String title, String message, long timestamp, int type, String taskId) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.type = type;
        this.taskId = taskId;
    }
}