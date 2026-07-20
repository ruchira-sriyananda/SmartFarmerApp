package com.example.smartfarmer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.ChatRoom;
import java.util.List;

public class ChatRoomAdapter extends RecyclerView.Adapter<ChatRoomAdapter.ViewHolder> {
    private List<ChatRoom> rooms;
    private OnChatRoomClickListener listener;

    public interface OnChatRoomClickListener {
        void onChatRoomClick(ChatRoom room);
    }

    public ChatRoomAdapter(List<ChatRoom> rooms, OnChatRoomClickListener listener) {
        this.rooms = rooms;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatRoom room = rooms.get(position);
        holder.tvName.setText(room.getName());
        holder.tvLastMsg.setText(room.getLastMessage());
        
        // Format time
        if (room.getLastMessageTime() != null && !room.getLastMessageTime().isEmpty()) {
            try {
                // Example format: 2024-07-11T12:35:00
                String timePart = room.getLastMessageTime().split("T")[1].substring(0, 5);
                holder.tvTime.setText(timePart);
            } catch (Exception e) {
                holder.tvTime.setText("");
            }
        } else {
            holder.tvTime.setText("");
        }
        
        if (room.isGroup()) {
            holder.ivRoom.setPadding(0, 0, 0, 0);
            holder.ivRoom.setBackgroundResource(R.drawable.bg_circle_button);
            holder.ivRoom.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    holder.itemView.getContext().getResources().getColor(R.color.light_blue)));
            
            loadProfileImage(room.getImageUrl(), holder.ivRoom, android.R.drawable.ic_menu_myplaces);
            holder.ivLock.setVisibility(View.GONE);
        } else {
            holder.ivRoom.setPadding(0, 0, 0, 0);
            holder.ivRoom.setBackground(null);
            loadProfileImage(room.getImageUrl(), holder.ivRoom, R.drawable.ic_person);
            holder.ivLock.setVisibility(View.VISIBLE);
        }
        
        holder.itemView.setOnClickListener(v -> listener.onChatRoomClick(room));
    }

    @Override
    public int getItemCount() {
        return rooms.size();
    }

    public void updateRooms(List<ChatRoom> newRooms) {
        this.rooms = newRooms;
        notifyDataSetChanged();
    }

    private void loadProfileImage(String imageStr, ImageView imageView, int placeholder) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(placeholder);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(placeholder)
                    .error(placeholder)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivRoom, ivLock;
        TextView tvName, tvLastMsg, tvTime;

        ViewHolder(View itemView) {
            super(itemView);
            ivRoom = itemView.findViewById(R.id.ivChatRoom);
            ivLock = itemView.findViewById(R.id.ivPrivateLock);
            tvName = itemView.findViewById(R.id.tvChatRoomName);
            tvLastMsg = itemView.findViewById(R.id.tvLastMessage);
            tvTime = itemView.findViewById(R.id.tvChatTime);
        }
    }
}
