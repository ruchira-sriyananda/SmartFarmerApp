package com.example.smartfarmer.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ColumnInfo;
import androidx.annotation.NonNull;

@Entity(tableName = "messages")
public class MessageEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "message_id")
    private String messageId;

    @ColumnInfo(name = "sender_id")
    private String senderId;

    @ColumnInfo(name = "receiver_id")
    private String receiverId;

    @ColumnInfo(name = "message_text")
    private String messageText;

    @ColumnInfo(name = "message_type")
    private String messageType = "text";

    @ColumnInfo(name = "attachment_url")
    private String attachmentUrl;

    @ColumnInfo(name = "sent_at")
    private String sentAt;

    public MessageEntity(@NonNull String messageId, String senderId, String receiverId, String messageText, String sentAt, String messageType, String attachmentUrl) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.messageText = messageText;
        this.sentAt = sentAt;
        this.messageType = messageType;
        this.attachmentUrl = attachmentUrl;
    }

    @NonNull
    public String getMessageId() { return messageId; }
    public void setMessageId(@NonNull String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getAttachmentUrl() { return attachmentUrl; }
    public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
}
