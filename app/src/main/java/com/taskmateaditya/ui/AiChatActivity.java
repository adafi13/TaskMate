package com.taskmateaditya.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

    // Views sesuai XML
    private RecyclerView rvChat;
    private EditText etInput;
    private ImageButton btnSend;
    private ImageButton btnBack;
    private ProgressBar progressBar;

    private ChatAdapter adapter;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        // 1. Inisialisasi View dengan ID yang BENAR (Sesuai XML activity_ai_chat.xml)
        rvChat = findViewById(R.id.recyclerViewChat); // ID di XML: recyclerViewChat
        etInput = findViewById(R.id.etMessage);       // ID di XML: etMessage
        btnSend = findViewById(R.id.btnSend);         // Tipe ImageButton
        btnBack = findViewById(R.id.btnBack);         // Tombol kembali di header custom
        progressBar = findViewById(R.id.progressBar); // Loading bar

        // 2. Setup RecyclerView
        adapter = new ChatAdapter(); // Pastikan ChatAdapter sudah benar
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // Pesan Sambutan
        adapter.addMessage(new ChatMessage("Hai! 👋 Saya TaskMate AI. Apa yang bisa saya bantu agar harimu lebih mudah?", false));

        // 3. Setup Tombol Kembali (Karena pakai Header Custom)
        btnBack.setOnClickListener(v -> finish());

        // 4. Setup Network Client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String query = etInput.getText().toString().trim();
        if (query.isEmpty()) return;

        // Tampilkan pesan user
        adapter.addMessage(new ChatMessage(query, true));
        etInput.setText("");
        scrollToBottom();

        // Tampilkan loading
        progressBar.setVisibility(View.VISIBLE);

        // Buat JSON Body untuk Groq
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "Kamu asisten TaskMate. Jawab dalam Bahasa Indonesia yang ramah, singkat, dan membantu.");
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
                    adapter.addMessage(new ChatMessage("Gagal terhubung. Cek internet Anda.", false));
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
                            adapter.addMessage(new ChatMessage("Error memproses jawaban AI.", false));
                        }
                    } else {
                        adapter.addMessage(new ChatMessage("Error Server: " + response.code(), false));
                        Log.e("AiChat", "Error: " + responseData);
                    }
                    scrollToBottom();
                });
            }
        });
    }

    private void scrollToBottom() {
        rvChat.smoothScrollToPosition(adapter.getItemCount() - 1);
    }
}