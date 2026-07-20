package com.example.smartfarmer.models;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {MessageEntity.class, PostEntity.class, NotificationEntity.class}, version = 5)
public abstract class ChatDatabase extends RoomDatabase {
    private static ChatDatabase instance;

    public abstract MessageDao messageDao();
    public abstract PostDao postDao();
    public abstract NotificationDao notificationDao();

    public static synchronized ChatDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(context.getApplicationContext(),
                    ChatDatabase.class, "chat_database")
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
