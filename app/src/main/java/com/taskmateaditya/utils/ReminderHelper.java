package com.taskmateaditya.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.taskmateaditya.data.Task;

import java.util.Calendar;

public class ReminderHelper {


    private static final long MINUTE_IN_MILLIS = 60 * 1000;
    private static final long HOUR_IN_MILLIS = 60 * MINUTE_IN_MILLIS;

    public static void setReminder(Context context, Task task) {

        if (!task.isReminderActive()) {
            return;
        }

        long deadlineTime = task.getReminderTime();

        // --- DAFTAR JADWAL NOTIFIKASI ---

        // 1. Notifikasi UTAMA (Pas Waktu Deadline) - ID Offset 0
        scheduleAlarm(context, task, deadlineTime, "Waktunya mengerjakan tugas! ⏰", 0);

        // 2. Notifikasi 30 MENIT Sebelum - ID Offset 1
        long time30Min = deadlineTime - (30 * MINUTE_IN_MILLIS);
        scheduleAlarm(context, task, time30Min, "Sisa waktu 30 menit lagi! Ayo selesaikan 🔥", 1);

        // 3. Notifikasi 2 JAM Sebelum - ID Offset 2
        long time2Hour = deadlineTime - (2 * HOUR_IN_MILLIS);
        scheduleAlarm(context, task, time2Hour, "Deadline tinggal 2 jam lagi. Semangat! ⏳", 2);

        // 4. Notifikasi 15 JAM Sebelum - ID Offset 3
        long time15Hour = deadlineTime - (15 * HOUR_IN_MILLIS);
        scheduleAlarm(context, task, time15Hour, "Ingat, tugas ini deadline-nya 15 jam lagi 📅", 3);
    }


    private static void scheduleAlarm(Context context, Task task, long triggerTime, String message, int idOffset) {

        if (triggerTime <= System.currentTimeMillis()) {
            return;
        }

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);


        intent.putExtra("TASK_ID", task.getId());
        intent.putExtra("TASK_TITLE", task.getTitle());
        intent.putExtra("TASK_MESSAGE", message);


        int uniqueId = task.getId().hashCode() + idOffset;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                uniqueId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                }
                Log.d("ReminderHelper", "Sukses set alarm: " + message + " untuk jam " + triggerTime);
            } catch (SecurityException e) {
                e.printStackTrace();
            }
        }
    }

    public static void cancelReminder(Context context, Task task) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);

        int[] offsets = {0, 1, 2, 3};

        for (int offset : offsets) {
            int uniqueId = task.getId().hashCode() + offset;

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    uniqueId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
        Log.d("ReminderHelper", "Semua jadwal alarm dibatalkan untuk: " + task.getTitle());
    }
}