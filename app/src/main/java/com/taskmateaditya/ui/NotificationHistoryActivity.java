package com.taskmateaditya.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.taskmateaditya.R;
import com.taskmateaditya.data.NotificationLog;
import com.taskmateaditya.data.TaskDatabase;

import java.util.List;

public class NotificationHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private NotificationHistoryAdapter adapter;
    private View emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        // 1. Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar); // Mengaktifkan Toolbar sebagai ActionBar

        // Tombol Back (Panah Kiri)
        toolbar.setNavigationOnClickListener(v -> finish());

        // 2. Setup RecyclerView
        recyclerView = findViewById(R.id.rvNotifications);
        emptyState = findViewById(R.id.emptyStateLayout);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationHistoryAdapter();
        recyclerView.setAdapter(adapter);

        // 3. Load Data Live dari Database
        LiveData<List<NotificationLog>> liveData = TaskDatabase.getDatabase(this).notificationDao().getAllNotifications();
        liveData.observe(this, logs -> {
            adapter.setLogs(logs);

            // Tampilkan Empty State jika kosong
            if (logs.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        });
    }

    // 🔥 WAJIB DITAMBAHKAN: Agar Menu "Hapus Semua" Muncul 🔥
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notification_history, menu);
        return true;
    }

    // 🔥 WAJIB DITAMBAHKAN: Menangani Klik Menu 🔥
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_all) {
            showClearConfirmation();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showClearConfirmation() {
        // Cek dulu apakah ada data, kalau kosong jangan tampilkan dialog
        if (adapter.getItemCount() == 0) {
            Toast.makeText(this, "Riwayat sudah kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Hapus Semua?")
                .setMessage("Apakah Anda yakin ingin menghapus seluruh riwayat notifikasi? Tindakan ini tidak dapat dibatalkan.")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    // Hapus data di Background Thread
                    TaskDatabase.getDatabase(this).databaseWriteExecutor.execute(() -> {
                        TaskDatabase.getDatabase(this).notificationDao().clearAll();
                    });

                    Snackbar.make(findViewById(android.R.id.content), "Riwayat dihapus", Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}