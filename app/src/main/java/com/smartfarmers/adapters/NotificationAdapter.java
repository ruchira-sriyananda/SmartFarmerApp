package com.smartfarmers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.models.Notification;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private List<Notification> notifications;
    private OnNotificationClickListener listener;

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
        void onDeleteClick(Notification notification);
    }

    public NotificationAdapter(List<Notification> notifications, OnNotificationClickListener listener) {
        this.notifications = notifications;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.tvTitle.setText(notification.getTitle());
        holder.tvMessage.setText(notification.getMessage());
        holder.tvTime.setText(formatDate(notification.getCreatedAt()));
        
        holder.viewUnread.setVisibility(notification.isRead() ? View.GONE : View.VISIBLE);

        // Set Icon based on type
        int iconRes = R.drawable.ic_home; // Default
        int colorRes = R.color.ocean_blue;
        
        String type = notification.getType() != null ? notification.getType().toLowerCase() : "";
        switch (type) {
            case "community":
                iconRes = R.drawable.ic_community;
                colorRes = R.color.ocean_blue;
                break;
            case "chat":
                iconRes = R.drawable.ic_ai_chat; // Or a message icon if available
                colorRes = android.R.color.holo_green_dark;
                break;
            case "market":
                iconRes = R.drawable.ic_market;
                colorRes = android.R.color.holo_orange_dark;
                break;
            case "barter":
                iconRes = R.drawable.ic_barter;
                colorRes = android.R.color.holo_purple;
                break;
        }
        
        holder.ivIcon.setImageResource(iconRes);
        holder.ivIcon.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), colorRes)));
        
        holder.tvType.setText(type);
        holder.tvType.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(holder.itemView.getContext(), colorRes)));
        holder.tvType.setTextColor(android.graphics.Color.WHITE);
        
        holder.itemView.setOnClickListener(v -> listener.onNotificationClick(notification));
        holder.btnClear.setOnClickListener(v -> listener.onDeleteClick(notification));
    }

    private String formatDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "";
        try {
            // Supabase ISO format: 2024-03-20T10:30:00Z
            String cleanDate = rawDate.split("\\.")[0].replace("Z", "");
            java.text.SimpleDateFormat isoFormat = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            isoFormat.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = isoFormat.parse(cleanDate);
            
            if (date != null) {
                long diff = System.currentTimeMillis() - date.getTime();
                long minutes = diff / (1000 * 60);
                long hours = minutes / 60;
                long days = hours / 24;

                if (minutes < 1) return "Just now";
                if (minutes < 60) return minutes + "m ago";
                if (hours < 24) return hours + "h ago";
                if (days < 7) return days + "d ago";
                
                return new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(date);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rawDate;
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public void updateNotifications(List<Notification> newNotifications) {
        this.notifications = newNotifications;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvMessage, tvTime, tvType;
        View viewUnread;
        android.widget.ImageButton btnClear;
        com.google.android.material.imageview.ShapeableImageView ivIcon;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvNotificationTitle);
            tvType = itemView.findViewById(R.id.tvNotificationType);
            tvMessage = itemView.findViewById(R.id.tvNotificationMessage);
            tvTime = itemView.findViewById(R.id.tvNotificationTime);
            viewUnread = itemView.findViewById(R.id.viewUnreadIndicator);
            btnClear = itemView.findViewById(R.id.btnClearNotification);
            ivIcon = itemView.findViewById(R.id.ivNotificationIcon);
        }
    }
}
