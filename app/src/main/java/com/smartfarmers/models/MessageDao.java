package com.smartfarmers.models;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageEntity message);

    @Query("SELECT * FROM messages WHERE (sender_id = :userId AND receiver_id = :otherId) OR (sender_id = :otherId AND receiver_id = :userId) ORDER BY sent_at ASC")
    List<MessageEntity> getDirectMessages(String userId, String otherId);

    @Query("SELECT * FROM messages WHERE receiver_id = :roomId ORDER BY sent_at ASC")
    List<MessageEntity> getRoomMessages(String roomId);
    
    @Query("SELECT * FROM messages ORDER BY sent_at DESC")
    List<MessageEntity> getAllMessages();

    @Query("DELETE FROM messages WHERE (sender_id = :userId AND receiver_id = :otherId) OR (sender_id = :otherId AND receiver_id = :userId)")
    void deleteDirectMessages(String userId, String otherId);

    @Query("DELETE FROM messages WHERE receiver_id = :roomId")
    void deleteRoomMessages(String roomId);

    @Query("DELETE FROM messages WHERE message_id = :messageId")
    void deleteMessageById(String messageId);

    @Query("DELETE FROM messages")
    void deleteAll();
}
