package com.taskmateaditya.ui;

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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;

public class DailyReminderReceiver extends BroadcastReceiver {

    // Gunakan ID channel yang berbeda dengan pengingat tugas agar user bisa atur beda
    private static final String CHANNEL_ID = "daily_reminder_channel";
    private static final String CHANNEL_NAME = "Daily Reminder";
    private static final int NOTIFICATION_ID = 200; // ID unik konstan untuk daily

    @Override
    public void onReceive(Context context, Intent intent) {
        // 1. Ambil Nama User dari Firebase agar sapaan lebih personal
        String userName = "Sobat"; // Default jika belum login/tidak ada nama

        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                // Ambil kata pertama saja (Nama Depan)
                String fullName = user.getDisplayName();
                String[] parts = fullName.split(" ");
                if (parts.length > 0) {
                    userName = parts[0];
                } else {
                    userName = fullName;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Tampilkan Notifikasi
        showDailyNotification(context, userName);
    }

    private void showDailyNotification(Context context, String name) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 3. Konfigurasi Suara
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        // 4. Setup Channel (WAJIB dilakukan SEBELUM notify untuk Android 8.0 Oreo ke atas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("Pengingat harian rutin jam 07:00");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{1000, 1000, 1000}); // Getar: Panjang-Panjang-Panjang

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(alarmSound, audioAttributes);

            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }

        // 5. Setup Intent (Aksi saat notifikasi diklik)
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 6. Pesan Notifikasi (Sesuai Request)
        String title = "🔔 TaskMate Daily";
        String message = "Halo " + name + ", sudah cek tugas hari ini belum?";

        // 7. Build Notifikasi
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tm_logo) // Pastikan ikon ini ada (atau ganti ic_baseline_notifications_24)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // Agar teks panjang terbaca semua
                .setColor(ContextCompat.getColor(context, R.color.tm_green)) // Warna hijau khas aplikasi
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(alarmSound)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true); // Notifikasi hilang saat diklik

        // 8. Tampilkan
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        }
    }
}