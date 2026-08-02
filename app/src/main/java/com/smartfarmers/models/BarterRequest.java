package com.smartfarmers.models;

public class BarterRequest {
    private String requestId;
    private String listingId;
    private String requesterId;
    private String offeredItem;
    private String requestStatus;
    private String createdAt;
    
    // Joined Details
    private String requesterName;
    private String requesterProfileImage;
    private String listingTitle;
    private String ownerId;

    public BarterRequest() {}

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getListingId() { return listingId; }
    public void setListingId(String listingId) { this.listingId = listingId; }

    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }

    public String getOfferedItem() { return offeredItem; }
    public void setOfferedItem(String offeredItem) { this.offeredItem = offeredItem; }

    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getRequesterName() { return requesterName; }
    public void setRequesterName(String requesterName) { this.requesterName = requesterName; }

    public String getRequesterProfileImage() { return requesterProfileImage; }
    public void setRequesterProfileImage(String requesterProfileImage) { this.requesterProfileImage = requesterProfileImage; }

    public String getListingTitle() { return listingTitle; }
    public void setListingTitle(String listingTitle) { this.listingTitle = listingTitle; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
