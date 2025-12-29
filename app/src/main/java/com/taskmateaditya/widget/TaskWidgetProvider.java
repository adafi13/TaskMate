package com.taskmateaditya.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import com.taskmateaditya.R;
import com.taskmateaditya.ui.DetailTaskActivity;
import com.taskmateaditya.ui.HomeActivity;

public class TaskWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // 1. Setup Service Intent (Menghubungkan ke Adapter)
        Intent intent = new Intent(context, TaskWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

        // 2. Load Layout
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task_list);

        // 3. Bind ListView & Empty View
        // Pastikan ID ini sama persis dengan yang ada di widget_task_list.xml
        views.setRemoteAdapter(R.id.widgetListView, intent);
        views.setEmptyView(R.id.widgetListView, R.id.empty_view);

        // 4. Intent untuk Klik Item (Membuka Detail Tugas)
        Intent clickIntent = new Intent(context, DetailTaskActivity.class);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widgetListView, clickPendingIntent);

        // 5. Intent untuk Tombol Tambah (+) (Membuka Halaman Tambah Tugas)
        Intent addIntent = new Intent(context, DetailTaskActivity.class);
        PendingIntent addPendingIntent = PendingIntent.getActivity(
                context, 0, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.btnWidgetAdd, addPendingIntent);

        // 6. Intent untuk Klik Header (Membuka Home)
        Intent homeIntent = new Intent(context, HomeActivity.class);
        PendingIntent homePendingIntent = PendingIntent.getActivity(
                context, 0, homeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Kita pasang pending intent ini pada judul "TaskMate" jika ingin bisa diklik
        // views.setOnClickPendingIntent(R.id.text_view_title_id, homePendingIntent);

        // Update Widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Method Helper untuk Refresh Widget Real-time
    public static void sendRefreshBroadcast(Context context) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.setComponent(new ComponentName(context, TaskWidgetProvider.class));
        context.sendBroadcast(intent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TaskWidgetProvider.class));

        // Refresh data ListView
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widgetListView);
    }
}