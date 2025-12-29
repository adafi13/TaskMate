package com.taskmateaditya.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.taskmateaditya.data.Task;

public class ReminderHelper {

    public static void setReminder(Context context, Task task) {
        // 1. Validasi: Jangan set alarm jika fitur dimatikan atau waktu sudah lewat
        if (!task.isReminderActive() || task.getReminderTime() <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);

        // --- KIRIM DATA KE ALARM RECEIVER ---
        // Gunakan string "TASK_TITLE" dan "TASK_ID" agar cocok dengan AlarmReceiver.java
        intent.putExtra("TASK_ID", task.getId());
        intent.putExtra("TASK_TITLE", task.getTitle());
        intent.putExtra("TASK_MESSAGE", "Jangan lupa, tenggat waktu semakin dekat!");

        // Buat ID unik dari hashcode ID Tugas
        // Ini penting agar notifikasi tugas A tidak menimpa tugas B
        int uniqueId = task.getId().hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                // 2. Set Alarm Sesuai Versi Android
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12 ke atas butuh izin SCHEDULE_EXACT_ALARM
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.getReminderTime(), pendingIntent);
                    } else {
                        // Fallback jika izin tidak diberikan (jarang terjadi jika manifest benar)
                        alarmManager.set(AlarmManager.RTC_WAKEUP, task.getReminderTime(), pendingIntent);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Android 6.0 - 11
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.getReminderTime(), pendingIntent);
                } else {
                    // Android di bawah 6.0
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, task.getReminderTime(), pendingIntent);
                }

                Log.d("ReminderHelper", "Alarm berhasil diset untuk: " + task.getTitle());

            } catch (SecurityException e) {
                e.printStackTrace();
                Log.e("ReminderHelper", "Gagal set alarm: Izin tidak diberikan");
            }
        }
    }

    public static void cancelReminder(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);

        // Gunakan ID yang sama persis untuk membatalkan
        int uniqueId = task.getId().hashCode();

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d("ReminderHelper", "Alarm dibatalkan untuk: " + task.getTitle());
        }
    }
}