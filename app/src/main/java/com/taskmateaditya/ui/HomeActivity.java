package com.taskmateaditya.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.graphics.Bitmap;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.ActivityHomeBinding;
import com.taskmateaditya.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import android.net.Uri;

public class HomeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String PREFS_NAME = "TaskMatePrefs";
    private static final String KEY_FIRST_LOGIN = "isFirstLogin";

    private ActivityHomeBinding binding;
    private TaskViewModel taskViewModel;
    private TaskAdapter taskAdapter;

    // --- Groq AI Config ---
    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";
    private OkHttpClient aiHttpClient;

    // Voice to Task
    private SpeechRecognizer speechRecognizer;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Firestore auth-aware sync listener
    private FirebaseAuth.AuthStateListener authStateListener;

    // TTS
    private TextToSpeech textToSpeech;
    private boolean voiceActionRequested = false;

    // Speed Dial state
    private boolean isFabOpen = false;

    // OCR Photo-to-Task
    private ActivityResultLauncher<Void> cameraLauncher;
    private ActivityResultLauncher<String> requestCameraPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Launchers
        setupScanTaskLaunchers();

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

        taskViewModel.setSearchQuery("");

        setupSwipeToRefresh();

        aiHttpClient = com.taskmateaditya.TaskMateApplication.getInstance().getHttpClient();

        setupListeners();
        checkNotificationPermission();
        textToSpeech = new TextToSpeech(this, this);
        
        // Start Breathing Animation for AI Header (Option A)
        startBreathingAnimation();
 
        // Restore on Login: show blocking dialog on first install, silent refresh
        // afterward
        checkAndRestoreOnFirstLogin();
    }

    /**
     * Detects whether this is the first launch on this device after login.
     * - First time: shows a blocking "Memulihkan data..." dialog, clears the flag
     * when done.
     * - Subsequent times: silent background refresh (existing behaviour).
     */
    private void checkAndRestoreOnFirstLogin() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isFirstLogin = prefs.getBoolean(KEY_FIRST_LOGIN, true);

        if (isFirstLogin) {
            // Build a non-cancellable spinner dialog
            AlertDialog restoreDialog = new AlertDialog.Builder(this)
                    .setTitle("Memulihkan Data")
                    .setMessage("Sedang mengambil tugas Anda dari Cloud...\nMohon tunggu sebentar. ☁️")
                    .setCancelable(false)
                    .setView(LayoutInflater.from(this).inflate(
                            android.R.layout.activity_list_item, null, false))
                    .create();
            restoreDialog.show();

            taskViewModel.refreshTasksFromCloud(() -> {
                // Called on main thread after data is in Room
                if (restoreDialog.isShowing())
                    restoreDialog.dismiss();

                // Mark first login done so dialog never shows again on this device
                prefs.edit().putBoolean(KEY_FIRST_LOGIN, false).apply();

                com.google.android.material.snackbar.Snackbar
                        .make(binding.getRoot(), "✅ Data berhasil dipulihkan!",
                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                        .setAnchorView(
                                binding.btnAddTaskFab.getVisibility() == View.VISIBLE ? binding.btnAddTaskFab : null)
                        .show();
            });
        }
    }

    private void setupScanTaskLaunchers() {
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.TakePicturePreview(), bitmap -> {
            if (bitmap != null) {
                extractTextFromImage(bitmap);
            }
        });

        requestCameraPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        cameraLauncher.launch(null);
                    } else {
                        Toast.makeText(this, "Izin kamera ditolak. Tidak bisa melakukan scan.", Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private void extractTextFromImage(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Mengekstrak teks dari foto...", Toast.LENGTH_SHORT).show();

        recognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String resultText = visionText.getText();
                    if (resultText.isEmpty()) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(this, "Tidak ada teks yang terdeteksi.", Toast.LENGTH_SHORT).show();
                    } else {
                        binding.progressBar.setVisibility(View.GONE);
                        showScanOptionsDialog(resultText);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Gagal memproses gambar.", Toast.LENGTH_SHORT).show();
                });
    }

    private void showScanOptionsDialog(String text) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Hasil Scan Berhasil")
                .setMessage("Teks berhasil dideteksi. Apa yang ingin Anda lakukan selanjutnya?")
                .setNeutralButton("Batal", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Tanya-Jawab AI", (dialog, which) -> {
                    Intent intent = new Intent(this, AiChatActivity.class);
                    intent.putExtra(AiChatActivity.EXTRA_DOC_TEXT, text);
                    startActivity(intent);
                })
                .setPositiveButton("Buat Tugas Pintar", (dialog, which) -> {
                    binding.progressBar.setVisibility(View.VISIBLE);
                    callImageNLPApi(text);
                })
                .show();
    }

    private void callImageNLPApi(String text) {
        // Logika sama dengan Voice NLP tapi dengan input teks dari OCR
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                    "Extract task details from this OCR text into JSON: {title, date(yyyy-MM-dd), time(HH:mm), category, priority, subtasks: [\"step1\", \"step2\", ...]}. "
                            +
                            "Break down the text into actionable steps. Use logical defaults if missing. Language: Indonesian."));
            messages.put(new JSONObject().put("role", "user").put("content", text));
            jsonBody.put("messages", messages);
        } catch (Exception e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .post(body)
                .build();

        aiHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(HomeActivity.this, "AI Gagal terhubung.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String responseData = response.body().string();
                        JSONObject resp = new JSONObject(responseData);
                        String content = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                                .getString("content");

                        if (content.contains("{")) {
                            content = content.substring(content.indexOf("{"), content.lastIndexOf("}") + 1);
                        }

                        JSONObject taskJson = new JSONObject(content);
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            openDetailWithAiData(taskJson);
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            Toast.makeText(HomeActivity.this, "AI Gagal memproses data.", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(HomeActivity.this, "Pencarian AI Gagal.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setupSwipeToRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.tm_green);
        binding.swipeRefreshLayout.setOnRefreshListener(() -> {
            taskViewModel.refreshTasksFromCloud();
        });

        taskViewModel.getIsLoading().observe(this, isLoading -> {
            binding.swipeRefreshLayout.setRefreshing(isLoading);
        });
    }

    private void setupSwipeActions() {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

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

        if (newStatus) {
            try {
                JSONArray arr = new JSONArray(task.getSubtasksJson());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    obj.put("isCompleted", true);
                }
                task.setSubtasksJson(arr.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        taskViewModel.update(task);

        if (newStatus) {
            binding.getRoot().setHapticFeedbackEnabled(true);
            binding.getRoot().performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }

        String pesan = newStatus ? "Selamat! 🎉 Tugas selesai dengan sempurna!"
                : "Tugas dibuka kembali! Ayo selesaikan dengan semangat! 💪";
        Snackbar.make(binding.getRoot(), pesan, Snackbar.LENGTH_SHORT)
                .setAnchorView(binding.btnAddTaskFab.getVisibility() == View.VISIBLE ? binding.btnAddTaskFab : null)
                .setAction("Batal", v -> {
                    task.setCompleted(!newStatus);
                    taskViewModel.update(task);
                })
                .show();
    }

    private void setupListeners() {
        binding.btnAddTaskEmpty.setOnClickListener(v -> openDetailActivity(null));
        binding.btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        binding.btnAddTaskFab.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            toggleSpeedDial();
        });
        binding.fabScrim.setOnClickListener(v -> closeSpeedDial());

        if (binding.fabAiChat != null) {
            binding.fabAiChat.setOnClickListener(v -> {
                closeSpeedDial();
                Intent intent = new Intent(HomeActivity.this, AiChatActivity.class);
                startActivity(intent);
            });
        }

        binding.btnNotification.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationHistoryActivity.class);
            startActivity(intent);
        });

        binding.btnSort.setOnClickListener(v -> showSortMenu());

        if (binding.fabVoiceTask != null) {
            binding.fabVoiceTask.setOnClickListener(v -> {
                closeSpeedDial();
                checkVoicePermissionAndStart();
            });
        }

        if (binding.fabFocusTimer != null) {
            binding.fabFocusTimer.setOnClickListener(v -> {
                closeSpeedDial();
                startActivity(new Intent(HomeActivity.this, FocusTimerActivity.class));
            });
        }

        if (binding.fabItemAddTask != null) {
            binding.fabItemAddTask.setOnClickListener(v -> {
                closeSpeedDial();
                openDetailActivity(null);
            });
        }

        // --- AI Daily Briefing (Option A: Integrated in Greeting) ---
        binding.layoutHeaderBriefing.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            startDailyBriefing();
        });

        if (binding.fabScanTask != null) {
            binding.fabScanTask.setOnClickListener(v -> {
                closeSpeedDial();
                checkCameraPermissionAndStart();
            });
        }

        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                taskViewModel.setCategoryFilter("Semua");
                return;
            }
            int id = checkedIds.get(0);
            String selectedCategory = "Semua";
            if (id == R.id.chipKuliah)
                selectedCategory = "Sekolah/Kuliah";
            else if (id == R.id.chipPribadi)
                selectedCategory = "Pribadi";
            else if (id == R.id.chipKerja)
                selectedCategory = "Pekerjaan";
            taskViewModel.setCategoryFilter(selectedCategory);
        });

        binding.editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                taskViewModel.setSearchQuery(s.toString());
            }
        });

        taskAdapter.setOnItemClickListener(new TaskAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Task task) {
                openDetailActivity(task.getId());
            }

            @Override
            public void onCheckChange(Task task, boolean isCompleted) {
                if (isCompleted) {
                    try {
                        JSONArray arr = new JSONArray(task.getSubtasksJson());
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            obj.put("isCompleted", true);
                        }
                        task.setSubtasksJson(arr.toString());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                task.setCompleted(isCompleted);
                taskViewModel.update(task);

                if (isCompleted) {
                    binding.getRoot().performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                }

                Snackbar.make(binding.getRoot(),
                        isCompleted ? "Selamat! 🎉 Tugas selesai dengan sukses!"
                                : "Tugas dibuka kembali. Ayo selesaikan dengan semangat! 💪",
                        Snackbar.LENGTH_SHORT)
                        .setAnchorView(
                                binding.btnAddTaskFab.getVisibility() == View.VISIBLE ? binding.btnAddTaskFab : null)
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
                            .setAnchorView(binding.btnAddTaskFab.getVisibility() == View.VISIBLE ? binding.btnAddTaskFab
                                    : null)
                            .setAction("Batalkan", v -> taskViewModel.insert(task)).show();
                })
                .setNegativeButton("Batal", (dialog, which) -> taskAdapter.notifyDataSetChanged())
                .setCancelable(false)
                .show();
    }

    private void openDetailActivity(String taskId) {
        Intent intent = new Intent(this, DetailTaskActivity.class);
        if (taskId != null)
            intent.putExtra("EXTRA_TASK_ID", taskId);
        startActivity(intent);
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(this, binding.btnSort);
        popup.getMenu().add(0, 1, 0, "Terbaru");
        popup.getMenu().add(0, 2, 0, "Batas Waktu");
        popup.getMenu().add(0, 3, 0, "Prioritas");
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    taskViewModel.setSortOrder(TaskViewModel.SortType.NEWEST);
                    return true;
                case 2:
                    taskViewModel.setSortOrder(TaskViewModel.SortType.DEADLINE);
                    return true;
                case 3:
                    taskViewModel.setSortOrder(TaskViewModel.SortType.PRIORITY);
                    return true;
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
        if (isFabOpen)
            closeSpeedDial();
    }

    private void toggleSpeedDial() {
        if (isFabOpen) {
            closeSpeedDial();
        } else {
            openSpeedDial();
        }
    }

    private void openSpeedDial() {
        isFabOpen = true;

        binding.btnAddTaskFab.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .setDuration(100)
                .withEndAction(() -> {
                    binding.btnAddTaskFab.setImageResource(R.drawable.ic_baseline_close_24);
                    binding.btnAddTaskFab.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .start();
                }).start();

        binding.fabScrim.setVisibility(View.VISIBLE);
        binding.fabScrim.animate().alpha(1f).setDuration(250).start();

        showSubFab(binding.fabItemAddTask, 0);
        showSubFab(binding.fabItemVoice, 60);
        showSubFab(binding.fabItemAi, 120);
        showSubFab(binding.fabItemScan, 180);
        showSubFab(binding.fabItemTimer, 240);
    }

    private void closeSpeedDial() {
        isFabOpen = false;

        binding.btnAddTaskFab.animate()
                .scaleX(0.85f).scaleY(0.85f)
                .setDuration(100)
                .withEndAction(() -> {
                    binding.btnAddTaskFab.setImageResource(R.drawable.ic_baseline_add_24);
                    binding.btnAddTaskFab.animate()
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150)
                            .setInterpolator(new android.view.animation.OvershootInterpolator())
                            .start();
                }).start();

        binding.fabScrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> binding.fabScrim.setVisibility(View.GONE)).start();

        hideSubFab(binding.fabItemTimer, 0);
        hideSubFab(binding.fabItemScan, 30);
        hideSubFab(binding.fabItemAi, 60);
        hideSubFab(binding.fabItemVoice, 90);
        hideSubFab(binding.fabItemAddTask, 120);
    }

    private void showSubFab(android.view.View item, long delayMs) {
        item.setVisibility(View.VISIBLE);
        item.setTranslationY(30f);
        item.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void hideSubFab(android.view.View item, long delayMs) {
        item.animate()
                .alpha(0f)
                .translationY(20f)
                .setStartDelay(delayMs)
                .setDuration(160)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> item.setVisibility(View.GONE))
                .start();
    }

    private void checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null);
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS }, 101);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        authStateListener = auth -> {
            if (auth.getCurrentUser() != null) {
                taskViewModel.syncData();
                taskViewModel.refreshTasksFromCloud();
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
        taskViewModel.stopSync();
    }

    @Override
    protected void onPause() {
        super.onPause();
        voiceActionRequested = false;
        if (textToSpeech != null && textToSpeech.isSpeaking()) {
            textToSpeech.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
        binding = null;
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech.setLanguage(new Locale("id", "ID"));
        }
    }

    private void checkVoicePermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO },
                    REQUEST_RECORD_AUDIO_PERMISSION);
        } else {
            startVoiceRecognition();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceRecognition();
            } else {
                Toast.makeText(this, "Izin mikrofon diperlukan untuk fitur suara.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startVoiceRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Toast.makeText(HomeActivity.this, "Mendengarkan...", Toast.LENGTH_SHORT).show();

                binding.btnAddTaskFab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444));
                binding.btnAddTaskFab.setImageResource(R.drawable.ic_baseline_mic_24);
                android.view.animation.Animation pulse = android.view.animation.AnimationUtils
                        .loadAnimation(HomeActivity.this, R.anim.pulse);
                binding.btnAddTaskFab.startAnimation(pulse);
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
                resetFabAfterSpeech();
            }

            @Override
            public void onError(int error) {
                resetFabAfterSpeech();
                String message = "Gagal mendeteksi suara.";
                if (error == SpeechRecognizer.ERROR_NO_MATCH)
                    message = "Tidak ada suara yang cocok.";
                else if (error == SpeechRecognizer.ERROR_NETWORK)
                    message = "Masalah jaringan.";
                Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String voiceText = matches.get(0);
                    callVoiceNLPApi(voiceText);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        });

        speechRecognizer.startListening(recognizerIntent);
    }

    private void resetFabAfterSpeech() {
        if (binding != null && binding.btnAddTaskFab != null) {
            binding.btnAddTaskFab.clearAnimation();
            binding.btnAddTaskFab.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.tm_green)));
            binding.btnAddTaskFab.setImageResource(R.drawable.ic_baseline_add_24);
        }
    }

    private void callVoiceNLPApi(String text) {
        binding.progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(this, "Mengekstrak rincian tugas cerdas...", Toast.LENGTH_SHORT).show();

        String systemPrompt = "You are an intelligent task assistant for 'TaskMate'. Analyze the user's voice input and return a JSON object with an 'intent' and a 'payload'.\n\n"
                + "Intents:\n"
                + "1. 'ADD_TASK': User wants to create a task. Payload: {title, date(YYYY-MM-DD), time(HH:MM), category, priority}.\n"
                + "2. 'SEARCH': User wants to find a task. Payload: {query}.\n"
                + "3. 'START_TIMER': User wants to focus. Payload: {duration_minutes, task_title(optional)}.\n"
                + "4. 'SUMMARY': User asks about their general schedule.\n"
                + "5. 'DEADLINE_SUMMARY': User asks specifically for their closest or next deadline.\n"
                + "6. 'NAVIGATE': User wants to open a menu. Payload: {target: 'settings', 'notifications', 'ai_chat', 'timer', 'dashboard', 'home'}.\n"
                + "7. 'COMPLETE_TASK': User wants to finish a task. Payload: {task_title}.\n"
                + "8. 'DELETE_TASK': User wants to remove a task. Payload: {task_title}.\n\n"
                + "Context: Current date is " + new java.util.Date().toString() + "\n"
                + "Input: " + text;

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content",
                    "Return ONLY JSON with 'intent' and 'payload'."));
            messages.put(new JSONObject().put("role", "user").put("content", systemPrompt));
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.1);
        } catch (Exception e) {
            binding.progressBar.setVisibility(View.GONE);
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .post(body)
                .build();

        aiHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(HomeActivity.this, "Gagal AI: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || binding == null)
                        return;
                    binding.progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String content = jsonResponse.getJSONArray("choices").getJSONObject(0)
                                    .getJSONObject("message").getString("content").trim();

                            if (content.startsWith("```")) {
                                content = content.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
                            }

                            JSONObject voiceResponse = new JSONObject(content);
                            handleVoiceIntent(voiceResponse);

                        } catch (Exception e) {
                            Log.e("VoiceNLP", "Parse error: " + responseData, e);
                            Toast.makeText(HomeActivity.this, "Gagal memproses AI.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(HomeActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT)
                                .show();
                    }
                });
            }
        });
    }

    private void handleVoiceIntent(JSONObject json) {
        String intent = json.optString("intent", "ADD_TASK");
        JSONObject payload = json.optJSONObject("payload");
        if (payload == null)
            payload = new JSONObject();

        switch (intent) {
            case "SEARCH":
                String query = payload.optString("query", "");
                binding.editTextSearch.setText(query);
                taskViewModel.setSearchQuery(query);
                Toast.makeText(this, "Mencari: " + query, Toast.LENGTH_SHORT).show();
                break;

            case "START_TIMER":
                Intent timerIntent = new Intent(this, FocusTimerActivity.class);
                timerIntent.putExtra("EXTRA_VOICE_DURATION", payload.optInt("duration_minutes", 25));
                timerIntent.putExtra("EXTRA_VOICE_TASK", payload.optString("task_title", ""));
                startActivity(timerIntent);
                break;

            case "SUMMARY":
                voiceActionRequested = true;
                generateAndSpeakSummary();
                break;

            case "DEADLINE_SUMMARY":
                voiceActionRequested = true;
                speakClosestDeadline();
                break;

            case "NAVIGATE":
                navigateViaVoice(payload.optString("target", ""));
                break;

            case "COMPLETE_TASK":
                voiceActionRequested = true;
                toggleTaskViaVoice(payload.optString("task_title", ""), true);
                break;

            case "DELETE_TASK":
                voiceActionRequested = true;
                deleteTaskViaVoice(payload.optString("task_title", ""));
                break;

            case "ADD_TASK":
                voiceActionRequested = false;
                openDetailWithAiData(payload);
                break;
            default:
                voiceActionRequested = false;
                openDetailWithAiData(payload);
                break;
        }
    }

    private void generateAndSpeakSummary() {
        List<Task> currentTasks = taskViewModel.getAllTasks().getValue();
        if (currentTasks != null) {
            processAndSpeak(currentTasks);
        } else {
            taskViewModel.getAllTasks().observe(this, new Observer<List<Task>>() {
                @Override
                public void onChanged(List<Task> tasks) {
                    taskViewModel.getAllTasks().removeObserver(this);
                    processAndSpeak(tasks);
                }
            });
        }
    }

    private void processAndSpeak(List<Task> tasks) {
        if (!voiceActionRequested || isFinishing() || isDestroyed())
            return;
        voiceActionRequested = false;

        if (tasks == null || tasks.isEmpty()) {
            speak("Jadwal Anda masih kosong. Ayo buat tugas baru!");
            return;
        }

        int count = 0;
        StringBuilder sb = new StringBuilder("Berikut adalah agenda Anda hari ini. ");
        for (Task t : tasks) {
            if (!t.isCompleted()) {
                count++;
                if (count <= 3) {
                    sb.append("Tugas ke-").append(count).append(" adalah ").append(t.getTitle()).append(". ");
                }
            }
        }

        if (count > 3) {
            sb.append("Dan ada ").append(count - 3).append(" tugas lainnya yang menunggu.");
        } else if (count == 0 && !tasks.isEmpty()) {
            sb.append("Semua tugas Anda sudah selesai. Luar biasa!");
        }

        speak(sb.toString());
    }

    private void speakClosestDeadline() {
        List<Task> currentTasks = taskViewModel.getAllTasks().getValue();
        if (currentTasks != null) {
            processAndSpeakDeadline(currentTasks);
        } else {
            taskViewModel.getAllTasks().observe(this, new Observer<List<Task>>() {
                @Override
                public void onChanged(List<Task> tasks) {
                    taskViewModel.getAllTasks().removeObserver(this);
                    processAndSpeakDeadline(tasks);
                }
            });
        }
    }

    private void processAndSpeakDeadline(List<Task> tasks) {
        if (!voiceActionRequested || isFinishing() || isDestroyed())
            return;
        voiceActionRequested = false;

        if (tasks == null || tasks.isEmpty()) {
            speak("Tidak ada tugas yang terdaftar.");
            return;
        }

        Task closest = null;
        long now = System.currentTimeMillis();
        long minDiff = Long.MAX_VALUE;

        for (Task t : tasks) {
            if (!t.isCompleted() && t.getReminderTime() > 0) {
                long diff = t.getReminderTime() - now;
                if (diff > 0 && diff < minDiff) {
                    minDiff = diff;
                    closest = t;
                }
            }
        }

        if (closest != null) {
            speak("Tugas dengan deadline terdekat adalah " + closest.getTitle()
                    + " pada tanggal " + closest.getDeadline() + ". Semangat menyelesaikannya!");
        } else {
            speak("Sepertinya Anda tidak memiliki tugas mendatang dengan pengingat aktif.");
        }
    }

    private void navigateViaVoice(String target) {
        if (target == null || target.isEmpty())
            return;

        Intent intent = null;
        String message = "";

        switch (target.toLowerCase()) {
            case "settings":
            case "pengaturan":
                intent = new Intent(this, SettingsActivity.class);
                message = "Membuka Pengaturan.";
                break;
            case "notifications":
            case "riwayat":
            case "notifikasi":
                intent = new Intent(this, NotificationHistoryActivity.class);
                message = "Membuka Riwayat Notifikasi.";
                break;
            case "ai_chat":
            case "chat":
                intent = new Intent(this, AiChatActivity.class);
                message = "Membuka Chat A I.";
                break;
            case "timer":
            case "fokus":
                intent = new Intent(this, FocusTimerActivity.class);
                message = "Membuka Timer Fokus.";
                break;
            case "dashboard":
                intent = new Intent(this, DashboardActivity.class);
                message = "Membuka Dashboard Statistik.";
                break;
            case "home":
                message = "Anda sudah berada di halaman Home.";
                break;
        }

        if (intent != null) {
            speak(message);
            startActivity(intent);
        } else if (!message.isEmpty()) {
            speak(message);
        } else {
            speak("Maaf, saya tidak menemukan menu tersebut.");
        }
    }

    private void toggleTaskViaVoice(String title, boolean complete) {
        List<Task> currentTasks = taskViewModel.getAllTasks().getValue();
        if (currentTasks != null) {
            processToggleTask(currentTasks, title, complete);
        } else {
            taskViewModel.getAllTasks().observe(this, new Observer<List<Task>>() {
                @Override
                public void onChanged(List<Task> tasks) {
                    taskViewModel.getAllTasks().removeObserver(this);
                    processToggleTask(tasks, title, complete);
                }
            });
        }
    }

    private void processToggleTask(List<Task> tasks, String title, boolean complete) {
        if (!voiceActionRequested || isFinishing() || isDestroyed())
            return;
        voiceActionRequested = false;

        if (tasks == null || title == null || title.isEmpty())
            return;

        for (Task t : tasks) {
            if (t.getTitle().toLowerCase().contains(title.toLowerCase())) {
                t.setCompleted(complete);
                taskViewModel.update(t);
                speak("Oke, tugas " + t.getTitle() + " berhasil ditandai sebagai "
                        + (complete ? "selesai." : "belum selesai."));
                return;
            }
        }
        speak("Maaf, saya tidak menemukan tugas dengan judul " + title);
    }

    private void deleteTaskViaVoice(String title) {
        List<Task> currentTasks = taskViewModel.getAllTasks().getValue();
        if (currentTasks != null) {
            processDeleteTask(currentTasks, title);
        } else {
            taskViewModel.getAllTasks().observe(this, new Observer<List<Task>>() {
                @Override
                public void onChanged(List<Task> tasks) {
                    taskViewModel.getAllTasks().removeObserver(this);
                    processDeleteTask(tasks, title);
                }
            });
        }
    }

    private void processDeleteTask(List<Task> tasks, String title) {
        if (!voiceActionRequested || isFinishing() || isDestroyed())
            return;
        voiceActionRequested = false;

        if (tasks == null || title == null || title.isEmpty())
            return;

        for (Task t : tasks) {
            if (t.getTitle().toLowerCase().contains(title.toLowerCase())) {
                taskViewModel.delete(t);
                speak("Tugas " + t.getTitle() + " telah dihapus sesuai perintah.");
                return;
            }
        }
        speak("Tugas " + title + " tidak ditemukan.");
    }

    private void speak(String text) {
        if (textToSpeech != null) {
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SummaryID");
        }
    }

 
    private void startDailyBriefing() {
        Toast.makeText(this, "Menyusun rangkuman hari ini...", Toast.LENGTH_SHORT).show();
        binding.progressBar.setVisibility(View.VISIBLE);
 
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        final String userId = (currentUser != null) ? currentUser.getUid() : null;
        if (userId == null) {
            binding.progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Mohon login untuk menggunakan fitur ini.", Toast.LENGTH_SHORT).show();
            return;
        }
 
        com.taskmateaditya.data.TaskDatabase.databaseWriteExecutor.execute(() -> {
            List<Task> pendingTasks = com.taskmateaditya.data.TaskDatabase.getDatabase(this)
                    .taskDao().getPendingTasksForUser(userId);
            
            final String taskSummary = buildTaskSummary(pendingTasks);
            runOnUiThread(() -> callBriefingApi(taskSummary));
        });
    }
 
    private String buildTaskSummary(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return "No pending tasks.";
        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            sb.append("- ").append(t.getTitle())
              .append(" (Deadline: ").append(t.getDeadline()).append(")\n");
        }
        return sb.toString();
    }
 
    private void callBriefingApi(String summary) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();
            String systemPrompt = "Anda adalah TaskMate AI, asisten produktivitas yang ceria dan akrab. " +
                    "Tugas Anda: buat naskah briefing pagi singkat (max 80 kata) dalam Bahasa Indonesia " +
                    "yang sangat memotivasi dan santai (ngobrol) berdasarkan daftar tugas berikut. " +
                    "Sapa user dengan semangat, sebutkan beberapa tugas penting, dan beri kata penutup yang kuat. " +
                    "Jangan gunakan format bullet points, tulis dalam bentuk narasi untuk dibacakan.";
            
            messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", "Berikut daftar tugas saya hari ini:\n" + summary));
            jsonBody.put("messages", messages);
        } catch (Exception e) { e.printStackTrace(); }
 
        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL).addHeader("Authorization", "Bearer " + GROQ_API_KEY).post(body).build();
 
        aiHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(HomeActivity.this, "Gagal terhubung ke AI.", Toast.LENGTH_SHORT).show();
                });
            }
 
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                runOnUiThread(() -> {
                    binding.progressBar.setVisibility(View.GONE);
                    try {
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String script = jsonResponse.getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content").trim();
                        playBriefing(script);
                    } catch (Exception e) {
                        Toast.makeText(HomeActivity.this, "Gagal memproses naskah.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
 
    private void startBreathingAnimation() {
        android.view.animation.AlphaAnimation breathing = new android.view.animation.AlphaAnimation(0.5f, 1.0f);
        breathing.setDuration(1500);
        breathing.setRepeatMode(android.view.animation.Animation.REVERSE);
        breathing.setRepeatCount(android.view.animation.Animation.INFINITE);
        binding.ivMagicSparkle.startAnimation(breathing);
        binding.tvHeaderSmallGreeting.startAnimation(breathing);
    }
 
    private void playBriefing(String text) {
        if (textToSpeech != null) {
            // Stop breathing, start active pulse during speaking
            binding.ivMagicSparkle.clearAnimation();
            binding.tvHeaderSmallGreeting.clearAnimation();
            
            android.view.animation.Animation pulse = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse);
            binding.layoutHeaderBriefing.startAnimation(pulse);
            binding.ivMagicSparkle.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFEF4444)); // Red during speech
            
            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "BriefingID");
            
            // Revert back when done
            textToSpeech.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {}
                @Override public void onDone(String utteranceId) {
                    runOnUiThread(() -> {
                        binding.layoutHeaderBriefing.clearAnimation();
                        binding.ivMagicSparkle.setImageTintList(android.content.res.ColorStateList.valueOf(0xFF00C853)); // Back to Green
                        startBreathingAnimation(); // Restart breathing
                    });
                }
                @Override public void onError(String utteranceId) {}
            });
        }
    }
 
    private void openDetailWithAiData(JSONObject taskJson) {
        Intent intent = new Intent(this, DetailTaskActivity.class);
        intent.putExtra(DetailTaskActivity.EXTRA_VOICE_TITLE, taskJson.optString("title", ""));
        intent.putExtra(DetailTaskActivity.EXTRA_VOICE_DATE, taskJson.optString("date", ""));
        intent.putExtra(DetailTaskActivity.EXTRA_VOICE_TIME, taskJson.optString("time", ""));
        intent.putExtra(DetailTaskActivity.EXTRA_VOICE_CATEGORY, taskJson.optString("category", ""));
        intent.putExtra(DetailTaskActivity.EXTRA_VOICE_PRIORITY, taskJson.optString("priority", ""));

        // Pass subtasks if any
        if (taskJson.has("subtasks")) {
            intent.putExtra(DetailTaskActivity.EXTRA_AI_SUBTASKS, taskJson.optJSONArray("subtasks").toString());
        }

        startActivity(intent);
    }
}