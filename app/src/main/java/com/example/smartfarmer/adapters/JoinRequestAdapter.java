package com.example.smartfarmer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.User;
import com.google.android.material.imageview.ShapeableImageView;
import java.util.List;

public class JoinRequestAdapter extends RecyclerView.Adapter<JoinRequestAdapter.ViewHolder> {
    private List<User> requests;
    private OnRequestClickListener listener;

    public interface OnRequestClickListener {
        void onAccept(User user);
        void onReject(User user);
    }

    public JoinRequestAdapter(List<User> requests, OnRequestClickListener listener) {
        this.requests = requests;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_join_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = requests.get(position);
        holder.tvName.setText(user.getFullName());
        
        if (user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(user.getProfileImage(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.ivProfile.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivProfile.setImageResource(android.R.drawable.ic_menu_myplaces);
            }
        } else {
            holder.ivProfile.setImageResource(android.R.drawable.ic_menu_myplaces);
        }

        holder.btnAccept.setOnClickListener(v -> listener.onAccept(user));
        holder.btnReject.setOnClickListener(v -> listener.onReject(user));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateRequests(List<User> newRequests) {
        this.requests = newRequests;
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivProfile;
        TextView tvName;
        View btnAccept, btnReject;

        ViewHolder(View itemView) {
            super(itemView);
            ivProfile = itemView.findViewById(R.id.ivRequestUser);
            tvName = itemView.findViewById(R.id.tvRequestUserName);
            btnAccept = itemView.findViewById(R.id.btnAcceptJoin);
            btnReject = itemView.findViewById(R.id.btnRejectJoin);
        }
    }
}