package com.example.smartfarmer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.ChatAdapter;
import com.example.smartfarmer.models.ChatMessage;
import com.github.ybq.android.spinkit.SpinKitView;
import com.google.android.material.button.MaterialButton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AiAssistantActivity extends BaseActivity {
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private EditText etMessage;
    private MaterialButton btnSend;
    private SpinKitView progressBar;
    private final OkHttpClient client = new OkHttpClient();

    // 1. Get your free key from https://console.groq.com/keys
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        initViews();
        
        // Welcome message
        addMessage("Hello! I am your Farmer AI assistant powered by Llama 3. How can I help you with your crops today?", false);
    }

    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarAi);
        toolbar.setNavigationOnClickListener(v -> finish());

        rvChat = findViewById(R.id.rvChat);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        progressBar = findViewById(R.id.pbAi);

        rvChat.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatAdapter(messages);
        rvChat.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        if (GROQ_API_KEY.equals("PASTE_YOUR_GROQ_KEY_HERE")) {
            addMessage("Please add your Groq API Key in AiAssistantActivity.java to start chatting!", false);
            return;
        }

        addMessage(text, true);
        etMessage.setText("");
        
        callGroqApi(text);
    }

    private void addMessage(String content, boolean isUser) {
        runOnUiThread(() -> {
            messages.add(new ChatMessage(content, isUser));
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });
    }

    private void callGroqApi(String userPrompt) {
        progressBar.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        try {
            JSONObject json = new JSONObject();
            json.put("model", "llama-3.3-70b-versatile");
            
            JSONArray messagesArr = new JSONArray();
            
            // System instruction
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are an expert agricultural assistant. Provide helpful farming advice.");
            messagesArr.put(systemMsg);
            
            // User prompt
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messagesArr.put(userMsg);
            
            json.put("messages", messagesArr);

            RequestBody body = RequestBody.create(
                    json.toString(), MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(GROQ_URL)
                    .addHeader("Authorization", "Bearer " + GROQ_API_KEY)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    showError("Connection failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resJson = new JSONObject(responseBody);
                            String aiText = resJson.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            addMessage(aiText, false);
                        } catch (Exception e) {
                            showError("Error parsing response");
                        }
                    } else {
                        showError("API Error: " + response.code());
                    }
                    runOnUiThread(() -> {
                        progressBar.setVisibility(View.GONE);
                        btnSend.setEnabled(true);
                    });
                }
            });
        } catch (Exception e) {
            showError("Request failed");
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            btnSend.setEnabled(true);
            addMessage(message, false);
        });
    }
}
