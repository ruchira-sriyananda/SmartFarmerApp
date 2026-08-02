package com.smartfarmers.models;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import java.util.List;

@Dao
public interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    List<PostEntity> getAllPosts();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPosts(List<PostEntity> posts);

    @Query("DELETE FROM posts")
    void deleteAllPosts();
}
