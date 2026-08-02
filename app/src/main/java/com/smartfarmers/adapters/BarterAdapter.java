package com.smartfarmers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.models.BarterListing;
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;
import android.util.Base64;
import java.util.List;

public class BarterAdapter extends RecyclerView.Adapter<BarterAdapter.ViewHolder> {
    private List<BarterListing> listings;
    private OnBarterActionListener listener;
    private String currentUserId;
    private java.util.Set<String> requestedListingIds = new java.util.HashSet<>();

    public interface OnBarterActionListener {
        void onMakeRequest(BarterListing listing);
    }

    public BarterAdapter(List<BarterListing> listings, OnBarterActionListener listener) {
        this.listings = listings;
        this.listener = listener;
    }

    public void setCurrentUserId(String userId) {
        this.currentUserId = userId;
    }

    public void setRequestedListingIds(java.util.Collection<String> ids) {
        this.requestedListingIds.clear();
        if (ids != null) this.requestedListingIds.addAll(ids);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_barter_listing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BarterListing listing = listings.get(position);
        holder.tvTitle.setText(listing.getTitle());
        holder.tvQuantity.setText(listing.getQuantity() + " " + listing.getUnit() + " available");
        holder.tvOwner.setText("by " + (listing.getUserName() != null ? listing.getUserName() : "Unknown Farmer"));
        holder.tvLocation.setText(listing.getDistrict() != null ? listing.getDistrict() : "");
        holder.tvType.setText(listing.getType() != null ? listing.getType() : "Goods");

        loadImage(listing.getImageUrl(), holder.ivImage, holder.progressBar);

        // Hide request button if it's the user's own listing or if no listener is provided
        if (listener == null || (currentUserId != null && currentUserId.equals(listing.getUserId()))) {
            holder.btnRequest.setVisibility(View.GONE);
        } else {
            holder.btnRequest.setVisibility(View.VISIBLE);
            
            // Check if already requested
            if (requestedListingIds.contains(listing.getListingId())) {
                holder.btnRequest.setText(R.string.request_sent); // "Request Sent!"
                holder.btnRequest.setEnabled(false);
                holder.btnRequest.setAlpha(0.6f);
            } else {
                holder.btnRequest.setText(R.string.send_request); // "Send Request"
                holder.btnRequest.setEnabled(true);
                holder.btnRequest.setAlpha(1.0f);
                holder.btnRequest.setOnClickListener(v -> {
                    if (listener != null) listener.onMakeRequest(listing);
                });
            }
        }
    }

    private void loadImage(String imageStr, ImageView imageView, View progressBar) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            progressBar.setVisibility(View.GONE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> listener = 
            new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
            @Override
            public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, 
                                      Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                      boolean isFirstResource) {
                progressBar.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, 
                                         com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                         com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                progressBar.setVisibility(View.GONE);
                return false;
            }
        };

        if (imageStr.startsWith("http")) {
            Glide.with(imageView.getContext())
                .load(imageStr)
                .listener(listener)
                .into(imageView);
        } else {
            try {
                byte[] bytes = Base64.decode(imageStr, Base64.DEFAULT);
                Glide.with(imageView.getContext())
                    .load(bytes)
                    .listener(listener)
                    .into(imageView);
            } catch (Exception e) {
                progressBar.setVisibility(View.GONE);
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    public void updateList(List<BarterListing> newList) {
        this.listings = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle, tvQuantity, tvOwner, tvLocation, tvType;
        MaterialButton btnRequest;
        View progressBar;

        ViewHolder(View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivBarterImage);
            tvTitle = itemView.findViewById(R.id.tvBarterTitle);
            tvType = itemView.findViewById(R.id.tvBarterType);
            tvQuantity = itemView.findViewById(R.id.tvBarterQuantity);
            tvOwner = itemView.findViewById(R.id.tvBarterOwner);
            tvLocation = itemView.findViewById(R.id.tvBarterLocation);
            btnRequest = itemView.findViewById(R.id.btnMakeRequest);
            progressBar = itemView.findViewById(R.id.pbBarterImage);
        }
    }
}
