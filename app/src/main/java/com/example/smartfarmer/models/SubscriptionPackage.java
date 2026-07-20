package com.example.smartfarmer.models;

import java.util.List;

public class SubscriptionPackage {
    private String packageId;
    private String packageName;
    private String description;
    private double price;
    private int durationDays;
    private String adType;
    private List<String> features;
    private boolean isActive;
    private int displayOrder;
    private String createdAt;
    private String updatedAt;

    public SubscriptionPackage() {}

    // Getters and Setters
    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
    public String getAdType() { return adType; }
    public void setAdType(String adType) { this.adType = adType; }
    public List<String> getFeatures() { return features; }
    public void setFeatures(List<String> features) { this.features = features; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
