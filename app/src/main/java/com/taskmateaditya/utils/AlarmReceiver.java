package com.taskmateaditya.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.taskmateaditya.R;
import com.taskmateaditya.data.NotificationLog;
import com.taskmateaditya.data.TaskDatabase;
import com.taskmateaditya.ui.DetailTaskActivity;

public class AlarmReceiver extends BroadcastReceiver {

    // ID Channel
    private static final String CHANNEL_ID = "task_channel_v2";
    private static final String CHANNEL_NAME = "Task Reminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Ambil data dari Intent (Dikirim oleh ReminderHelper)
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        String taskId = intent.getStringExtra("TASK_ID");
        String taskMessage = intent.getStringExtra("TASK_MESSAGE"); // Ambil pesan deskripsi

        if (taskTitle != null) {
            // A. Tampilkan Notifikasi Suara/Pop-up
            showAlarmNotification(context, taskTitle, taskId);

            // B. 🔥 TAMBAHAN BARU: Simpan ke Database Riwayat 🔥
            saveNotificationToHistory(context, taskTitle, taskMessage);
        }
    }

    // --- LOGIKA MENYIMPAN KE DATABASE ---
    private void saveNotificationToHistory(Context context, String title, String message) {
        // Gunakan Executor bawaan database untuk operasi background (agar tidak memblokir UI)
        TaskDatabase.getDatabase(context).databaseWriteExecutor.execute(() -> {

            String finalMsg = (message != null) ? message : "Waktunya mengerjakan tugas!";

            // Buat object Log Baru
            NotificationLog log = new NotificationLog(
                    title,              // Judul Log (Nama Tugas)
                    finalMsg,           // Pesan Log
                    System.currentTimeMillis(), // Waktu sekarang
                    1                   // Tipe 1 = Urgent/Alarm
            );

            // Insert ke tabel notification_logs
            TaskDatabase.getDatabase(context).notificationDao().insert(log);
        });
    }
    // ------------------------------------

    private void showAlarmNotification(Context context, String title, String taskId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // Setup Intent saat notifikasi diklik (Buka Detail Tugas)
        Intent intent = new Intent(context, DetailTaskActivity.class);
        if (taskId != null) {
            intent.putExtra("EXTRA_TASK_ID", taskId);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                taskId != null ? taskId.hashCode() : 0, // Request code unik
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Tentukan Suara Alarm
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) {
            alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }

        // Setup Notification Channel (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Pengingat untuk tugas harian");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{1000, 1000, 1000, 1000, 1000});
            channel.setSound(alarmSound, audioAttributes);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Build Notifikasi
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_baseline_notifications_24)
                .setContentTitle("Waktunya Mengerjakan Tugas! ⏰")
                .setContentText(title) // Judul Tugas
                .setColor(ContextCompat.getColor(context, R.color.tm_green))
                .setVibrate(new long[]{1000, 1000, 1000, 1000, 1000})
                .setLights(ContextCompat.getColor(context, R.color.tm_green), 3000, 3000)
                .setSound(alarmSound)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        // Tampilkan dengan ID unik (berdasarkan waktu)
        int notificationId = (int) System.currentTimeMillis();
        if (notificationManager != null) {
            notificationManager.notify(notificationId, builder.build());
        }
    }
}