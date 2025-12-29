package com.taskmateaditya.ui;

import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<Task> tasks = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Task task);
        void onCheckChange(Task task, boolean isCompleted);
        void onDeleteClick(Task task);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /**
     * TAMBAHAN PENTING: Untuk mendukung Swipe to Delete di HomeActivity
     */
    public Task getTaskAt(int position) {
        if (position >= 0 && position < tasks.size()) {
            return tasks.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        Task currentTask = tasks.get(position);

        // 1. Set Judul
        holder.textViewTitle.setText(currentTask.getTitle());

        // 2. Format Tanggal (Deadline)
        try {
            SimpleDateFormat sdfInput = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            SimpleDateFormat sdfOutput = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
            Date date = sdfInput.parse(currentTask.getDeadline());
            if (date != null) {
                holder.textViewDeadline.setText(sdfOutput.format(date));
            }
        } catch (ParseException e) {
            holder.textViewDeadline.setText(currentTask.getDeadline());
        }

        // 3. Logika Warna Prioritas
        String priority = currentTask.getPriority();
        holder.textViewPriority.setText(priority);

        int priorityColor;
        if ("Tinggi".equalsIgnoreCase(priority)) {
            priorityColor = Color.parseColor("#FF5252"); // Merah
        } else if ("Sedang".equalsIgnoreCase(priority)) {
            priorityColor = Color.parseColor("#FFC107"); // Oranye/Kuning
        } else {
            priorityColor = Color.parseColor("#00C853"); // Hijau
        }

        holder.priorityIndicator.setBackgroundColor(priorityColor);
        holder.textViewPriority.setTextColor(priorityColor);

        // 4. Set Kategori
        holder.textViewCategory.setText(currentTask.getCategory() != null ? currentTask.getCategory() : "Umum");

        // 5. Visual State: Selesai vs Belum (Efek Coret & Pudar)
        holder.checkBoxCompleted.setOnCheckedChangeListener(null);
        holder.checkBoxCompleted.setChecked(currentTask.isCompleted());

        if (currentTask.isCompleted()) {
            holder.textViewTitle.setPaintFlags(holder.textViewTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.itemView.setAlpha(0.6f);
        } else {
            holder.textViewTitle.setPaintFlags(holder.textViewTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            holder.itemView.setAlpha(1.0f);
        }

        // 6. Pasang Listeners
        holder.checkBoxCompleted.setOnCheckedChangeListener((btn, isChecked) -> {
            if (listener != null) listener.onCheckChange(currentTask, isChecked);
        });

        holder.btnDeleteTask.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(currentTask);
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(currentTask);
        });
    }

    @Override
    public int getItemCount() {
        return tasks.size();
    }

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        // Menggunakan notifyDataSetChanged untuk rilis awal agar stabil
        notifyDataSetChanged();
    }

    static class TaskViewHolder extends RecyclerView.ViewHolder {
        final TextView textViewTitle, textViewDeadline, textViewPriority, textViewCategory;
        final CheckBox checkBoxCompleted;
        final View priorityIndicator;
        final ImageButton btnDeleteTask;

        public TaskViewHolder(View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewDeadline = itemView.findViewById(R.id.textViewDeadline);
            textViewPriority = itemView.findViewById(R.id.textViewPriority);
            textViewCategory = itemView.findViewById(R.id.textViewCategory);
            checkBoxCompleted = itemView.findViewById(R.id.checkBoxCompleted);
            priorityIndicator = itemView.findViewById(R.id.priorityIndicator);
            btnDeleteTask = itemView.findViewById(R.id.btnDeleteTask);
        }
    }
}