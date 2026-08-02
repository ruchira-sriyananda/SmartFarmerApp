package com.smartfarmers.models;

public class ChatRoom {
    private String id;
    private String name;
    private String lastMessage;
    private String lastMessageTime;
    private String imageUrl;
    private String adminId;
    private boolean isGroup;
    private boolean isPublic;
    private boolean isDiscover;

    public ChatRoom() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }
    public String getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(String lastMessageTime) { this.lastMessageTime = lastMessageTime; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean aPublic) { isPublic = aPublic; }
    public boolean isDiscover() { return isDiscover; }
    public void setDiscover(boolean discover) { isDiscover = discover; }
}
