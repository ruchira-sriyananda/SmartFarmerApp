package com.example.smartfarmer.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.Order;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;
import java.util.Locale;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {
    private List<Order> orders;
    private OnOrderClickListener listener;

    public interface OnOrderClickListener {
        void onTrackClick(Order order);
    }

    public OrdersAdapter(List<Order> orders, OnOrderClickListener listener) {
        this.orders = orders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        Order order = orders.get(position);
        holder.tvName.setText(order.getProductName());
        holder.tvDate.setText("Purchased on: " + order.getDate().split("T")[0]);
        holder.tvPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", order.getAmount()));
        holder.tvRef.setText("Ref: " + order.getTransactionRef());
        holder.tvStatus.setText(order.getStatus().toUpperCase());

        // Status coloring
        if ("completed".equalsIgnoreCase(order.getStatus())) {
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            holder.tvStatus.getBackground().setTint(android.graphics.Color.parseColor("#E8F5E9"));
        } else {
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#1976D2"));
            holder.tvStatus.getBackground().setTint(android.graphics.Color.parseColor("#E3F2FD"));
        }

        if (order.getProductImageUrl() != null && !order.getProductImageUrl().isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(order.getProductImageUrl(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.ivImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }

        holder.btnTrack.setOnClickListener(v -> listener.onTrackClick(order));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    public void updateOrders(List<Order> newList) {
        this.orders = newList;
        notifyDataSetChanged();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivImage;
        TextView tvName, tvDate, tvPrice, tvStatus, tvRef;
        android.widget.Button btnTrack;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivOrderProduct);
            tvName = itemView.findViewById(R.id.tvOrderProductName);
            tvDate = itemView.findViewById(R.id.tvOrderDate);
            tvPrice = itemView.findViewById(R.id.tvOrderPrice);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvRef = itemView.findViewById(R.id.tvOrderRef);
            btnTrack = itemView.findViewById(R.id.btnTrackOrder);
        }
    }
}
