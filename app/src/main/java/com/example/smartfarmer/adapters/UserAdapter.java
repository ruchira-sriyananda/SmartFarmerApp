package com.example.smartfarmer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
    private List<User> users;
    private OnUserClickListener listener;
    private boolean isMultiSelect = false;
    private Set<String> selectedUserIds = new HashSet<>();

    public interface OnUserClickListener {
        void onUserClick(User user);
        default void onSelectionChanged(int count) {}
    }

    public UserAdapter(List<User> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    public void setMultiSelect(boolean multiSelect) {
        this.isMultiSelect = multiSelect;
        notifyDataSetChanged();
    }

    public Set<String> getSelectedUserIds() {
        return selectedUserIds;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        holder.tvName.setText(user.getFullName());
        holder.tvSub.setText(user.getEmail());

        loadProfileImage(user.getProfileImage(), holder.ivUser);

        if (isMultiSelect) {
            boolean isSelected = selectedUserIds.contains(user.getUserId());
            holder.ivCheck.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            holder.itemView.setBackgroundColor(isSelected ? 0x110000FF : android.graphics.Color.TRANSPARENT);
        } else {
            holder.ivCheck.setVisibility(View.GONE);
            holder.itemView.setBackgroundResource(0);
        }
        
        holder.itemView.setOnClickListener(v -> {
            if (isMultiSelect) {
                if (selectedUserIds.contains(user.getUserId())) {
                    selectedUserIds.remove(user.getUserId());
                } else {
                    selectedUserIds.add(user.getUserId());
                }
                notifyItemChanged(position);
                listener.onSelectionChanged(selectedUserIds.size());
            } else {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    private void loadProfileImage(String imageStr, ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivUser, ivCheck;
        TextView tvName, tvSub;

        ViewHolder(View itemView) {
            super(itemView);
            ivUser = itemView.findViewById(R.id.ivChatRoom);
            ivCheck = itemView.findViewById(R.id.ivSelectedCheck);
            tvName = itemView.findViewById(R.id.tvChatRoomName);
            tvSub = itemView.findViewById(R.id.tvLastMessage);
            itemView.findViewById(R.id.tvChatTime).setVisibility(View.GONE);
        }
    }
}
