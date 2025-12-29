package com.taskmateaditya.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskDao;
import com.taskmateaditya.data.TaskDatabase;
import com.taskmateaditya.ui.DetailTaskActivity;
import com.taskmateaditya.ui.HomeActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Gunakan layout widget_dashboard yang sesuai XML Anda
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_dashboard);

        // 1. Setup Tombol Tambah (+)
        Intent addIntent = new Intent(context, DetailTaskActivity.class);
        PendingIntent addPending = PendingIntent.getActivity(context, 0, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // PENTING: ID harus sama dengan XML (btn_widget_add)
        views.setOnClickPendingIntent(R.id.btn_widget_add, addPending);

        // 2. Setup Klik Background (Buka Home) untuk refresh manual user
        Intent homeIntent = new Intent(context, HomeActivity.class);
        PendingIntent homePending = PendingIntent.getActivity(context, 1, homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // ID ini (tv_widget_greeting) diklik untuk buka aplikasi
        views.setOnClickPendingIntent(R.id.tv_widget_greeting, homePending);

        // 3. Set Sapaan Waktu
        setGreeting(views);

        // 4. Load Data Statistik
        loadStatistics(context, views, appWidgetManager, appWidgetId);
    }

    private static void setGreeting(RemoteViews views) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        String greeting;
        if (hour >= 4 && hour < 11) greeting = "Selamat Pagi,";
        else if (hour >= 11 && hour < 15) greeting = "Selamat Siang,";
        else if (hour >= 15 && hour < 18) greeting = "Selamat Sore,";
        else greeting = "Selamat Malam,";

        views.setTextViewText(R.id.tv_widget_greeting, greeting);
    }

    private static void loadStatistics(Context context, RemoteViews views, AppWidgetManager appWidgetManager, int appWidgetId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            views.setTextViewText(R.id.tv_widget_username, "Tamu");
            views.setTextViewText(R.id.tv_widget_stats, "Silakan Login");
            views.setProgressBar(R.id.widget_progress_bar, 100, 0, false);
            appWidgetManager.updateAppWidget(appWidgetId, views);
            return;
        }

        // Set Nama User
        String name = user.getDisplayName();
        views.setTextViewText(R.id.tv_widget_username, (name != null && !name.isEmpty()) ? name : "User");

        // Query Database di Background
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            TaskDao dao = TaskDatabase.getDatabase(context).taskDao();
            int total = dao.getTotalTasksCount(user.getUid());
            int completed = dao.getCompletedTasksCount(user.getUid());
            int remaining = total - completed;

            // Hitung Persentase
            int progress = (total > 0) ? (completed * 100 / total) : 0;

            // Update UI di Main Thread (via RemoteViews tidak perlu runOnUiThread)
            String statText = completed + " Selesai, " + remaining + " Tersisa";
            views.setTextViewText(R.id.tv_widget_stats, statText);
            views.setProgressBar(R.id.widget_progress_bar, 100, progress, false);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
    }

    // --- BAGIAN INI YANG MEMPERBAIKI MASALAH UPDATE TIDAK JALAN ---
    public static void sendRefreshBroadcast(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, DashboardWidgetProvider.class);

        // Ambil ID semua widget yang terpasang
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

        // Paksa update satu per satu
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }
}