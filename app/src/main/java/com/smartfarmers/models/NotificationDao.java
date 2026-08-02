package com.smartfarmers.models;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    List<NotificationEntity> getAllNotifications();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotifications(List<NotificationEntity> notifications);

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    void markAsRead(String id);

    @Query("DELETE FROM notifications WHERE id = :id")
    void deleteById(String id);

    @Query("DELETE FROM notifications")
    void deleteAll();
}
