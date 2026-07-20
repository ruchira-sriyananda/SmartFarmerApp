package com.example.smartfarmer.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.Product;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class MarketAdapter extends RecyclerView.Adapter<MarketAdapter.ProductViewHolder> {
    private List<Product> products;
    private String currentUserId;
    private OnProductClickListener listener;

    public interface OnProductClickListener {
        void onBuyClick(Product product);
        void onAddToCartClick(Product product);
        void onMessageSellerClick(Product product);
        void onEditClick(Product product);
    }

    public MarketAdapter(List<Product> products, String currentUserId, OnProductClickListener listener) {
        this.products = products;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = products.get(position);
        holder.tvName.setText(product.getName());
        holder.tvCategory.setText(product.getCategory());
        holder.tvPrice.setText(String.format("Rs. %.2f", product.getPrice()));

        if (product.isAd()) {
            holder.tvAdBadge.setVisibility(View.VISIBLE);
        } else {
            holder.tvAdBadge.setVisibility(View.GONE);
        }

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

        // Logic for Edit/Buy/Cart visibility
        if (currentUserId != null && currentUserId.equals(product.getSellerId())) {
            // Own product: Show Edit, hide Buy/Cart
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.btnBuy.setVisibility(View.GONE);
            holder.btnAddToCart.setVisibility(View.GONE);
            holder.btnMessage.setVisibility(View.GONE);
        } else {
            // Other's product: Hide Edit, show Buy/Cart/Message
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnBuy.setVisibility(View.VISIBLE);
            holder.btnAddToCart.setVisibility(View.VISIBLE);
            holder.btnMessage.setVisibility(View.VISIBLE);
        }

        holder.btnBuy.setOnClickListener(v -> listener.onBuyClick(product));
        holder.btnAddToCart.setOnClickListener(v -> listener.onAddToCartClick(product));
        holder.btnMessage.setOnClickListener(v -> listener.onMessageSellerClick(product));
        holder.btnEdit.setOnClickListener(v -> listener.onEditClick(product));
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    public void updateProducts(List<Product> newProducts) {
        this.products = newProducts;
        notifyDataSetChanged();
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName, tvCategory, tvPrice, tvAdBadge;
        MaterialButton btnBuy;
        ImageButton btnEdit, btnAddToCart, btnMessage;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivProductImage);
            tvName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvProductCategory);
            tvPrice = itemView.findViewById(R.id.tvProductPrice);
            tvAdBadge = itemView.findViewById(R.id.tvAdBadge);
            btnBuy = itemView.findViewById(R.id.btnBuy);
            btnEdit = itemView.findViewById(R.id.btnEditProduct);
            btnAddToCart = itemView.findViewById(R.id.btnAddToCart);
            btnMessage = itemView.findViewById(R.id.btnMessageSeller);
        }
    }
}
