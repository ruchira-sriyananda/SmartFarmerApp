package com.smartfarmers.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.models.MobileAd;
import java.util.List;

public class AdAdapter extends RecyclerView.Adapter<AdAdapter.AdViewHolder> {
    private List<MobileAd> ads;
    private OnAdClickListener listener;

    public interface OnAdClickListener {
        void onAdClick(MobileAd ad);
    }

    public AdAdapter(List<MobileAd> ads, OnAdClickListener listener) {
        this.ads = ads;
        this.listener = listener;
    }

    @NonNull
    @Override
    public AdViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ad_banner, parent, false);
        return new AdViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdViewHolder holder, int position) {
        MobileAd ad = ads.get(position);
        holder.tvTitle.setText(ad.getTitle());
        holder.tvDescription.setText(ad.getDescription());

        if (ad.getImageUrl() != null && !ad.getImageUrl().isEmpty()) {
            try {
                byte[] decodedString = Base64.decode(ad.getImageUrl(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                holder.ivBanner.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivBanner.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }

        holder.itemView.setOnClickListener(v -> listener.onAdClick(ad));
    }

    @Override
    public int getItemCount() {
        return ads.size();
    }

    public void updateAds(List<MobileAd> newAds) {
        this.ads = newAds;
        notifyDataSetChanged();
    }

    static class AdViewHolder extends RecyclerView.ViewHolder {
        ImageView ivBanner;
        TextView tvTitle, tvDescription;

        public AdViewHolder(@NonNull View itemView) {
            super(itemView);
            ivBanner = itemView.findViewById(R.id.ivAdBanner);
            tvTitle = itemView.findViewById(R.id.tvAdTitle);
            tvDescription = itemView.findViewById(R.id.tvAdDescription);
        }
    }
}
