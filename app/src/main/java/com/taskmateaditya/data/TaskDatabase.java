package com.taskmateaditya.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// 🔥 UPDATE: Naikkan versi ke 9 (tambah subtasksJson)
@Database(entities = {Task.class, NotificationLog.class}, version = 9, exportSchema = false)
public abstract class TaskDatabase extends RoomDatabase {

    public abstract TaskDao taskDao();

    // 🔥 TAMBAHAN: Akses ke DAO Notifikasi
    public abstract NotificationDao notificationDao();

    private static volatile TaskDatabase INSTANCE;

    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE task_table ADD COLUMN subtasksJson TEXT DEFAULT '[]'");
        }
    };

    public static TaskDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (TaskDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    TaskDatabase.class, "task_database")
                            .addMigrations(MIGRATION_8_9) // Migrasi aman
                            .fallbackToDestructiveMigration() // Penting: Hapus data lama saat versi naik selain 8 ke 9
                            .setJournalMode(JournalMode.TRUNCATE) // Stabilisasi database
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}