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
        Intent intent = new Intent(context, TaskWidgetService.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.setData(Uri.parse(intent.toUri(Intent.URI_INTENT_SCHEME)));

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_task_list);
        views.setRemoteAdapter(R.id.widget_list_view, intent);
        views.setEmptyView(R.id.widget_list_view, R.id.appwidget_empty_view);

        // Template Intent untuk menangani klik item
        Intent clickIntent = new Intent(context, DetailTaskActivity.class);
        PendingIntent clickPendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        views.setPendingIntentTemplate(R.id.widget_list_view, clickPendingIntent);

        // Klik Header untuk buka Home
        Intent homeIntent = new Intent(context, HomeActivity.class);
        PendingIntent homePendingIntent = PendingIntent.getActivity(
                context, 0, homeIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.appwidget_empty_view, homePendingIntent); // Jika kosong, klik text buka home

        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    // Method Helper untuk refresh widget dari Activity lain
    public static void sendRefreshBroadcast(Context context) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.setComponent(new ComponentName(context, TaskWidgetProvider.class));
        context.sendBroadcast(intent);

        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, TaskWidgetProvider.class));
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view);
    }
}