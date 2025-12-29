package com.taskmateaditya.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskDatabase;
import com.taskmateaditya.ui.DetailTaskActivity;

import java.util.ArrayList;
import java.util.List;

public class TaskWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TaskWidgetItemFactory(this.getApplicationContext());
    }

    class TaskWidgetItemFactory implements RemoteViewsFactory {
        private Context context;
        private List<Task> taskList = new ArrayList<>();

        public TaskWidgetItemFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {}

        @Override
        public void onDataSetChanged() {
            // Ini dijalankan di background thread, aman akses Database
            if (context != null) {
                taskList = TaskDatabase.getDatabase(context).taskDao().getActiveTasksForWidget();
            }
        }

        @Override
        public void onDestroy() {}

        @Override
        public int getCount() {
            return taskList.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position >= taskList.size()) return null;

            Task task = taskList.get(position);
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_task);

            views.setTextViewText(R.id.widget_item_title, task.getTitle());
            views.setTextViewText(R.id.widget_item_date, task.getDeadline());

            // Warna Prioritas
            int color = Color.parseColor("#00C853"); // Default Hijau
            if ("Tinggi".equalsIgnoreCase(task.getPriority())) color = Color.parseColor("#FF5252");
            else if ("Sedang".equalsIgnoreCase(task.getPriority())) color = Color.parseColor("#FFC107");

            views.setInt(R.id.widget_priority_indicator, "setBackgroundColor", color);

            // Intent Klik per Item (Membuka Detail)
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra("EXTRA_TASK_ID", task.getId());
            views.setOnClickFillInIntent(R.id.widget_item_title, fillInIntent); // Klik pada teks judul
            // Atau set pada root layout item jika memungkinkan di layout xml widget

            return views;
        }

        @Override
        public RemoteViews getLoadingView() { return null; }

        @Override
        public int getViewTypeCount() { return 1; }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public boolean hasStableIds() { return true; }
    }
}