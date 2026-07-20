package com.example.smartfarmer.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.ChatMessage;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        holder.tvContent.setText(message.getContent());

        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.cardMessage.getLayoutParams();
        int margin = (int) (40 * holder.itemView.getContext().getResources().getDisplayMetrics().density);
        if (message.isUser()) {
            holder.layoutContainer.setGravity(Gravity.END);
            holder.cardMessage.setCardBackgroundColor(0xFFE3F2FD); // Light Blue
            params.setMargins(margin, 0, 0, 0);
        } else {
            holder.layoutContainer.setGravity(Gravity.START);
            holder.cardMessage.setCardBackgroundColor(0xFFFFFFFF); // White
            params.setMargins(0, 0, margin, 0);
        }
        holder.cardMessage.setLayoutParams(params);

    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout layoutContainer;
        MaterialCardView cardMessage;
        TextView tvContent;

        ViewHolder(View itemView) {
            super(itemView);
            layoutContainer = itemView.findViewById(R.id.layoutMessageContainer);
            cardMessage = itemView.findViewById(R.id.cardMessage);
            tvContent = itemView.findViewById(R.id.tvMessageContent);
        }
    }
}
