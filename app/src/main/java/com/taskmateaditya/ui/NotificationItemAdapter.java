package com.taskmateaditya.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import java.util.ArrayList;
import java.util.List;

public class NotificationItemAdapter extends RecyclerView.Adapter<NotificationItemAdapter.ViewHolder> {
    private List<Task> tasks = new ArrayList<>();

    public void setTasks(List<Task> tasks) {
        this.tasks = tasks;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Kita gunakan layout dropdown_item yang sudah Anda punya agar lebih simple
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.dropdown_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Task task = tasks.get(position);
        holder.tvContent.setText(task.getTitle());
        holder.tvSubContent.setText("Deadline: " + task.getDeadline());
    }

    @Override
    public int getItemCount() { return tasks.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvSubContent;
        ViewHolder(View itemView) {
            super(itemView);
            // Sesuaikan ID dengan yang ada di dropdown_item.xml atau buat layout baru
            tvContent = itemView.findViewById(android.R.id.text1);
        }
    }
}