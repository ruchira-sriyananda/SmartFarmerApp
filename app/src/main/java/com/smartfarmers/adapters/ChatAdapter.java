package com.smartfarmers.adapters;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.models.ChatMessage;
import com.google.android.material.card.MaterialCardView;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tables.TableTheme;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    private List<ChatMessage> messages;
    private Markwon markwon;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (markwon == null) {
            TableTheme tableTheme = new TableTheme.Builder()
                    .tableBorderColor(0xFFBDBDBD)
                    .tableBorderWidth(2)
                    .tableCellPadding((int) (8 * parent.getContext().getResources().getDisplayMetrics().density))
                    .build();

            markwon = Markwon.builder(parent.getContext())
                    .usePlugin(TablePlugin.create(tableTheme))
                    .build();
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        if (message.isUser()) {
            holder.tvContent.setText(message.getContent());
        } else {
            markwon.setMarkdown(holder.tvContent, message.getContent());
        }

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
