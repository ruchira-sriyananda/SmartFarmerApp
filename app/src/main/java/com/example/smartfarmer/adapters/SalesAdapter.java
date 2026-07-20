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

public class SalesAdapter extends RecyclerView.Adapter<SalesAdapter.SaleViewHolder> {
    private List<Order> sales;
    private OnSaleClickListener listener;

    public interface OnSaleClickListener {
        void onUpdateStatusClick(Order sale);
    }

    public SalesAdapter(List<Order> sales, OnSaleClickListener listener) {
        this.sales = sales;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SaleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new SaleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SaleViewHolder holder, int position) {
        Order sale = sales.get(position);
        holder.tvName.setText(sale.getProductName());
        holder.tvDate.setText("Sold on: " + sale.getDate().split("T")[0]);
        holder.tvPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", sale.getAmount()));
        holder.tvRef.setText("Ref: " + sale.getTransactionRef());
        holder.tvStatus.setText(sale.getStatus().toUpperCase());

        // Change button text for seller
        holder.btnAction.setText(R.string.update_status);
        holder.btnAction.setCompoundDrawablesWithIntrinsicBounds(android.R.drawable.ic_menu_edit, 0, 0, 0);

        // Status coloring
        if ("delivered".equalsIgnoreCase(sale.getStatus()) || "completed".equalsIgnoreCase(sale.getStatus())) {
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            holder.tvStatus.getBackground().setTint(android.graphics.Color.parseColor("#E8F5E9"));
        } else if ("shipped".equalsIgnoreCase(sale.getStatus())) {
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#FF9800"));
            holder.tvStatus.getBackground().setTint(android.graphics.Color.parseColor("#FFF3E0"));
        } else {
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#1976D2"));
            holder.tvStatus.getBackground().setTint(android.graphics.Color.parseColor("#E3F2FD"));
        }

        if (sale.getProductImageUrl() != null && !sale.getProductImageUrl().isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(sale.getProductImageUrl(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.ivImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }

        holder.btnAction.setOnClickListener(v -> listener.onUpdateStatusClick(sale));
    }

    @Override
    public int getItemCount() {
        return sales.size();
    }

    public void updateSales(List<Order> newList) {
        this.sales = newList;
        notifyDataSetChanged();
    }

    static class SaleViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivImage;
        TextView tvName, tvDate, tvPrice, tvStatus, tvRef;
        com.google.android.material.button.MaterialButton btnAction;

        public SaleViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivOrderProduct);
            tvName = itemView.findViewById(R.id.tvOrderProductName);
            tvDate = itemView.findViewById(R.id.tvOrderDate);
            tvPrice = itemView.findViewById(R.id.tvOrderPrice);
            tvStatus = itemView.findViewById(R.id.tvOrderStatus);
            tvRef = itemView.findViewById(R.id.tvOrderRef);
            btnAction = itemView.findViewById(R.id.btnTrackOrder);
        }
    }
}
