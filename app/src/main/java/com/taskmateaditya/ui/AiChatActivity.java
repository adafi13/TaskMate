package com.taskmateaditya.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.ChatMessage;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskDatabase;
import com.taskmateaditya.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiChatActivity extends AppCompatActivity {

    public static final String EXTRA_DOC_TEXT = "EXTRA_DOC_TEXT";

    private static final String GROQ_API_KEY = BuildConfig.GROQ_API_KEY;
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnSend, btnBack;
    private ProgressBar progressBar;

    private LinearLayout layoutWelcome;
    private Chip chipIdea, chipSchedule, chipSummary;

    private ChatAdapter adapter;
    private OkHttpClient client;
    private String docContextText = null;

    private TaskDatabase taskDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        initViews();

        adapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        checkEmptyState();

        btnBack.setOnClickListener(v -> finish());

        client = com.taskmateaditya.TaskMateApplication.getInstance().getHttpClient();

        taskDatabase = TaskDatabase.getDatabase(this);

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            sendMessage(text);
        });

        setupChipListeners();

        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    layoutWelcome.setVisibility(View.GONE);
                } else if (adapter.getItemCount() == 0) {
                    layoutWelcome.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // Handle Document context if passed
        if (getIntent().hasExtra(EXTRA_DOC_TEXT)) {
            docContextText = getIntent().getStringExtra(EXTRA_DOC_TEXT);
            layoutWelcome.setVisibility(View.GONE);
            rvChat.setVisibility(View.VISIBLE);
            adapter.addMessage(new ChatMessage("Sken berhasil! Saya sudah membaca dokumen Anda. Apa yang ingin Anda tanyakan atau ringkas?", false));
        }
    }

    private void initViews() {
        rvChat = findViewById(R.id.recyclerViewChat);
        etInput = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);

        layoutWelcome = findViewById(R.id.layoutWelcome);
        chipIdea = findViewById(R.id.chipIdea);
        chipSchedule = findViewById(R.id.chipSchedule);
        chipSummary = findViewById(R.id.chipSummary);
    }

    private void setupChipListeners() {
        chipIdea.setOnClickListener(v -> {
            sendMessage("Ide Tugas");
        });

        chipSchedule.setOnClickListener(v -> {
            sendMessage("Buat Jadwal");
        });

        chipSummary.setOnClickListener(v -> {
            etInput.setText("Tolong ringkas teks berikut ini:\n\n");
            etInput.setSelection(etInput.getText().length());
            etInput.requestFocus();
            layoutWelcome.setVisibility(View.GONE);
        });
    }

    private void checkEmptyState() {
        if (adapter.getItemCount() == 0) {
            layoutWelcome.setVisibility(View.VISIBLE);
            rvChat.setVisibility(View.GONE);
        } else {
            layoutWelcome.setVisibility(View.GONE);
            rvChat.setVisibility(View.VISIBLE);
        }
    }

    private void sendMessage(String query) {
        if (query.isEmpty())
            return;

        layoutWelcome.setVisibility(View.GONE);
        rvChat.setVisibility(View.VISIBLE);

        adapter.addMessage(new ChatMessage(query, true));

        etInput.setText("");
        scrollToBottom();

        progressBar.setVisibility(View.VISIBLE);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        final String userId = (currentUser != null) ? currentUser.getUid() : null;

        TaskDatabase.databaseWriteExecutor.execute(() -> {

            List<Task> pendingTasks = null;
            if (userId != null) {
                try {
                    pendingTasks = taskDatabase.taskDao().getPendingTasksForUser(userId);
                } catch (Exception e) {
                    Log.e("AiChat", "Failed to fetch tasks from Room", e);
                }
            }

            final String taskSummary = buildTaskSummary(pendingTasks);

            runOnUiThread(() -> callGroqApi(query, taskSummary));
        });
    }

    private String buildTaskSummary(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "No pending tasks.";
        }

        StringBuilder sb = new StringBuilder("Current Tasks: ");
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);

            String title = task.getTitle() != null ? task.getTitle() : "Untitled";
            String priority = task.getPriority() != null ? task.getPriority() : "No priority";
            String deadline = (task.getDeadline() != null && !task.getDeadline().isEmpty())
                    ? "Due " + task.getDeadline()
                    : "No due date";

            sb.append(title)
                    .append(" (")
                    .append(priority)
                    .append(", ")
                    .append(deadline)
                    .append(")");

            if (i < tasks.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private void callGroqApi(String query, String taskSummary) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            
            String promptContext = "You are TaskMate AI. You have access to the user's current tasks: " + taskSummary + ". ";
            if (docContextText != null && !docContextText.isEmpty()) {
                promptContext += "The user also scanned a document with this text: \"" + docContextText + "\". " +
                                 "Prioritize answering based strictly on the document content if the user asks about it. ";
            }
            promptContext += "Answer strictly based on provided data. Keep it short and conversational.";
            
            sysMsg.put("content", promptContext);
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", query);
            messages.put(userMsg);

            jsonBody.put("messages", messages);
        } catch (Exception e) {
            e.printStackTrace();
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(GROQ_URL)
                .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    adapter.addMessage(new ChatMessage("Gagal terhubung. Cek koneksi internet Anda.", false));
                    scrollToBottom();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String text = jsonResponse.getJSONArray("choices")
                                    .getJSONObject(0).getJSONObject("message").getString("content");
                            adapter.addMessage(new ChatMessage(text.trim(), false));
                        } catch (Exception e) {
                            adapter.addMessage(
                                    new ChatMessage("Maaf, terjadi kesalahan saat memproses jawaban.", false));
                        }
                    } else {
                        adapter.addMessage(new ChatMessage("Server Error: " + response.code(), false));
                        Log.e("AiChat", "Error Response: " + responseData);
                    }
                    scrollToBottom();
                });
            }
        });
    }

    private void scrollToBottom() {
        if (adapter.getItemCount() > 0) {
            rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }
}