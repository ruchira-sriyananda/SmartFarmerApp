package com.smartfarmers.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.models.SubscriptionPackage;
import com.google.android.material.button.MaterialButton;
import java.util.List;

public class PackageAdapter extends RecyclerView.Adapter<PackageAdapter.PackageViewHolder> {
    private List<SubscriptionPackage> packages;
    private OnPackageClickListener listener;

    public interface OnPackageClickListener {
        void onBuyClick(SubscriptionPackage subscriptionPackage);
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
        
        holder.tvAdType.setText(pkg.getAdType());
        holder.tvDuration.setText(pkg.getDurationDays() + " Days");

        if (pkg.getFeatures() != null && !pkg.getFeatures().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String feature : pkg.getFeatures()) {
                sb.append("• ").append(feature).append("\n");
            }
            holder.tvFeatures.setText(sb.toString().trim());
            holder.tvFeatures.setVisibility(View.VISIBLE);
        } else {
            holder.tvFeatures.setVisibility(View.GONE);
        }

        holder.btnBuy.setOnClickListener(v -> listener.onBuyClick(pkg));
    }

    @Override
    public int getItemCount() {
        return packages.size();
    }

    public void updatePackages(List<SubscriptionPackage> newPackages) {
        this.packages = newPackages;
        notifyDataSetChanged();
    }

    static class PackageViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDescription, tvPrice, tvAdType, tvDuration, tvFeatures;
        MaterialButton btnBuy;

        public PackageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPackageName);
            tvDescription = itemView.findViewById(R.id.tvPackageDescription);
            tvPrice = itemView.findViewById(R.id.tvPackagePrice);
            tvAdType = itemView.findViewById(R.id.tvAdType);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            tvFeatures = itemView.findViewById(R.id.tvFeatures);
            btnBuy = itemView.findViewById(R.id.btnBuyPackage);
        }
    }
}
