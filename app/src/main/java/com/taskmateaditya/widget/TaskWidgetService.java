package com.taskmateaditya.widget;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskDatabase;

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
                // Pastikan database tidak null dan ambil data
                TaskDatabase db = TaskDatabase.getDatabase(context);
                if (db != null) {
                    taskList = db.taskDao().getActiveTasksForWidget();
                }
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
            // Pastikan layout yang dipanggil benar: item_widget_task
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.item_widget_task);

            // [PERBAIKAN 1] Sesuaikan ID dengan XML: widgetTaskTitle
            views.setTextViewText(R.id.widgetTaskTitle, task.getTitle());

            // [PERBAIKAN 2] Sesuaikan ID dengan XML: widgetTaskDate
            views.setTextViewText(R.id.widgetTaskDate, task.getDeadline());

            // Logika Warna Prioritas
            int color = Color.parseColor("#00C853"); // Default Hijau
            if (task.getPriority() != null) {
                if ("Tinggi".equalsIgnoreCase(task.getPriority())) {
                    color = Color.parseColor("#FF5252"); // Merah
                } else if ("Sedang".equalsIgnoreCase(task.getPriority())) {
                    color = Color.parseColor("#FFC107"); // Kuning
                }
            }

            // [PERBAIKAN 3] Ganti logika background color menjadi text color
            // Karena di XML kamu menggunakan TextView (widgetTaskPriority), bukan View kotak.
            views.setTextColor(R.id.widgetTaskPriority, color);
            views.setTextViewText(R.id.widgetTaskPriority, "• " + (task.getPriority() != null ? task.getPriority() : "Normal"));

            // Intent Klik per Item (Membuka Detail)
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra("EXTRA_TASK_ID", task.getId());

            // [PERBAIKAN 4] Pasang klik listener di Container agar area klik lebih luas
            // widgetItemContainer adalah ID LinearLayout terluar di XML kamu
            views.setOnClickFillInIntent(R.id.widgetItemContainer, fillInIntent);

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