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
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskDatabase;
import com.taskmateaditya.data.TaskDao;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DailyReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "daily_reminder_channel";
    private static final String CHANNEL_NAME = "Daily Reminder";
    private static final int NOTIFICATION_ID = 200;

    // Groq AI Config
    private static final String GROQ_API_KEY = com.taskmateaditya.BuildConfig.GROQ_API_KEY;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                processDailySummary(context);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private void processDailySummary(Context context) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null)
            return;

        String userId = user.getUid();
        String name = "Sobat";
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            name = user.getDisplayName().split(" ")[0];
        }

        // Get Today's tasks from Database
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        TaskDao taskDao = TaskDatabase.getDatabase(context).taskDao();
        List<Task> todayTasks = taskDao.getTasksForDateSync(userId, todayDate);

        if (todayTasks == null || todayTasks.isEmpty()) {
            showNotification(context, "🔔 TaskMate Daily",
                    "Halo " + name + ", tidak ada tugas untuk hari ini. Waktunya bersantai! ☕");
            return;
        }

        // Prepare prompt for AI
        StringBuilder taskList = new StringBuilder();
        for (int i = 0; i < todayTasks.size(); i++) {
            taskList.append(i + 1).append(". ").append(todayTasks.get(i).getTitle()).append("\n");
        }

        String prompt = "You are a friendly personal assistant. "
                + "Summarize these tasks for today in one short, motivating, and natural paragraph. "
                + "Use Indonesian language. Use friendly emojis. Keep it under 200 characters.\n\n"
                + "Tasks:\n" + taskList.toString();

        // Call Groq API
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                    "You are a friendly productivity assistant."));
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            jsonBody.put("messages", messages);
        } catch (Exception e) {
            fallbackNotification(context, name);
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                String summary = jsonResponse.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message").getString("content").trim();
                showNotification(context, "🔔 TaskMate Daily", summary);
            } else {
                fallbackNotification(context, name);
            }
        } catch (Exception e) {
            Log.e("DailyReminder", "API error", e);
            fallbackNotification(context, name);
        }
    }

    private void fallbackNotification(Context context, String name) {
        showNotification(context, "🔔 TaskMate Daily",
                "Halo " + name + ", ada beberapa tugas yang menantimu hari ini. Semangat! 💪");
    }

    private void showNotification(Context context, String title, String message) {
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null)
            return;

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Ringkasan tugas harian oleh AI");
            channel.enableVibration(true);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel.setSound(alarmSound, audioAttributes);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tm_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(ContextCompat.getColor(context, R.color.tm_green))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(alarmSound)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}