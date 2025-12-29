package com.taskmateaditya.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.DialogNotificationsBinding;

import java.util.ArrayList;
import java.util.List;

public class NotificationDialog extends BottomSheetDialogFragment {

    private DialogNotificationsBinding binding;
    private TaskAdapter notificationAdapter;
    private TaskViewModel viewModel; // Jadikan field class agar bisa diakses di mana saja

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = DialogNotificationsBinding.inflate(inflater, container, false);

        // Setup RecyclerView
        binding.rvNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        notificationAdapter = new TaskAdapter();
        binding.rvNotifications.setAdapter(notificationAdapter);

        // Inisialisasi ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        // --- PERBAIKAN DI SINI ---
        notificationAdapter.setOnItemClickListener(new TaskAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Task task) {
                // Buka detail tugas
                Intent intent = new Intent(getContext(), DetailTaskActivity.class);
                intent.putExtra("EXTRA_TASK_ID", task.getId());
                startActivity(intent);
                dismiss();
            }

            @Override
            public void onCheckChange(Task task, boolean isCompleted) {
                // Update status selesai
                task.setCompleted(isCompleted);
                viewModel.update(task);
            }

            // METODE INI SEBELUMNYA HILANG DAN MENYEBABKAN ERROR
            @Override
            public void onDeleteClick(Task task) {
                // Hapus tugas jika tombol sampah di dialog diklik
                viewModel.delete(task);
            }
        });
        // -------------------------

        // Observe Data
        viewModel.getAllTasks().observe(getViewLifecycleOwner(), allTasks -> {
            if (allTasks != null && !allTasks.isEmpty()) {
                // Filter: Hanya tampilkan yang belum selesai
                List<Task> incompleteTasks = new ArrayList<>();
                for (Task t : allTasks) {
                    if (!t.isCompleted()) {
                        incompleteTasks.add(t);
                    }
                }

                if (incompleteTasks.isEmpty()) {
                    showEmptyState(true);
                } else {
                    notificationAdapter.setTasks(incompleteTasks);
                    showEmptyState(false);
                }
            } else {
                showEmptyState(true);
            }
        });

        binding.btnCloseNotification.setOnClickListener(v -> dismiss());

        return binding.getRoot();
    }

    private void showEmptyState(boolean isEmpty) {
        if (binding == null) return;
        if (isEmpty) {
            binding.tvEmptyNotifications.setVisibility(View.VISIBLE);
            binding.rvNotifications.setVisibility(View.GONE);
        } else {
            binding.tvEmptyNotifications.setVisibility(View.GONE);
            binding.rvNotifications.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}