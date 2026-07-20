package com.example.smartfarmer.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.Product;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {
    private List<Product> cartItems;
    private OnCartItemClickListener listener;

    public interface OnCartItemClickListener {
        void onRemoveClick(Product product, int position);
    }

    public CartAdapter(List<Product> cartItems, OnCartItemClickListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        Product product = cartItems.get(position);
        holder.tvName.setText(product.getName());
        holder.tvCategory.setText(product.getCategory());
        holder.tvPrice.setText(String.format(Locale.getDefault(), "Rs. %.2f", product.getPrice()));

        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(product.getImageUrl(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.ivImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        } else {
            holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
        }

        holder.btnRemove.setOnClickListener(v -> listener.onRemoveClick(product, holder.getAdapterPosition()));
    }

    @Override
    public int getItemCount() {
        return cartItems.size();
    }

    public void updateItems(List<Product> newList) {
        this.cartItems = newList;
        notifyDataSetChanged();
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivImage;
        TextView tvName, tvPrice, tvCategory;
        ImageButton btnRemove;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivCartItemImage);
            tvName = itemView.findViewById(R.id.tvCartItemName);
            tvCategory = itemView.findViewById(R.id.tvCartItemCategory);
            tvPrice = itemView.findViewById(R.id.tvCartItemPrice);
            btnRemove = itemView.findViewById(R.id.btnRemoveFromCart);
        }
    }
}
