package com.smartfarmers.models;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface AiMessageDao {
    @Insert
    void insert(ChatMessage message);

    @Query("SELECT * FROM ai_messages ORDER BY timestamp ASC")
    List<ChatMessage> getAllMessages();

    @Query("DELETE FROM ai_messages")
    void deleteAll();
}
