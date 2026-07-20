package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.MessageAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.Message;
import com.example.smartfarmer.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends BaseActivity {
    private RecyclerView rvMessages;
    private MessageAdapter adapter;
    private EditText etMessage;
    private ImageButton btnSend;
    private String roomId, otherUserId, roomName, adminId;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private com.example.smartfarmer.models.ChatDatabase localDb;
    private android.widget.ImageView ivPartner;
    private android.widget.TextView tvPartnerName;
    private com.github.ybq.android.spinkit.SpinKitView pbLoading;
    private android.widget.ImageButton btnAttach;
    private final android.os.Handler pollingHandler = new android.os.Handler();
    private static final int PICK_MEDIA_REQUEST = 501;
    private Runnable pollingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        roomId = getIntent().getStringExtra("room_id");
        otherUserId = getIntent().getStringExtra("other_user_id"); 
        roomName = getIntent().getStringExtra("room_name");
        adminId = getIntent().getStringExtra("admin_id");

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);
        localDb = com.example.smartfarmer.models.ChatDatabase.getInstance(this);

        initViews();
        setupRecyclerView();
        updateToolbarInfo();
        startPolling();

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarChat);
        toolbar.setNavigationOnClickListener(v -> finish());
        
        View headerLayout = findViewById(R.id.layoutChatHeader);

        if (roomId != null) {
            // Group Chat: Clicking the name opens group details
            headerLayout.setOnClickListener(v -> {
                Intent intent = new Intent(this, GroupDetailsActivity.class);
                intent.putExtra("room_id", roomId);
                intent.putExtra("room_name", roomName);
                intent.putExtra("admin_id", adminId);
                startActivity(intent);
            });

            toolbar.inflateMenu(R.menu.chat_menu);
            toolbar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_group_info) {
                    Intent intent = new Intent(this, GroupDetailsActivity.class);
                    intent.putExtra("room_id", roomId);
                    intent.putExtra("room_name", roomName);
                    intent.putExtra("admin_id", adminId);
                    startActivity(intent);
                    return true;
                }
                return false;
            });
        }
    }

    private void startPolling() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchMessages();
                pollingHandler.postDelayed(this, 1000); // 1-second high-speed polling
            }
        };
        pollingHandler.post(pollingRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateToolbarInfo();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pollingHandler != null) pollingHandler.removeCallbacks(pollingRunnable);
    }

    private void initViews() {
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etChatMessage);
        btnSend = findViewById(R.id.btnSendChatMessage);
        btnAttach = findViewById(R.id.btnAttachMedia);
        ivPartner = findViewById(R.id.ivChatPartner);
        tvPartnerName = findViewById(R.id.tvChatPartnerName);
        pbLoading = findViewById(R.id.pbChatLoading);

        btnSend.setOnClickListener(v -> sendMessage());
        btnAttach.setOnClickListener(v -> selectMedia());
    }

    private void selectMedia() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        String[] mimeTypes = {"image/*", "video/*"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        startActivityForResult(Intent.createChooser(intent, "Select Media"), PICK_MEDIA_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleMediaSelection(data.getData());
        }
    }

    private void handleMediaSelection(android.net.Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        String type = "image";
        if (mimeType != null && mimeType.startsWith("video")) {
            type = "video";
        }
        
        final String finalType = type;
        pbLoading.setVisibility(View.VISIBLE);
        
        // Convert to Base64 (simplification for this project's style)
        new Thread(() -> {
            try {
                java.io.InputStream inputStream = getContentResolver().openInputStream(uri);
                byte[] bytes = new byte[inputStream.available()];
                inputStream.read(bytes);
                String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
                
                runOnUiThread(() -> sendMediaMessage(finalType, base64));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to process media", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void sendMediaMessage(String type, String base64) {
        try {
            JSONObject json = new JSONObject();
            json.put("sender_id", sessionManager.getUserId());
            json.put("message_type", type);
            json.put("attachment_url", base64); // Storing as base64 in this column for simplicity
            json.put("message_text", ""); // Add empty text to satisfy NOT NULL if exists
            
            // Add required timestamp
            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            json.put("sent_at", currentTime);
            
            if (otherUserId != null) {
                json.put("receiver_id", otherUserId);
            } else if (roomId != null) {
                json.put("receiver_id", roomId);
            }

            supabaseAuth.sendDirectMessage(json, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        fetchMessages();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(ChatActivity.this, "Upload failed: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateToolbarInfo() {
        if (otherUserId != null) {
            // Personal Chat: Fetch real name and image
            supabaseAuth.getUserProfile(otherUserId, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String json) {
                    try {
                        JSONObject user = new JSONObject(json);
                        String name = user.optString("full_name", roomName);
                        String image = user.optString("profile_image", "");
                        
                        runOnUiThread(() -> {
                            tvPartnerName.setText(name);
                            loadChatPartnerImage(image, ivPartner, R.drawable.ic_person);
                        });
                    } catch (Exception e) {}
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> tvPartnerName.setText(roomName));
                }
            });
        } else {
            // Group Chat: Show group name and image if available
            tvPartnerName.setText(roomName);
            supabaseAuth.fetchChatRoomDetails(roomId, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String json) {
                    try {
                        JSONObject room = new JSONObject(json);
                        String image = room.optString("profile_image", 
                                       room.optString("image_url", 
                                       room.optString("avatar_url", 
                                       room.optString("image", ""))));

                        runOnUiThread(() -> {
                            loadChatPartnerImage(image, ivPartner, android.R.drawable.ic_menu_myplaces);
                        });
                    } catch (Exception e) {}
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        loadChatPartnerImage(null, ivPartner, android.R.drawable.ic_menu_myplaces);
                    });
                }
            });
        }
    }

    private void loadChatPartnerImage(String imageStr, android.widget.ImageView imageView, int placeholder) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(placeholder);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageStr)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(imageBytes)
                        .placeholder(placeholder)
                        .error(placeholder)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(placeholder);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter(new ArrayList<>(), sessionManager.getUserId(), getChatSeed());
        if (roomId != null) {
            adapter.setGroupChat(true);
        }
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }

    private void fetchMessages() {
        if (adapter.getItemCount() == 0) pbLoading.setVisibility(View.VISIBLE);
        // Load from local DB first for instant UI
        new Thread(() -> {
            try {
                List<com.example.smartfarmer.models.MessageEntity> localMsgs;
                if (otherUserId != null && !otherUserId.isEmpty()) {
                    localMsgs = localDb.messageDao().getDirectMessages(sessionManager.getUserId(), otherUserId);
                } else {
                    localMsgs = localDb.messageDao().getRoomMessages(roomId);
                }
                
                if (!localMsgs.isEmpty()) {
                    runOnUiThread(() -> {
                        List<Message> list = new ArrayList<>();
                        for (com.example.smartfarmer.models.MessageEntity entity : localMsgs) {
                            Message m = new Message();
                            m.setMessageId(entity.getMessageId());
                            m.setMessageText(entity.getMessageText());
                            m.setSenderId(entity.getSenderId());
                            m.setReceiverId(entity.getReceiverId());
                            m.setSentAt(entity.getSentAt());
                            m.setMessageType(entity.getMessageType());
                            m.setAttachmentUrl(entity.getAttachmentUrl());
                            list.add(m);
                        }
                        if (list.size() > adapter.getItemCount()) {
                            adapter.updateMessages(list);
                            rvMessages.scrollToPosition(list.size() - 1);
                        }
                    });
                }
            } catch (android.database.sqlite.SQLiteBlobTooBigException e) {
                // Ignore and let fetchMessages load fresh data
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback callback = new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    List<Message> list = new ArrayList<>();
                    List<com.example.smartfarmer.models.MessageEntity> entities = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        String rId = obj.optString("receiver_id", "");
                        // Safeguard: If in personal chat, don't show group messages
                        if (otherUserId != null && rId.startsWith("GRP-")) continue;

                        Message m = new Message();
                        m.setMessageId(obj.getString("message_id"));
                        m.setMessageText(obj.optString("message_text", ""));
                        m.setSenderId(obj.getString("sender_id"));
                        m.setReceiverId(obj.optString("receiver_id", ""));
                        m.setMessageType(obj.optString("message_type", "text"));
                        m.setAttachmentUrl(obj.optString("attachment_url", ""));
                        m.setSentAt(obj.optString("sent_at", ""));
                        list.add(m);

                        entities.add(new com.example.smartfarmer.models.MessageEntity(
                                m.getMessageId(), m.getSenderId(), m.getReceiverId(), m.getMessageText(), m.getSentAt(),
                                m.getMessageType(), m.getAttachmentUrl()
                        ));
                    }

                    // Complete local sync: Remove old, insert current state
                    new Thread(() -> {
                        if (otherUserId != null) {
                            localDb.messageDao().deleteDirectMessages(sessionManager.getUserId(), otherUserId);
                        } else {
                            localDb.messageDao().deleteRoomMessages(roomId);
                        }
                        for (com.example.smartfarmer.models.MessageEntity entity : entities) {
                            localDb.messageDao().insertMessage(entity);
                        }
                    }).start();

                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        if (list.size() != adapter.getItemCount() || hasDataChanged(list)) {
                            adapter.updateMessages(list);
                            rvMessages.scrollToPosition(list.size() - 1);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
                    e.printStackTrace();
                }
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
            }
        };

        if (otherUserId != null && !otherUserId.isEmpty()) {
            // Direct 1-to-1 Chat: Only sender/receiver see these messages
            supabaseAuth.fetchDirectMessages(sessionManager.getUserId(), otherUserId, callback);
        } else if (roomId != null) {
            // Group Chat
            supabaseAuth.fetchRoomMessages(roomId, callback);
        }
    }

    private boolean hasDataChanged(List<Message> newList) {
        if (newList.size() != adapter.getItemCount()) return true;
        List<Message> oldList = adapter.getMessages();
        for (int i = 0; i < newList.size(); i++) {
            if (!newList.get(i).getMessageId().equals(oldList.get(i).getMessageId()) ||
                !newList.get(i).getMessageText().equals(oldList.get(i).getMessageText())) {
                return true;
            }
        }
        return false;
    }

    private String getChatSeed() {
        if (otherUserId != null && !otherUserId.isEmpty()) {
            return com.example.smartfarmer.utils.EncryptionUtils.getConversationSeed(sessionManager.getUserId(), otherUserId);
        }
        return roomId;
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        // Auto-join group if messaging for the first time
        if (roomId != null && roomId.startsWith("GRP-")) {
            supabaseAuth.addParticipant(roomId, sessionManager.getUserId(), "joined", "member", new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {
                    performSendMessage(text);
                }
                @Override public void onError(String error) {
                    // Try to send anyway, maybe already joined but local state unknown
                    performSendMessage(text);
                }
            });
        } else {
            performSendMessage(text);
        }
    }

    private void performSendMessage(String text) {
        // HIGH SECURITY: Conversation-Specific AES End-to-End Encryption
        String seed = getChatSeed();
        String encryptedText = com.example.smartfarmer.utils.EncryptionUtils.encrypt(text, seed);

        try {
            JSONObject json = new JSONObject();
            json.put("sender_id", sessionManager.getUserId());
            json.put("message_text", encryptedText);
            json.put("message_type", "text"); // Required information
            
            // Generate a temporary timestamp for local and potential remote use
            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            json.put("sent_at", currentTime);
            
            if (otherUserId != null) {
                json.put("receiver_id", otherUserId);
            } else if (roomId != null) {
                json.put("receiver_id", roomId);
            }

            // UI Speed: Optimistic update
            Message localMsg = new Message();
            localMsg.setSenderId(sessionManager.getUserId());
            localMsg.setMessageText(encryptedText);
            localMsg.setSentAt(currentTime);
            
            runOnUiThread(() -> {
                adapter.addMessage(localMsg);
                rvMessages.scrollToPosition(adapter.getItemCount() - 1);
                etMessage.setText("");
            });

            supabaseAuth.sendDirectMessage(json, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    // Notify Receiver (only for personal chats)
                    if (otherUserId != null && !otherUserId.isEmpty()) {
                        supabaseAuth.createNotification(
                            otherUserId,
                            getString(R.string.notification_community_title),
                            "New message from " + sessionManager.getUserName(),
                            "chat",
                            sessionManager.getUserId(),
                            new SupabaseAuthHelper.AuthCallback() {
                                @Override public void onSuccess(String data) {}
                                @Override public void onError(String error) {}
                            }
                        );
                    }
                    runOnUiThread(() -> fetchMessages());
                }

                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(ChatActivity.this, "Security Error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
