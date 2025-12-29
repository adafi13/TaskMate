package com.taskmateaditya.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.ActivityHomeBinding;

public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        binding.recyclerViewTasks.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewTasks.setHasFixedSize(true);
        taskAdapter = new TaskAdapter();
        binding.recyclerViewTasks.setAdapter(taskAdapter);

        setupSwipeActions();

        taskViewModel.getSearchResults().observe(this, tasks -> {
            taskAdapter.setTasks(tasks);
            showEmptyState(tasks.isEmpty());
        });

        taskViewModel.getIsLoading().observe(this, isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        taskViewModel.syncData();
        taskViewModel.setSearchQuery("");
        setupListeners();
        checkNotificationPermission();
    }

    private void setupSwipeActions() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Task task = taskAdapter.getTaskAt(position);

                if (direction == ItemTouchHelper.RIGHT) {
                    toggleTaskCompletion(task);
                } else if (direction == ItemTouchHelper.LEFT) {
                    showDeleteConfirmationDialog(task);
                    taskAdapter.notifyItemChanged(position);
                }
            }
        };

        new ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewTasks);
    }

    private void toggleTaskCompletion(Task task) {
        boolean newStatus = !task.isCompleted();
        task.setCompleted(newStatus);
        taskViewModel.update(task);

        if (newStatus) {
            binding.getRoot().setHapticFeedbackEnabled(true);
            binding.getRoot().performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            );
        }

        String pesan = newStatus ? "Selamat! 🎉 Tugas selesai dengan sempurna!" : "Tugas dibuka kembali! Ayo selesaikan dengan semangat! 💪";
        Snackbar.make(binding.getRoot(), pesan, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.btnAddTaskFab)
                .setAction("Batal", v -> {
                    task.setCompleted(!newStatus);
                    taskViewModel.update(task);
                })
                .show();
    }

    private void setupListeners() {
        binding.btnAddTaskEmpty.setOnClickListener(v -> openDetailActivity(null));
        binding.btnAddTaskFab.setOnClickListener(v -> openDetailActivity(null));
        binding.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // --- FITUR AI ---
        if (binding.fabAiChat != null) {
            binding.fabAiChat.setOnClickListener(v -> {
                Intent intent = new Intent(HomeActivity.this, AiChatActivity.class);
                startActivity(intent);
            });
        }

        // --- UPDATE: FITUR RIWAYAT NOTIFIKASI ---
        // Mengganti Dialog lama dengan Activity Riwayat baru
        binding.btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationHistoryActivity.class);
            startActivity(intent);
        });
        // ----------------------------------------

        binding.btnSort.setOnClickListener(v -> showSortMenu());

        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                taskViewModel.setCategoryFilter("Semua");
                return;
            }
            int id = checkedIds.get(0);
            String selectedCategory = "Semua";
            if (id == R.id.chipKuliah) selectedCategory = "Sekolah/Kuliah";
            else if (id == R.id.chipPribadi) selectedCategory = "Pribadi";
            else if (id == R.id.chipKerja) selectedCategory = "Pekerjaan";
            taskViewModel.setCategoryFilter(selectedCategory);
        });

        binding.editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { taskViewModel.setSearchQuery(s.toString()); }
        });

        taskAdapter.setOnItemClickListener(new TaskAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Task task) {
                openDetailActivity(task.getId());
            }

            @Override
            public void onCheckChange(Task task, boolean isCompleted) {
                task.setCompleted(isCompleted);
                taskViewModel.update(task);

                if (isCompleted) {
                    binding.getRoot().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }

                Snackbar.make(binding.getRoot(),
                                isCompleted ? "Selamat! 🎉 Tugas selesai dengan sukses!" : "Tugas dibuka kembali. Ayo selesaikan dengan semangat! 💪",
                                Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.btnAddTaskFab)
                        .show();
            }

            @Override
            public void onDeleteClick(Task task) {
                showDeleteConfirmationDialog(task);
            }
        });
    }

    private void showDeleteConfirmationDialog(Task task) {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Tugas? 🗑️")
                .setMessage("Tugas '" + task.getTitle() + "' akan hilang secara permanen. Yakin ingin menghapusnya?")
                .setPositiveButton("Hapus Tugas", (dialog, which) -> {
                    taskViewModel.delete(task);
                    Snackbar.make(binding.getRoot(), "Tugas berhasil dihapus.", Snackbar.LENGTH_LONG)
                            .setAnchorView(binding.btnAddTaskFab)
                            .setAction("Batalkan", v -> taskViewModel.insert(task)).show();
                })
                .setNegativeButton("Batal", (dialog, which) -> taskAdapter.notifyDataSetChanged())
                .setCancelable(false)
                .show();
    }

    private void openDetailActivity(String taskId) {
        Intent intent = new Intent(this, DetailTaskActivity.class);
        if (taskId != null) intent.putExtra("EXTRA_TASK_ID", taskId);
        startActivity(intent);
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(this, binding.btnSort);
        popup.getMenu().add(0, 1, 0, "Terbaru");
        popup.getMenu().add(0, 2, 0, "Batas Waktu");
        popup.getMenu().add(0, 3, 0, "Prioritas");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: taskViewModel.setSortOrder(TaskViewModel.SortType.NEWEST); return true;
                case 2: taskViewModel.setSortOrder(TaskViewModel.SortType.DEADLINE); return true;
                case 3: taskViewModel.setSortOrder(TaskViewModel.SortType.PRIORITY); return true;
            }
            return false;
        });
        popup.show();
    }

    private void showEmptyState(boolean isEmpty) {
        if (isEmpty) {
            binding.emptyStateLayout.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(500);
            binding.emptyStateLayout.startAnimation(fadeIn);
            binding.recyclerViewTasks.setVisibility(View.GONE);
            binding.btnAddTaskFab.setVisibility(View.GONE);
        } else {
            binding.emptyStateLayout.setVisibility(View.GONE);
            binding.recyclerViewTasks.setVisibility(View.VISIBLE);
            binding.btnAddTaskFab.setVisibility(View.VISIBLE);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}