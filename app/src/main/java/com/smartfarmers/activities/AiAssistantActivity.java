package com.smartfarmers.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.adapters.ChatAdapter;
import com.smartfarmers.models.ChatMessage;
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
import com.smartfarmers.models.AiMessageDao;
import com.smartfarmers.models.ChatDatabase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AiAssistantActivity extends BaseActivity {
    private RecyclerView rvChat;
    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private EditText etMessage;
    private MaterialButton btnSend;
    private SpinKitView progressBar;
    private final OkHttpClient client = new OkHttpClient();
    private AiMessageDao aiMessageDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // 1. Get your free key from https://console.groq.com/keys
    private static final String GROQ_API_KEY = "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";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_assistant);

        aiMessageDao = ChatDatabase.getInstance(this).aiMessageDao();
        initViews();
        loadChatHistory();
    }

    private void loadChatHistory() {
        executor.execute(() -> {
            List<ChatMessage> history = aiMessageDao.getAllMessages();
            runOnUiThread(() -> {
                if (history.isEmpty()) {
                    addMessage("Hello! I am your Farmer AI assistant. How can I help you with your crops today?", false, false);
                } else {
                    messages.addAll(history);
                    adapter.notifyDataSetChanged();
                    rvChat.scrollToPosition(messages.size() - 1);
                }
            });
        });
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
            addMessage("Please add your Groq API Key in AiAssistantActivity.java to start chatting!", false, false);
            return;
        }

        addMessage(text, true);
        etMessage.setText("");
        
        callGroqApi();
    }

    private void addMessage(String content, boolean isUser) {
        addMessage(content, isUser, true);
    }

    private void addMessage(String content, boolean isUser, boolean saveToDb) {
        ChatMessage message = new ChatMessage(content, isUser);
        runOnUiThread(() -> {
            messages.add(message);
            adapter.notifyItemInserted(messages.size() - 1);
            rvChat.scrollToPosition(messages.size() - 1);
        });

        if (saveToDb) {
            executor.execute(() -> aiMessageDao.insert(message));
        }
    }

    private void callGroqApi() {
        progressBar.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        try {
            JSONObject json = new JSONObject();
            // Switched to the high-performance successor model: openai/gpt-oss-120b
            // This avoids the 404 error and provides the best experience.
            json.put("model", "openai/gpt-oss-120b");
            
            JSONArray messagesArr = new JSONArray();
            
            // System instruction
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are an expert agricultural assistant. Provide clear, direct, and highly accurate farming advice. " +
                    "Start with a concise direct answer. Use bullet points for steps and headers for different sections. " +
                    "Use tables only when comparing items or showing technical data. " +
                    "Detect the language of the user's message and respond in that same language (e.g., Sinhala for Sinhala queries). " +
                    "Avoid unnecessary conversational filler and focus on practical, actionable steps for a farmer.");
            messagesArr.put(systemMsg);
            
            // Add conversation history (last 5 messages) for context
            // messages list already contains the message just added by addMessage(text, true)
            synchronized (messages) {
                int historyStart = Math.max(0, messages.size() - 6);
                for (int i = historyStart; i < messages.size(); i++) {
                    ChatMessage msg = messages.get(i);
                    JSONObject historyMsg = new JSONObject();
                    historyMsg.put("role", msg.isUser() ? "user" : "assistant");
                    historyMsg.put("content", msg.getContent());
                    messagesArr.put(historyMsg);
                }
            }
            
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
                        android.util.Log.e("GroqAPI", "Error Response: " + responseBody);
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
            addMessage(message, false, false);
        });
    }
}
