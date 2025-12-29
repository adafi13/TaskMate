package com.taskmateaditya.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.io.Serializable;
import java.util.UUID;

@Entity(tableName = "task_table")
public class Task implements Serializable {

    // PERBAIKAN UTAMA: Menggunakan String UUID, bukan int AutoIncrement
    @PrimaryKey
    @NonNull
    private String id;

    // Data Tugas
    private String title;
    private String mataKuliah;
    private String deadline;
    private String notes;
    private String priority;
    private String category;
    private boolean isCompleted;

    // Alarm
    private long reminderTime;
    private boolean isReminderActive;

    // Lampiran
    private String attachmentPath;

    // Pemilik Tugas
    private String userId;

    // --- 1. WAJIB ADA: KONSTRUKTOR KOSONG (No-Argument Constructor) ---
    // Firebase MEMBUTUHKAN ini untuk mengubah data JSON kembali ke Object Java
    public Task() {
        // Generate ID unik otomatis saat objek kosong dibuat
        this.id = UUID.randomUUID().toString();
    }
    // ------------------------------------------------------------------

    // 2. Konstruktor Utama
    public Task(String title, String mataKuliah, String deadline, String notes, String priority, String category, boolean isCompleted, long reminderTime, boolean isReminderActive, String attachmentPath) {
        // Generate ID unik otomatis saat tugas baru dibuat
        this.id = UUID.randomUUID().toString();

        this.title = title;
        this.mataKuliah = mataKuliah;
        this.deadline = deadline;
        this.notes = notes;
        this.priority = priority;
        this.category = category;
        this.isCompleted = isCompleted;
        this.reminderTime = reminderTime;
        this.isReminderActive = isReminderActive;
        this.attachmentPath = attachmentPath;
    }

    // --- Getter & Setter ---

    // Ubah return type menjadi String
    @NonNull
    public String getId() { return id; }

    // Ubah parameter menjadi String
    public void setId(@NonNull String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMataKuliah() { return mataKuliah; }
    public void setMataKuliah(String mataKuliah) { this.mataKuliah = mataKuliah; }

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }

    public long getReminderTime() { return reminderTime; }
    public void setReminderTime(long reminderTime) { this.reminderTime = reminderTime; }

    public boolean isReminderActive() { return isReminderActive; }
    public void setReminderActive(boolean reminderActive) { isReminderActive = reminderActive; }

    public String getAttachmentPath() { return attachmentPath; }
    public void setAttachmentPath(String attachmentPath) { this.attachmentPath = attachmentPath; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}