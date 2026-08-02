package com.smartfarmers.adapters;

import android.content.Intent;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.activities.FullscreenImageActivity;
import com.smartfarmers.models.Message;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private List<Message> messages;
    private String currentUserId;
    private String encryptionSeed;
    private boolean isGroupChat = false;
    private com.smartfarmers.auth.SupabaseAuthHelper supabaseAuth;

    public MessageAdapter(List<Message> messages, String currentUserId, String encryptionSeed) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.encryptionSeed = encryptionSeed;
        this.supabaseAuth = new com.smartfarmers.auth.SupabaseAuthHelper();
    }

    public void setGroupChat(boolean groupChat) {
        this.isGroupChat = groupChat;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (messages.get(position).getSenderId().equals(currentUserId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message msg = messages.get(position);
        
        if (holder instanceof SentViewHolder) {
            bindSentMessage((SentViewHolder) holder, msg);
        } else {
            bindReceivedMessage((ReceivedViewHolder) holder, msg);
        }
    }

    private void bindSentMessage(SentViewHolder holder, Message msg) {
        // We assume msg.getMessageText() might already be decrypted if activity did it,
        // but for safety we can try decrypting here too if it looks like encrypted data.
        String text = msg.getMessageText();
        String decryptedText = com.smartfarmers.utils.EncryptionUtils.decrypt(text, encryptionSeed);
        holder.tvText.setText(decryptedText);
        holder.tvText.setVisibility(decryptedText.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvTime.setText(formatTime(msg.getSentAt()));
        
        handleMediaDisplay(holder.ivMedia, msg);

        holder.itemView.setOnLongClickListener(v -> {
            showEditDeletePopup(v, msg);
            return true;
        });
    }

    private void showEditDeletePopup(View v, Message msg) {
        if (msg.getMessageId() == null || msg.getMessageId().isEmpty() || msg.getMessageId().equalsIgnoreCase("null")) {
            Toast.makeText(v.getContext(), "Message not synced yet. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        PopupMenu popup = new PopupMenu(v.getContext(), v);
        
        if ("text".equals(msg.getMessageType())) {
            popup.getMenu().add(0, 1, 0, "Edit");
        }
        
        SpannableString deleteText = new SpannableString("Delete");
        deleteText.setSpan(new ForegroundColorSpan(ContextCompat.getColor(v.getContext(), R.color.red)), 0, deleteText.length(), 0);
        popup.getMenu().add(0, 2, 1, deleteText);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showEditDialog(v.getContext(), msg);
            } else if (item.getItemId() == 2) {
                showDeleteConfirm(v.getContext(), msg);
            }
            return true;
        });
        popup.show();
    }

    private void showEditDialog(android.content.Context context, Message msg) {
        EditText input = new EditText(context);
        String decryptedText = com.smartfarmers.utils.EncryptionUtils.decrypt(msg.getMessageText(), encryptionSeed);
        input.setText(decryptedText);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(context)
                .setTitle("Edit Message")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        supabaseAuth.updateMessage(msg.getMessageId(), newText, encryptionSeed, new com.smartfarmers.auth.SupabaseAuthHelper.AuthCallback() {
                            @Override
                            public void onSuccess(String data) {
                                ((android.app.Activity)context).runOnUiThread(() -> {
                                    msg.setMessageText(com.smartfarmers.utils.EncryptionUtils.encrypt(newText, encryptionSeed));
                                    notifyDataSetChanged();
                                    Toast.makeText(context, "Message updated", Toast.LENGTH_SHORT).show();
                                });
                            }
                            @Override
                            public void onError(String error) {
                                ((android.app.Activity)context).runOnUiThread(() -> 
                                    Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show());
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirm(android.content.Context context, Message msg) {
        new AlertDialog.Builder(context)
                .setTitle("Delete Message")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    supabaseAuth.deleteMessage(msg.getMessageId(), new com.smartfarmers.auth.SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            ((android.app.Activity)context).runOnUiThread(() -> {
                                messages.remove(msg);
                                notifyDataSetChanged();
                                Toast.makeText(context, "Message deleted", Toast.LENGTH_SHORT).show();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            ((android.app.Activity)context).runOnUiThread(() -> 
                                Toast.makeText(context, "Error: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void bindReceivedMessage(ReceivedViewHolder holder, Message msg) {
        String text = msg.getMessageText();
        String decryptedText = com.smartfarmers.utils.EncryptionUtils.decrypt(text, encryptionSeed);
        holder.tvText.setText(decryptedText);
        holder.tvText.setVisibility(decryptedText.isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvTime.setText(formatTime(msg.getSentAt()));

        if (isGroupChat) {
            holder.tvSender.setVisibility(View.VISIBLE);
            holder.ivSenderProfile.setVisibility(View.VISIBLE);
            
            // Optimization: If sender name already exists in cache or previous message, use it.
            // For now, fetch from Supabase
            supabaseAuth.getUserProfile(msg.getSenderId(), new com.smartfarmers.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String json) {
                    try {
                        org.json.JSONObject user = new org.json.JSONObject(json);
                        String name = user.optString("full_name", "User");
                        String image = user.optString("profile_image", "");
                        
                        if (holder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                            ((android.app.Activity) holder.itemView.getContext()).runOnUiThread(() -> {
                                holder.tvSender.setText(name);
                                loadProfileImage(image, holder.ivSenderProfile);
                            });
                        }
                    } catch (Exception e) {}
                }
                @Override public void onError(String error) {}
            });
        } else {
            holder.tvSender.setVisibility(View.GONE);
            holder.ivSenderProfile.setVisibility(View.GONE);
        }
        
        handleMediaDisplay(holder.ivMedia, msg);
    }

    private void loadProfileImage(String imageStr, android.widget.ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_person);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
                        .load(imageBytes)
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.ic_person);
            }
        }
    }

    private void handleMediaDisplay(android.widget.ImageView ivMedia, Message msg) {
        if ("text".equals(msg.getMessageType()) || msg.getAttachmentUrl() == null || msg.getAttachmentUrl().isEmpty()) {
            ivMedia.setVisibility(View.GONE);
            ivMedia.setOnClickListener(null);
            return;
        }

        ivMedia.setVisibility(View.VISIBLE);
        if ("image".equals(msg.getMessageType())) {
            try {
                byte[] decodedString = android.util.Base64.decode(msg.getAttachmentUrl(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivMedia.setImageBitmap(bitmap);
                
                ivMedia.setOnClickListener(v -> {
                    Intent intent = new Intent(v.getContext(), FullscreenImageActivity.class);
                    intent.putExtra("image_data", msg.getAttachmentUrl());
                    v.getContext().startActivity(intent);
                });
            } catch (Exception e) {
                ivMedia.setImageResource(android.R.drawable.ic_menu_gallery);
                ivMedia.setOnClickListener(null);
            }
        } else if ("video".equals(msg.getMessageType())) {
            // Show video icon/thumbnail
            ivMedia.setImageResource(android.R.drawable.ic_media_play);
            ivMedia.setBackgroundColor(android.graphics.Color.BLACK);
            ivMedia.setOnClickListener(null);
        }
    }

    private String formatTime(String sentAt) {
        if (sentAt == null || sentAt.isEmpty()) return "";
        try {
            if (sentAt.contains("T")) {
                String[] parts = sentAt.split("T");
                return parts[0] + " " + parts[1].substring(0, 5);
            }
            return sentAt;
        } catch (Exception e) {
            return sentAt;
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message msg) {
        messages.add(msg);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessages(List<Message> newList) {
        this.messages = newList;
        notifyDataSetChanged();
    }

    public List<Message> getMessages() {
        return messages;
    }

    static class SentViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvText, tvTime;
        android.widget.ImageView ivMedia;
        SentViewHolder(View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageText);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
            ivMedia = itemView.findViewById(R.id.ivMessageMedia);
        }
    }

    static class ReceivedViewHolder extends RecyclerView.ViewHolder {
        android.widget.TextView tvText, tvTime, tvSender;
        android.widget.ImageView ivMedia, ivSenderProfile;
        ReceivedViewHolder(View itemView) {
            super(itemView);
            tvText = itemView.findViewById(R.id.tvMessageText);
            tvTime = itemView.findViewById(R.id.tvMessageTime);
            tvSender = itemView.findViewById(R.id.tvSenderName);
            ivMedia = itemView.findViewById(R.id.ivMessageMedia);
            ivSenderProfile = itemView.findViewById(R.id.ivSenderProfile);
        }
    }
}
