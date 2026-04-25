package com.taskmateaditya.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.services.drive.DriveScopes;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Subtask;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.ActivityDetailTaskBinding;
import com.taskmateaditya.utils.DriveServiceHelper;
import com.taskmateaditya.utils.ReminderHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DetailTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "EXTRA_TASK_ID";
    private static final int REQUEST_CODE_SIGN_IN_DRIVE = 100;

    // Intent Extras for Voice NLP
    public static final String EXTRA_VOICE_TITLE = "EXTRA_VOICE_TITLE";
    public static final String EXTRA_VOICE_DATE = "EXTRA_VOICE_DATE";
    public static final String EXTRA_VOICE_TIME = "EXTRA_VOICE_TIME";
    public static final String EXTRA_VOICE_CATEGORY = "EXTRA_VOICE_CATEGORY";
    public static final String EXTRA_VOICE_PRIORITY = "EXTRA_VOICE_PRIORITY";
    public static final String EXTRA_AI_SUBTASKS = "EXTRA_AI_SUBTASKS";

    // --- Groq AI Config (same as AiChatActivity) ---
    private static final String GROQ_API_KEY = com.taskmateaditya.BuildConfig.GROQ_API_KEY;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private ActivityDetailTaskBinding binding;
    private TaskViewModel taskViewModel;

    private String taskId = null;
    private Task currentTask;
    private Calendar alarmCalendar = Calendar.getInstance();

    private java.util.List<Subtask> subtaskList = new java.util.ArrayList<>();
    private SubtaskAdapter subtaskAdapter;

    // Variabel Google Drive
    private DriveServiceHelper mDriveServiceHelper;
    private Uri selectedLocalUri = null;
    private String cloudFileUrl = null;

    // AI Subtask Generator
    private String generatedSubtasks = null;
    private OkHttpClient aiHttpClient;

    // AI Smart Categorization — debounce
    private static final long AI_DEBOUNCE_MS = 1500L;
    private final Handler aiDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable aiCategorizationRunnable;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedLocalUri = result.getData().getData();
                    if (selectedLocalUri != null) {
                        showAttachmentInfo("File Siap Diupload (Klik Simpan)", true);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        // Initialize OkHttpClient for AI subtask calls
        aiHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        setupDropdowns();
        setupInputBehavior();
        setupSubtasks();

        if (getIntent().hasExtra(EXTRA_TASK_ID)) {
            taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
            if (taskId != null) {
                loadTaskData(taskId);
                binding.btnDelete.setVisibility(View.VISIBLE);
                binding.btnUpdate.setText("PERBARUI TUGAS");
            } else {
                setupNewTaskMode();
                handleVoiceExtras();
            }
        } else {
            setupNewTaskMode();
            handleVoiceExtras();
        }

        setupListeners();
    }

    private void requestDriveSignIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN_DRIVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SIGN_IN_DRIVE) {
            if (resultCode == RESULT_OK) {
                handleDriveSignIn();
                openFilePicker();
            } else {
                Toast.makeText(this, "Gagal login Google Drive", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleDriveSignIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            mDriveServiceHelper = new DriveServiceHelper(
                    DriveServiceHelper.getGoogleDriveService(this, account, "TaskMate"));
        }
    }

    private void checkDrivePermissionAndPickFile() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE_FILE))) {
            handleDriveSignIn();
            openFilePicker();
        } else {
            requestDriveSignIn();
        }
    }

    private void prepareSaveTask() {
        String title = binding.editTextTitle.getText().toString().trim();
        String deadline = binding.editTextDeadline.getText().toString().trim();

        if (title.isEmpty() || deadline.isEmpty()) {
            Toast.makeText(this, "Judul dan Deadline wajib diisi.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (binding.switchReminder.isChecked()) {
            if (alarmCalendar.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Waktu pengingat sudah lewat, mohon atur ulang waktu.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (selectedLocalUri != null) {
            if (mDriveServiceHelper == null) {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null)
                    handleDriveSignIn();

                if (mDriveServiceHelper == null) {
                    Toast.makeText(this, "Sesi Drive habis, silakan pilih file ulang", Toast.LENGTH_SHORT).show();
                    checkDrivePermissionAndPickFile();
                    return;
                }
            }
            uploadFileToDrive();
        } else {
            saveTaskToDatabase(cloudFileUrl);
        }
    }

    private void uploadFileToDrive() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Mengupload ke Google Drive...");
        progressDialog.setMessage("Mohon tunggu");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mExecutor.execute(() -> {
            try {
                String fileName = "task_file_" + System.currentTimeMillis();
                String fileId = mDriveServiceHelper.uploadFile(getContentResolver(), selectedLocalUri, fileName);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (fileId != null) {
                        Toast.makeText(this, "Upload Berhasil!", Toast.LENGTH_SHORT).show();
                        String driveLink = "googledrive://" + fileId;
                        saveTaskToDatabase(driveLink);
                    } else {
                        Toast.makeText(this, "Gagal mendapatkan ID File", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Gagal Upload: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setupNewTaskMode() {
        taskId = null;
        binding.btnDelete.setVisibility(View.GONE);
        binding.btnUpdate.setText("TAMBAH TUGAS");
        binding.tvAiSuggestionBadge.setVisibility(View.GONE);
    }

    private void handleVoiceExtras() {
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_VOICE_TITLE)) {
            binding.editTextTitle.setText(intent.getStringExtra(EXTRA_VOICE_TITLE));
        }
        if (intent.hasExtra(EXTRA_VOICE_DATE)) {
            binding.editTextDeadline.setText(intent.getStringExtra(EXTRA_VOICE_DATE));
        }
        if (intent.hasExtra(EXTRA_VOICE_TIME)) {
            binding.editTextTime.setText(intent.getStringExtra(EXTRA_VOICE_TIME));
            binding.switchReminder.setChecked(true);
        }
        if (intent.hasExtra(EXTRA_VOICE_CATEGORY)) {
            String cat = intent.getStringExtra(EXTRA_VOICE_CATEGORY);
            binding.editTextCategory.setText(cat, false);
        }
        if (intent.hasExtra(EXTRA_VOICE_PRIORITY)) {
            String prio = intent.getStringExtra(EXTRA_VOICE_PRIORITY);
            binding.editTextPriority.setText(prio, false);
        }
        
        // Handle AI-generated subtasks
        if (intent.hasExtra(EXTRA_AI_SUBTASKS)) {
            try {
                String subtasksJson = intent.getStringExtra(EXTRA_AI_SUBTASKS);
                org.json.JSONArray array = new org.json.JSONArray(subtasksJson);
                for (int i = 0; i < array.length(); i++) {
                    String subtaskTitle = array.getString(i);
                    if (!subtaskTitle.trim().isEmpty()) {
                        subtaskList.add(new com.taskmateaditya.data.Subtask(subtaskTitle));
                    }
                }
                if (subtaskAdapter != null) {
                    subtaskAdapter.notifyDataSetChanged();
                }
            } catch (Exception e) {
                android.util.Log.e("DetailTaskActivity", "Error parsing AI subtasks", e);
            }
        }
    }

    private void setupDropdowns() {
        binding.editTextPriority.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item,
                getResources().getStringArray(R.array.priority_options)));
        binding.editTextCategory.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item,
                getResources().getStringArray(R.array.category_options)));
    }

    private void setupInputBehavior() {
        binding.editTextDeadline.setFocusable(false);
        binding.editTextDeadline.setClickable(true);
        binding.editTextTime.setFocusable(false);
        binding.editTextTime.setClickable(true);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnUpdate.setOnClickListener(v -> prepareSaveTask());
        binding.btnDelete.setOnClickListener(v -> showDeleteConfirmation());
        binding.editTextDeadline.setOnClickListener(v -> showDatePickerDialog());
        binding.editTextTime.setOnClickListener(v -> showTimePickerDialog());
        binding.editTextPriority.setOnClickListener(v -> binding.editTextPriority.showDropDown());
        binding.editTextCategory.setOnClickListener(v -> binding.editTextCategory.showDropDown());

        binding.btnAttachFile.setOnClickListener(v -> checkDrivePermissionAndPickFile());

        binding.btnRemoveAttachment.setOnClickListener(v -> {
            selectedLocalUri = null;
            cloudFileUrl = null;
            binding.layoutAttachmentInfo.setVisibility(View.GONE);
        });

        binding.tvAttachmentName.setOnClickListener(v -> openAttachment());

        // --- AI Subtask Generator ---
        binding.btnGenerateSubtasks.setOnClickListener(v -> generateSubtasks());
        binding.btnSaveSubtasks.setOnClickListener(v -> saveSubtasksToNotes());

        // --- AI Smart Categorization debounce TextWatcher ---
        // Only active for new tasks; editing existing tasks is unaffected.
        binding.editTextTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Cancel any pending categorization call
                if (aiCategorizationRunnable != null) {
                    aiDebounceHandler.removeCallbacks(aiCategorizationRunnable);
                }
                String title = s.toString().trim();
                // Only trigger for new tasks with a meaningful title
                if (taskId != null || title.length() < 3) {
                    binding.tvAiSuggestionBadge.setVisibility(View.GONE);
                    return;
                }
                // Schedule categorization after debounce delay
                aiCategorizationRunnable = () -> smartCategorize(title);
                aiDebounceHandler.postDelayed(aiCategorizationRunnable, AI_DEBOUNCE_MS);
            }
        });
    }
    private void setupSubtasks() {
        subtaskAdapter = new SubtaskAdapter(subtaskList, list -> {
            subtaskList = list;
        });
        binding.rvSubtasks.setAdapter(subtaskAdapter);

        binding.btnAddSubtask.setOnClickListener(v -> {
            subtaskList.add(new Subtask(""));
            subtaskAdapter.notifyItemInserted(subtaskList.size() - 1);
        });
    }

    private void loadSubtasksFromJson(String json) {
        subtaskList.clear();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Subtask s = new Subtask();
                s.setId(obj.optString("id", java.util.UUID.randomUUID().toString()));
                s.setTitle(obj.optString("title", ""));
                s.setCompleted(obj.optBoolean("isCompleted", false));
                subtaskList.add(s);
            }
        } catch (Exception e) {
            Log.e("DetailTaskActivity", "Error parsing subtasks", e);
        }
        if (subtaskAdapter != null) {
            subtaskAdapter.notifyDataSetChanged();
        }
    }

    private String getSubtasksAsJson() {
        JSONArray array = new JSONArray();
        for (Subtask s : subtaskList) {
            if (s.getTitle() == null || s.getTitle().trim().isEmpty()) continue; // Skip empty
            JSONObject obj = new JSONObject();
            try {
                obj.put("id", s.getId());
                obj.put("title", s.getTitle().trim());
                obj.put("isCompleted", s.isCompleted());
                array.put(obj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return array.toString();
    }



    // ============================================================
    // AI SMART CATEGORIZATION
    // ============================================================

    /**
     * Calls Groq API with a strict JSON prompt to auto-fill category & priority
     * dropdowns based on the given task title. Only runs for new tasks.
     */
    private void smartCategorize(String title) {
        // Show loading badge
        binding.tvAiSuggestionBadge.setText("🤖 AI menganalisis judul tugas...");
        binding.tvAiSuggestionBadge.setVisibility(View.VISIBLE);

        // System prompt: force strict JSON output with exact Indonesian option values
        String systemPrompt = "You are a task categorizer. "
                + "Respond ONLY with a valid JSON object — no explanation, no markdown, no code fences. "
                + "The JSON must have exactly two keys: "
                + "'category' (choose ONE from: Sekolah/Kuliah, Pribadi, Pekerjaan, Lain-lain) and "
                + "'priority' (choose ONE from: Tinggi, Sedang, Rendah). "
                + "Base your decision solely on the task title provided by the user.";

        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "Task title: " + title);
            messages.put(userMsg);

            jsonBody.put("messages", messages);
            // Low temperature → deterministic, less creative → precise category match
            jsonBody.put("temperature", 0.2);
        } catch (Exception e) {
            Log.e("AICategory", "JSON build error", e);
            binding.tvAiSuggestionBadge.setVisibility(View.GONE);
            return;
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        aiHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("AICategory", "Network error", e);
                // Silent fail — user keeps manual control
                runOnUiThread(() -> binding.tvAiSuggestionBadge.setVisibility(View.GONE));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    if (!response.isSuccessful()) {
                        Log.e("AICategory", "Server error " + response.code() + ": " + responseData);
                        binding.tvAiSuggestionBadge.setVisibility(View.GONE);
                        return;
                    }
                    try {
                        // Extract the AI text content
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String rawContent = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content")
                                .trim();

                        // Strip markdown code fences if the model added them despite instructions
                        if (rawContent.startsWith("```")) {
                            rawContent = rawContent
                                    .replaceAll("^```[a-zA-Z]*\\n?", "")
                                    .replaceAll("```$", "")
                                    .trim();
                        }

                        JSONObject result = new JSONObject(rawContent);
                        String category = result.optString("category", "").trim();
                        String priority = result.optString("priority", "").trim();

                        applyAiSuggestion(category, priority);

                    } catch (Exception e) {
                        Log.e("AICategory", "Parse error: " + responseData, e);
                        binding.tvAiSuggestionBadge.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    // Valid option lists (must match strings.xml exactly)
    private static final String[] VALID_CATEGORIES = { "Sekolah/Kuliah", "Pribadi", "Pekerjaan", "Lain-lain" };
    private static final String[] VALID_PRIORITIES = { "Tinggi", "Sedang", "Rendah" };

    /**
     * Validates returned values against the allowed lists and updates the
     * Category and Priority AutoCompleteTextView dropdowns.
     */
    private void applyAiSuggestion(String category, String priority) {
        boolean categoryValid = false;
        for (String v : VALID_CATEGORIES) {
            if (v.equalsIgnoreCase(category)) {
                category = v;
                categoryValid = true;
                break;
            }
        }
        boolean priorityValid = false;
        for (String v : VALID_PRIORITIES) {
            if (v.equalsIgnoreCase(priority)) {
                priority = v;
                priorityValid = true;
                break;
            }
        }

        if (!categoryValid && !priorityValid) {
            Log.w("AICategory", "Invalid AI response → category='" + category + "' priority='" + priority + "'");
            binding.tvAiSuggestionBadge.setVisibility(View.GONE);
            return;
        }

        if (categoryValid)
            binding.editTextCategory.setText(category, false);
        if (priorityValid)
            binding.editTextPriority.setText(priority, false);

        // Show success badge then auto-hide after 2.5 s
        binding.tvAiSuggestionBadge.setText("✅ Kategori & Prioritas diisi otomatis oleh AI");
        binding.tvAiSuggestionBadge.setVisibility(View.VISIBLE);
        aiDebounceHandler.postDelayed(
                () -> binding.tvAiSuggestionBadge.setVisibility(View.GONE),
                2500L);
    }

    // ============================================================
    // AI SUBTASK GENERATOR
    // ============================================================

    /**
     * Reads the current title & notes, calls the Groq API, and displays
     * 3-5 actionable subtasks in tvSubtaskResult.
     */
    private void generateSubtasks() {
        String title = binding.editTextTitle.getText() != null
                ? binding.editTextTitle.getText().toString().trim()
                : "";
        String notes = binding.editTextNotes.getText() != null
                ? binding.editTextNotes.getText().toString().trim()
                : "";

        if (title.isEmpty()) {
            Toast.makeText(this, "Isi judul tugas terlebih dahulu.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        binding.progressBarAi.setVisibility(View.VISIBLE);
        binding.btnGenerateSubtasks.setEnabled(false);
        binding.tvSubtaskResult.setVisibility(View.GONE);
        binding.btnSaveSubtasks.setVisibility(View.GONE);
        generatedSubtasks = null;

        // Build prompt
        String prompt = "Break down the following task into 3 to 5 short, clear, and actionable subtasks. "
                + "Return ONLY a numbered list (e.g. 1. ...), nothing else. No intro, no outro.\n\n"
                + "Task Title: " + title + "\n"
                + (notes.isEmpty() ? "" : "Description: " + notes);

        // Build JSON request body
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content",
                    "You are a productivity assistant. Your sole job is to break tasks into concise, numbered subtasks.");
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);

            jsonBody.put("messages", messages);
        } catch (Exception e) {
            Log.e("AISubtask", "JSON build error", e);
            resetAiLoadingState();
            return;
        }

        RequestBody body = RequestBody.create(
                jsonBody.toString(),
                MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        aiHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("AISubtask", "Network error", e);
                runOnUiThread(() -> {
                    resetAiLoadingState();
                    Toast.makeText(DetailTaskActivity.this,
                            "Gagal terhubung. Cek koneksi internet.", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body() != null ? response.body().string() : "";
                runOnUiThread(() -> {
                    resetAiLoadingState();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String text = jsonResponse
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            generatedSubtasks = text.trim();
                            binding.tvSubtaskResult.setText(generatedSubtasks);
                            binding.tvSubtaskResult.setVisibility(View.VISIBLE);
                            binding.btnSaveSubtasks.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            Log.e("AISubtask", "Parse error", e);
                            Toast.makeText(DetailTaskActivity.this,
                                    "Gagal memproses respons AI.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.e("AISubtask", "Server error: " + responseData);
                        Toast.makeText(DetailTaskActivity.this,
                                "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    /**
     * Re-enables the generate button and hides the spinner after an API call
     * finishes.
     */
    private void resetAiLoadingState() {
        binding.progressBarAi.setVisibility(View.GONE);
        binding.btnGenerateSubtasks.setEnabled(true);
    }

    /**
     * Appends the generated subtasks to the notes field and persists
     * the change to Room DB (only when editing an existing task).
     */
    private void saveSubtasksToNotes() {
        if (generatedSubtasks == null || generatedSubtasks.isEmpty())
            return;

        String existingNotes = binding.editTextNotes.getText() != null
                ? binding.editTextNotes.getText().toString()
                : "";
        String updatedNotes = existingNotes.isEmpty()
                ? "--- AI Subtasks ---\n" + generatedSubtasks
                : existingNotes + "\n\n--- AI Subtasks ---\n" + generatedSubtasks;

        binding.editTextNotes.setText(updatedNotes);

        // Persist immediately if editing an existing task
        if (currentTask != null) {
            currentTask.setNotes(updatedNotes);
            taskViewModel.update(currentTask);
            Toast.makeText(this, "Subtask disimpan ke catatan!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Subtask ditambahkan ke catatan. Tekan Simpan untuk menyimpan tugas.",
                    Toast.LENGTH_LONG).show();
        }

        binding.btnSaveSubtasks.setVisibility(View.GONE);
    }

    private void loadTaskData(String id) {
        taskViewModel.getTaskById(id).observe(this, task -> {
            if (task != null) {
                currentTask = task;
                binding.editTextTitle.setText(task.getTitle());
                binding.editTextMataKuliah.setText(task.getMataKuliah());
                binding.editTextDeadline.setText(task.getDeadline());
                binding.editTextNotes.setText(task.getNotes());
                binding.editTextPriority.setText(task.getPriority(), false);
                binding.editTextCategory.setText(task.getCategory(), false);
                binding.switchReminder.setChecked(task.isReminderActive());
                if (task.getReminderTime() > 0) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    binding.editTextTime.setText(timeFormat.format(task.getReminderTime()));
                    alarmCalendar.setTimeInMillis(task.getReminderTime());
                }

                if (task.getAttachmentPath() != null && !task.getAttachmentPath().isEmpty()) {
                    cloudFileUrl = task.getAttachmentPath();
                    showAttachmentInfo("Lihat File Drive", false);
                }
                
                loadSubtasksFromJson(task.getSubtasksJson());
            }
        });
    }

    private void saveTaskToDatabase(String finalAttachmentUrl) {
        boolean isReminderActive = binding.switchReminder.isChecked();

        long reminderTime = isReminderActive ? alarmCalendar.getTimeInMillis() : 0;

        if (isReminderActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }
        }
        // Cek Izin Notifikasi (Android 13+)
        if (isReminderActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.POST_NOTIFICATIONS }, 101);
                return;
            }
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getUid() : "";

        Task taskToSave;
        if (taskId == null) {
            taskToSave = new Task(
                    binding.editTextTitle.getText().toString().trim(),
                    binding.editTextMataKuliah.getText().toString(),
                    binding.editTextDeadline.getText().toString(),
                    binding.editTextNotes.getText().toString(),
                    binding.editTextPriority.getText().toString().isEmpty() ? "Sedang"
                            : binding.editTextPriority.getText().toString(),
                    binding.editTextCategory.getText().toString().isEmpty() ? "Pribadi"
                            : binding.editTextCategory.getText().toString(),
                    false,
                    reminderTime, // Kita hanya perlu menyimpan waktu UTAMA ini
                    isReminderActive,
                    finalAttachmentUrl);
            taskToSave.setUserId(currentUserId);
            taskToSave.setSubtasksJson(getSubtasksAsJson());
            taskViewModel.insert(taskToSave);
        } else {
            if (currentTask != null) {
                currentTask.setTitle(binding.editTextTitle.getText().toString().trim());
                currentTask.setMataKuliah(binding.editTextMataKuliah.getText().toString());
                currentTask.setDeadline(binding.editTextDeadline.getText().toString());
                currentTask.setNotes(binding.editTextNotes.getText().toString());
                currentTask.setPriority(binding.editTextPriority.getText().toString());
                currentTask.setCategory(binding.editTextCategory.getText().toString());

                currentTask.setReminderTime(reminderTime);
                currentTask.setReminderActive(isReminderActive);

                currentTask.setAttachmentPath(finalAttachmentUrl);
                currentTask.setUserId(currentUserId);
                currentTask.setSubtasksJson(getSubtasksAsJson());
                taskViewModel.update(currentTask);
                taskToSave = currentTask;
            } else {
                return;
            }
        }

        if (isReminderActive)
            ReminderHelper.setReminder(this, taskToSave);
        else
            ReminderHelper.cancelReminder(this, taskToSave);

        String msg = isReminderActive ? "Tugas berhasil disimpan dengan pengingat bertahap." : "Tugas disimpan!";
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = { "image/*", "application/pdf" };
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void showAttachmentInfo(String text, boolean isCloud) {
        binding.layoutAttachmentInfo.setVisibility(View.VISIBLE);
        binding.tvAttachmentName.setText(text);
        if (isCloud) {
            binding.tvAttachmentName.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        } else {
            binding.tvAttachmentName.setTextColor(ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private void openAttachment() {
        if (cloudFileUrl != null && !cloudFileUrl.isEmpty()) {
            if (cloudFileUrl.startsWith("googledrive://")) {
                String fileId = cloudFileUrl.replace("googledrive://", "");
                String webUrl = "https://drive.google.com/file/d/" + fileId + "/view?usp=drivesdk";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(webUrl));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Tidak ada aplikasi untuk membuka link ini", Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(cloudFileUrl));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (selectedLocalUri != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedLocalUri, getContentResolver().getType(selectedLocalUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Tidak bisa membuka preview file lokal", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "File belum dipilih", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Tugas")
                .setMessage("Apakah Anda yakin ingin menghapus tugas ini?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    if (currentTask != null) {
                        ReminderHelper.cancelReminder(this, currentTask);
                        taskViewModel.delete(currentTask);
                        Toast.makeText(this, "Tugas dihapus", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void showDatePickerDialog() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            alarmCalendar.set(Calendar.YEAR, year);
            alarmCalendar.set(Calendar.MONTH, month);
            alarmCalendar.set(Calendar.DAY_OF_MONTH, day);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            binding.editTextDeadline.setText(sdf.format(alarmCalendar.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    private void showTimePickerDialog() {
        Calendar c = Calendar.getInstance();
        int currentHour = c.get(Calendar.HOUR_OF_DAY);
        int currentMinute = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {

            Calendar tempCalendar = (Calendar) alarmCalendar.clone();
            tempCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            tempCalendar.set(Calendar.MINUTE, minute);
            tempCalendar.set(Calendar.SECOND, 0);

            if (tempCalendar.getTimeInMillis() < System.currentTimeMillis()) {
                Toast.makeText(this, "Alarm hanya dapat disetel untuk waktu yang akan datang.", Toast.LENGTH_SHORT)
                        .show();
            } else {

                alarmCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                alarmCalendar.set(Calendar.MINUTE, minute);
                alarmCalendar.set(Calendar.SECOND, 0);

                String timeFormat = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                binding.editTextTime.setText(timeFormat);
                binding.switchReminder.setChecked(true);
            }
        }, currentHour, currentMinute, true).show();
    }
}