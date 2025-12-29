package com.taskmateaditya.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.taskmateaditya.R;
import com.taskmateaditya.data.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.*;

public class AiChatActivity extends AppCompatActivity {

    // 🔥 API Key Groq Anda (Pastikan benar!)
    private static final String GROQ_API_KEY = "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";

    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.1-8b-instant";

    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private EditText etInput;
    private FloatingActionButton btnSend;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        // Setup Toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Init Views
        rvChat = findViewById(R.id.rvChat);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);

        // Setup RecyclerView
        adapter = new ChatAdapter();
        rvChat.setLayoutManager(new LinearLayoutManager(this));
        rvChat.setAdapter(adapter);

        // Pesan Sambutan
        adapter.addMessage(new ChatMessage("Hai! 👋 Saya TaskMate AI. Apa yang bisa saya bantu agar harimu lebih mudah?", false));

        // Setup Network Client
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String query = etInput.getText().toString().trim();
        if (query.isEmpty()) return;

        // 1. Tampilkan pesan user
        adapter.addMessage(new ChatMessage(query, true));
        etInput.setText("");
        scrollToBottom();

        // 2. Tampilkan loading sementara
        adapter.addMessage(new ChatMessage("Sedang berpikir...", false));
        scrollToBottom();

        // 3. Kirim Request
        JSONObject jsonBody = new JSONObject();
        try {
            jsonBody.put("model", GROQ_MODEL);
            JSONArray messages = new JSONArray();

            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", "Kamu asisten TaskMate. Jawab singkat, padat, helpful.");
            messages.put(sysMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", query);
            messages.put(userMsg);

            jsonBody.put("messages", messages);
        } catch (Exception e) { e.printStackTrace(); }

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
                    adapter.removeLastMessage(); // Hapus "Sedang berpikir..."
                    adapter.addMessage(new ChatMessage("Gagal mengakses. Pastikan perangkat Anda tersambung ke jaringan internet.", false));
                    scrollToBottom();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String responseData = response.body().string();
                runOnUiThread(() -> {
                    adapter.removeLastMessage(); // Hapus loading
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseData);
                            String text = jsonResponse.getJSONArray("choices")
                                    .getJSONObject(0).getJSONObject("message").getString("content");
                            adapter.addMessage(new ChatMessage(text.trim(), false));
                        } catch (Exception e) {
                            adapter.addMessage(new ChatMessage("Error parsing data.", false));
                        }
                    } else {
                        adapter.addMessage(new ChatMessage("Error Server: " + response.code(), false));
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