package com.example.smartfarmer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.BarterRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.bumptech.glide.Glide;
import android.util.Base64;
import java.util.List;

public class BarterRequestAdapter extends RecyclerView.Adapter<BarterRequestAdapter.ViewHolder> {
    private List<BarterRequest> requests;
    private OnRequestActionListener listener;
    private boolean isReceivedType;

    public interface OnRequestActionListener {
        void onAccept(BarterRequest request);
        void onReject(BarterRequest request);
    }

    public BarterRequestAdapter(List<BarterRequest> requests, boolean isReceivedType, OnRequestActionListener listener) {
        this.requests = requests;
        this.isReceivedType = isReceivedType;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_barter_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BarterRequest request = requests.get(position);
        
        holder.tvName.setText(request.getRequesterName() != null ? request.getRequesterName() : "Unknown Farmer");
        holder.tvDate.setText(request.getCreatedAt().split("T")[0]);
        holder.tvStatus.setText(request.getRequestStatus().toUpperCase());
        
        // Status colors and styling
        int statusColor;
        int statusBg;
        switch (request.getRequestStatus().toLowerCase()) {
            case "accepted": 
                statusColor = 0xFF2E7D32; // Dark Green
                statusBg = 0xFFE8F5E9;    // Light Green
                break;
            case "rejected": 
                statusColor = 0xFFC62828; // Dark Red
                statusBg = 0xFFFFEBEE;    // Light Red
                break;
            default: 
                statusColor = 0xFF1565C0; // Dark Blue
                statusBg = 0xFFE3F2FD;    // Light Blue
                break;
        }
        holder.tvStatus.getBackground().setTint(statusBg);
        holder.tvStatus.setTextColor(statusColor);

        holder.tvListingTitle.setText(request.getListingTitle());
        holder.tvOffer.setText(request.getOfferedItem());

        loadProfileImage(request.getRequesterProfileImage(), holder.ivProfile);

        // UI Adjustments for Sent vs Received
        if (isReceivedType) {
            holder.tvLabelOffer.setText("Offer Received:");
            holder.tvName.setText(request.getRequesterName() != null ? request.getRequesterName() : "Unknown Farmer");
            if ("pending".equalsIgnoreCase(request.getRequestStatus())) {
                holder.layoutActions.setVisibility(View.VISIBLE);
            } else {
                holder.layoutActions.setVisibility(View.GONE);
            }
        } else {
            holder.tvLabelOffer.setText("Your Offer:");
            holder.layoutActions.setVisibility(View.GONE);
            // In Sent tab, show the person I sent it to
            holder.tvName.setText("Sent to " + (request.getRequesterName() != null ? request.getRequesterName() : "Unknown Farmer"));
        }

        holder.btnAccept.setOnClickListener(v -> {
            if (listener != null) listener.onAccept(request);
        });

        holder.btnReject.setOnClickListener(v -> {
            if (listener != null) listener.onReject(request);
        });
    }

    private void loadProfileImage(String imageStr, ShapeableImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            Glide.with(imageView.getContext()).load(imageStr).circleCrop().into(imageView);
        } else {
            try {
                byte[] bytes = Base64.decode(imageStr, Base64.DEFAULT);
                Glide.with(imageView.getContext()).load(bytes).circleCrop().into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateList(List<BarterRequest> newList) {
        this.requests = newList;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivProfile;
        TextView tvName, tvDate, tvStatus, tvListingTitle, tvOffer, tvLabelOffer;
        MaterialButton btnAccept, btnReject;
        View layoutActions;

        ViewHolder(View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivRequesterImage);
            tvName = itemView.findViewById(R.id.tvRequesterName);
            tvDate = itemView.findViewById(R.id.tvRequestDate);
            tvStatus = itemView.findViewById(R.id.tvRequestStatus);
            tvListingTitle = itemView.findViewById(R.id.tvListingTitleRequest);
            tvOffer = itemView.findViewById(R.id.tvOfferedItem);
            tvLabelOffer = itemView.findViewById(R.id.tvLabelOffer);
            btnAccept = itemView.findViewById(R.id.btnAcceptRequest);
            btnReject = itemView.findViewById(R.id.btnRejectRequest);
            layoutActions = itemView.findViewById(R.id.layoutRequestActions);
        }
    }
}
