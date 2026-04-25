package com.taskmateaditya.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.taskmateaditya.R;
import com.taskmateaditya.data.Subtask;

import java.util.List;

public class SubtaskAdapter extends RecyclerView.Adapter<SubtaskAdapter.ViewHolder> {

    private List<Subtask> subtaskList;
    private OnSubtaskChangeListener listener;

    public interface OnSubtaskChangeListener {
        void onSubtaskListChanged(List<Subtask> list);
    }

    public SubtaskAdapter(List<Subtask> subtaskList, OnSubtaskChangeListener listener) {
        this.subtaskList = subtaskList;
        this.listener = listener;
    }

    public void setSubtasks(List<Subtask> subtaskList) {
        this.subtaskList = subtaskList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subtask, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Subtask subtask = subtaskList.get(position);

        holder.cbSubtask.setOnCheckedChangeListener(null);
        holder.cbSubtask.setChecked(subtask.isCompleted());
        holder.etTitle.setText(subtask.getTitle());

        // Handling checkbox toggle
        holder.cbSubtask.setOnCheckedChangeListener((buttonView, isChecked) -> {
            subtask.setCompleted(isChecked);
            if (listener != null) {
                listener.onSubtaskListChanged(subtaskList);
            }
        });

        // Handling text changes
        if (holder.textWatcher != null) {
            holder.etTitle.removeTextChangedListener(holder.textWatcher);
        }
        
        holder.textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String newTitle = s.toString();
                if (!subtask.getTitle().equals(newTitle)) {
                    subtask.setTitle(newTitle);
                    if (listener != null) {
                        listener.onSubtaskListChanged(subtaskList);
                    }
                }
            }
        };
        holder.etTitle.addTextChangedListener(holder.textWatcher);

        // Handling delete
        holder.btnDelete.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                subtaskList.remove(currentPos);
                notifyItemRemoved(currentPos);
                // Also update subsequent indices
                notifyItemRangeChanged(currentPos, subtaskList.size());
                if (listener != null) {
                    listener.onSubtaskListChanged(subtaskList);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return subtaskList != null ? subtaskList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSubtask;
        EditText etTitle;
        ImageButton btnDelete;
        TextWatcher textWatcher;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSubtask = itemView.findViewById(R.id.cbSubtask);
            etTitle = itemView.findViewById(R.id.etSubtaskTitle);
            btnDelete = itemView.findViewById(R.id.btnDeleteSubtask);
        }
    }
}
