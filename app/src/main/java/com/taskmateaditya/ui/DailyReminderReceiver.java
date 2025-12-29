package com.taskmateaditya.ui;

import android.app.Notification;
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
import com.taskmateaditya.R;

public class DailyReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String taskTitle = intent.getStringExtra("TASK_TITLE");
        int taskId = intent.getIntExtra("TASK_ID", 0);

        if (taskTitle == null) taskTitle = "Ada tugas menunggu!";

        showNotification(context, taskTitle, taskId);
    }

    private void showNotification(Context context, String title, int notifId) {
            String channelId = "task_reminder_channel_high";
            String channelName = "Task Reminders High Priority";

            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

            // Konfigurasi Notifikasi
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_baseline_notifications_24)
                    // Judul Notifikasi (Berubah sesuai Judul Tugas)
                    .setContentTitle("Waktunya mengerjakan: " + title)
                    // Isi Notifikasi (Bisa Anda ganti sesuka hati)
                    .setContentText("Halo! Tugas ini sudah memasuki jadwalnya. Ayo segera diselesaikan!")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Pesan Pengingat: Tugas '" + title + "' harus segera diperiksa. Semangat produktif hari ini!"))
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(alarmSound)
                    .setDefaults(Notification.DEFAULT_ALL);

            notificationManager.notify(notifId, builder.build());

        // Tambahkan metode ini di dalam onReceive atau sebelum notificationManager.notify
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Channel untuk pengingat tugas prioritas tinggi");
            channel.enableVibration(true);
            notificationManager.createNotificationChannel(channel);
        }
        }
        }