package com.smartfarmers.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import java.util.List;

public class PostImageAdapter extends RecyclerView.Adapter<PostImageAdapter.ViewHolder> {
    private List<String> images;

    public PostImageAdapter(List<String> images) {
        this.images = images;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post_image_detail, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imageStr = images.get(position);
        if (imageStr == null || imageStr.isEmpty()) {
            holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .into(holder.ivImage);
        } else {
            try {
                byte[] decodedString = Base64.decode(imageStr, Base64.DEFAULT);
                com.bumptech.glide.Glide.with(holder.itemView.getContext())
                        .load(decodedString)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .into(holder.ivImage);
            } catch (Exception e) {
                holder.ivImage.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;

        ViewHolder(View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivPostImageDetail);
        }
    }
}
