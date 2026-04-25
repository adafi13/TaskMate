package com.taskmateaditya.ui;

import android.content.Context;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.ActivityFocusTimerBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FocusTimerActivity extends AppCompatActivity {

    private ActivityFocusTimerBinding binding;
    private TaskViewModel taskViewModel;
    
    // Timer Settings (Pomodoro: 25 mins)
    private static final long START_TIME_IN_MILLIS = 25 * 60 * 1000; 
    
    private CountDownTimer countDownTimer;
    private boolean timerRunning;
    private long timeLeftInMillis = START_TIME_IN_MILLIS;
    private long initialTimeInMillis = START_TIME_IN_MILLIS; // Simpan durasi custom user

    private Task selectedTask = null;
    private List<Task> pendingTasks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFocusTimerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        setupListeners();
        loadPendingTasks();
        handleVoiceIntent();
        updateCountDownText();
    }

    private void handleVoiceIntent() {
        int voiceDuration = getIntent().getIntExtra("EXTRA_VOICE_DURATION", -1);
        if (voiceDuration > 0) {
            timeLeftInMillis = (long) voiceDuration * 60 * 1000;
            initialTimeInMillis = timeLeftInMillis;
            binding.progressTimer.setMax((int) (timeLeftInMillis / 1000));
        }
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> {
            if (timerRunning) {
                showExitConfirmationDialog();
            } else {
                finish();
            }
        });

        binding.btnStartPause.setOnClickListener(v -> {
            if (timerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });

        binding.btnReset.setOnClickListener(v -> resetTimer());

        binding.cardTaskSelector.setOnClickListener(v -> showTaskSelectionDialog());

        binding.tvTimer.setOnClickListener(v -> {
            if (!timerRunning) {
                showDurationPickerDialog();
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (timerRunning) {
                    showExitConfirmationDialog();
                } else {
                    finish();
                }
            }
        });
    }

    private void showDurationPickerDialog() {
        final android.widget.NumberPicker numberPicker = new android.widget.NumberPicker(this);
        numberPicker.setMinValue(1);
        numberPicker.setMaxValue(120);
        numberPicker.setValue((int) (timeLeftInMillis / 60000));

        new AlertDialog.Builder(this)
                .setTitle("Atur Waktu Fokus (Menit)")
                .setView(numberPicker)
                .setPositiveButton("Setel", (dialog, which) -> {
                    timeLeftInMillis = (long) numberPicker.getValue() * 60 * 1000;
                    initialTimeInMillis = timeLeftInMillis;
                    binding.progressTimer.setMax((int) (timeLeftInMillis / 1000));
                    updateCountDownText();
                    updateProgressBar();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showExitConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Keluar dari Mode Fokus?")
                .setMessage("Timer akan dihentikan dan fokus Anda mungkin terganggu. Yakin ingin keluar?")
                .setPositiveButton("Ya, Keluar", (dialog, which) -> {
                    stopLockTask();
                    finish();
                })
                .setNegativeButton("Batal", null)
                .show();
    }


    private void loadPendingTasks() {
        taskViewModel.getAllTasks().observe(this, tasks -> {
            pendingTasks.clear();
            if (tasks != null) {
                for (Task t : tasks) {
                    if (!t.isCompleted()) {
                        pendingTasks.add(t);
                    }
                }
                
                // --- VOICE MATCHING ---
                String voiceTaskTitle = getIntent().getStringExtra("EXTRA_VOICE_TASK");
                if (selectedTask == null && voiceTaskTitle != null && !voiceTaskTitle.isEmpty()) {
                    for (Task t : pendingTasks) {
                        if (t.getTitle().toLowerCase().contains(voiceTaskTitle.toLowerCase())) {
                            selectedTask = t;
                            binding.tvSelectedTask.setText(selectedTask.getTitle());
                            // Auto-start if duration was also set
                            if (getIntent().hasExtra("EXTRA_VOICE_DURATION")) {
                                startTimer();
                            }
                            break;
                        }
                    }
                }
            }
        });
    }

    private void showTaskSelectionDialog() {
        if (pendingTasks.isEmpty()) {
            Toast.makeText(this, "Tidak ada tugas yang tertunda.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] taskTitles = new String[pendingTasks.size()];
        for (int i = 0; i < pendingTasks.size(); i++) {
            taskTitles[i] = pendingTasks.get(i).getTitle();
        }

        new AlertDialog.Builder(this)
                .setTitle("Pilih Tugas Fokus")
                .setItems(taskTitles, (dialog, which) -> {
                    selectedTask = pendingTasks.get(which);
                    binding.tvSelectedTask.setText(selectedTask.getTitle());
                })
                .show();
    }

    private void startTimer() {
        if (selectedTask == null) {
            Toast.makeText(this, "Silakan pilih tugas terlebih dahulu!", Toast.LENGTH_SHORT).show();
            return;
        }

        timerRunning = true;
        binding.btnStartPause.setText("JEDA");
        binding.btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF64748B)); // Slate
        
        // --- LOCK MODE (Screen Pinning) ---
        try {
            startLockTask();
            Toast.makeText(this, "Mode Fokus Terkunci Aktif 🔒", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("FocusTimer", "Lock task failed", e);
        }

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                updateCountDownText();
                updateProgressBar();
            }

            @Override
            public void onFinish() {
                timerRunning = false;
                onFocusFinished();
            }
        }.start();
    }

    private void pauseTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timerRunning = false;
        binding.btnStartPause.setText("LANJUTKAN");
        binding.btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444)); // Red
        
        try {
            stopLockTask();
        } catch (Exception e) {}
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timerRunning = false;
        timeLeftInMillis = initialTimeInMillis; // Kembalikan ke durasi custom user
        
        updateCountDownText();
        updateProgressBar();
        binding.btnStartPause.setText("MULAI FOKUS");
        binding.btnStartPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
        
        try {
            stopLockTask();
        } catch (Exception e) {}
    }

    private void updateCountDownText() {
        int minutes = (int) (timeLeftInMillis / 1000) / 60;
        int seconds = (int) (timeLeftInMillis / 1000) % 60;

        String timeLeftFormatted = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        binding.tvTimer.setText(timeLeftFormatted);
    }

    private void updateProgressBar() {
        int progress = (int) (timeLeftInMillis / 1000);
        binding.progressTimer.setProgress(progress);
    }

    private void onFocusFinished() {
        updateCountDownText();
        updateProgressBar();
        binding.btnStartPause.setText("SELESAI");
        binding.btnStartPause.setEnabled(false);

        try {
            stopLockTask();
        } catch (Exception e) {}

        // Vibrate
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(500);
            }
        }

        // Mark task as completed
        if (selectedTask != null) {
            selectedTask.setCompleted(true);
            taskViewModel.update(selectedTask);
            
            new AlertDialog.Builder(this)
                .setTitle("Luar Biasa! 🎉")
                .setMessage("Sesi fokus selesai. Tugas \"" + selectedTask.getTitle() + "\" telah ditandai sebagai selesai.")
                .setPositiveButton("Bagus!", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
