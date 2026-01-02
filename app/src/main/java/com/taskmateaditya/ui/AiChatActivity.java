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
import com.taskmateaditya.R;
import com.taskmateaditya.data.ChatMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiChatActivity extends AppCompatActivity {

    // API Key & Config
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    // Views
    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnSend, btnBack;
    private ProgressBar progressBar;

    // View Tambahan untuk Tampilan Baru
    private LinearLayout layoutWelcome;
    private Chip chipIdea, chipSchedule, chipSummary;

    private ChatAdapter adapter;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        // 1. Inisialisasi Views
        initViews();

        // 2. Setup RecyclerView
        adapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // 3. Cek Status Kosong (Tampilkan Robot jika belum ada chat)
        checkEmptyState();

        // 4. Setup Tombol Kembali
        btnBack.setOnClickListener(v -> finish());

        // 5. Setup Network Client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        // 6. Setup Listener Tombol Kirim (Manual)
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            sendMessage(text);
        });

        // 7. Setup Logic Chips (Saran Cepat)
        setupChipListeners();

        // 8. Setup TextWatcher (Hilangkan Robot saat mulai mengetik)
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    layoutWelcome.setVisibility(View.GONE);
                } else if (adapter.getItemCount() == 0) {
                    // Jika teks dihapus habis dan belum ada chat, munculkan lagi
                    layoutWelcome.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void initViews() {
        rvChat = findViewById(R.id.recyclerViewChat);
        etInput = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnBack = findViewById(R.id.btnBack);
        progressBar = findViewById(R.id.progressBar);

        // View Baru
        layoutWelcome = findViewById(R.id.layoutWelcome);
        chipIdea = findViewById(R.id.chipIdea);
        chipSchedule = findViewById(R.id.chipSchedule);
        chipSummary = findViewById(R.id.chipSummary);
    }

    private void setupChipListeners() {
        // 🚀 MODIFIKASI: Mengirim teks pendek "Ide Tugas" saja
        chipIdea.setOnClickListener(v -> {
            sendMessage("Ide Tugas");
        });

        // 🚀 MODIFIKASI: Mengirim teks pendek "Buat Jadwal" saja
        chipSchedule.setOnClickListener(v -> {
            sendMessage("Buat Jadwal");
        });

        // "Ringkas Teks" tetap masuk input karena user perlu paste teks
        chipSummary.setOnClickListener(v -> {
            etInput.setText("Tolong ringkas teks berikut ini:\n\n");
            etInput.setSelection(etInput.getText().length());
            etInput.requestFocus(); // Fokus agar user siap paste
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

    // Method sendMessage menerima parameter String agar bisa dipanggil dari Chip
    private void sendMessage(String query) {
        if (query.isEmpty()) return;

        // Update UI: Sembunyikan Robot, Tampilkan Chat
        layoutWelcome.setVisibility(View.GONE);
        rvChat.setVisibility(View.VISIBLE);

        // Tampilkan pesan user ke layar
        adapter.addMessage(new ChatMessage(query, true));

        // Bersihkan kolom input (Penting jika dikirim via tombol manual)
        etInput.setText("");
        scrollToBottom();

        // Tampilkan loading
        progressBar.setVisibility(View.VISIBLE);

        // Panggil API Groq
        callGroqApi(query);
    }

    private void callGroqApi(String query) {
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            // System Prompt diperbarui agar AI mengerti perintah singkat "Ide Tugas"
            sysMsg.put("content", "Kamu adalah TaskMate AI. Jika user mengirim 'Ide Tugas', berikan 3-5 ide tugas kreatif untuk mahasiswa IT. Jika user mengirim 'Buat Jadwal', berikan contoh template jadwal belajar harian. Jawablah dengan ramah, singkat, dan gunakan emoji.");
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
                            adapter.addMessage(new ChatMessage("Maaf, terjadi kesalahan saat memproses jawaban.", false));
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