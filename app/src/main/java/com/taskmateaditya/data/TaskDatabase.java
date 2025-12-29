package com.taskmateaditya.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 🔥 UPDATE: Tambahkan NotificationLog.class dan naikkan versi ke 6
@Database(entities = {Task.class, NotificationLog.class}, version = 6, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {

    public abstract TaskDao taskDao();

    // 🔥 TAMBAHAN: Akses ke DAO Notifikasi
    public abstract NotificationDao notificationDao();

    private static volatile TaskDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static TaskDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (TaskDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    TaskDatabase.class, "task_database")
                            .fallbackToDestructiveMigration() // Penting: Hapus data lama saat versi naik
                            .setJournalMode(JournalMode.TRUNCATE) // Stabilisasi database
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}