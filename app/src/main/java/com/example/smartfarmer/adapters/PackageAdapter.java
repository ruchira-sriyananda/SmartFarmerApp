package com.example.smartfarmer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.SubscriptionPackage;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.PackageViewHolder> {
    private List<SubscriptionPackage> packages;
    private OnPackageClickListener listener;

    public interface OnPackageClickListener {
        void onPackageClick(SubscriptionPackage pkg);
    }

    public PackageAdapter(List<SubscriptionPackage> packages, OnPackageClickListener listener) {
        this.packages = packages;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PackageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_subscription_package, parent, false);
        return new PackageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PackageViewHolder holder, int position) {
        SubscriptionPackage pkg = packages.get(position);
        holder.tvName.setText(pkg.getPackageName());
        holder.tvDescription.setText(pkg.getDescription());
        holder.tvPrice.setText(String.format("Rs. %.2f", pkg.getPrice()));

        if ("Gold".equalsIgnoreCase(pkg.getAdType())) {
            holder.card.setStrokeColor(android.graphics.Color.parseColor("#FFD700"));
        } else if ("Silver".equalsIgnoreCase(pkg.getAdType())) {
            holder.card.setStrokeColor(android.graphics.Color.parseColor("#C0C0C0"));
        }

        holder.itemView.setOnClickListener(v -> listener.onPackageClick(pkg));
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    static class PackageViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDescription, tvPrice;
        MaterialCardView card;

        public PackageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPackageName);
            tvDescription = itemView.findViewById(R.id.tvPackageDescription);
            tvPrice = itemView.findViewById(R.id.tvPackagePrice);
            card = itemView.findViewById(R.id.cardPackage);
        }
    }
}
