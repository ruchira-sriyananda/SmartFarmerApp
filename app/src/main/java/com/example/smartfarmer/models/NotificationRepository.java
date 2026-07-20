package com.example.smartfarmer.models;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepository {
    private final NotificationDao notificationDao;
    private final SupabaseAuthHelper supabaseAuth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface NotificationCallback {
        void onLoaded(List<Notification> notifications);
        void onError(String error);
    }

    public NotificationRepository(Context context) {
        this.notificationDao = ChatDatabase.getInstance(context).notificationDao();
        this.supabaseAuth = new SupabaseAuthHelper(context);
    }

    public void getNotifications(String userId, NotificationCallback callback) {
        executor.execute(() -> {
            // 1. Load from Cache
            try {
                List<NotificationEntity> cached = notificationDao.getAllNotifications();
                if (!cached.isEmpty()) {
                    mainHandler.post(() -> callback.onLoaded(convertToModels(cached)));
                }
            } catch (android.database.sqlite.SQLiteBlobTooBigException e) {
                notificationDao.deleteAll();
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Fetch from Network
            supabaseAuth.fetchNotifications(userId, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String json) {
                    executor.execute(() -> {
                        try {
                            List<Notification> network = parseJson(json);
                            notificationDao.deleteAll();
                            notificationDao.insertNotifications(convertToEntities(network));
                            mainHandler.post(() -> callback.onLoaded(network));
                        } catch (Exception e) {
                            mainHandler.post(() -> callback.onError(e.getMessage()));
                        }
                    });
                }
                @Override public void onError(String error) {
                    mainHandler.post(() -> callback.onError(error));
                }
            });
        });
    }

    public void markAsRead(String id, SupabaseAuthHelper.AuthCallback callback) {
        executor.execute(() -> {
            notificationDao.markAsRead(id);
            supabaseAuth.markNotificationAsRead(id, callback);
        });
    }

    public void deleteNotification(String id, SupabaseAuthHelper.AuthCallback callback) {
        executor.execute(() -> {
            notificationDao.deleteById(id);
            supabaseAuth.deleteNotification(id, callback);
        });
    }

    public void clearAll(String userId, SupabaseAuthHelper.AuthCallback callback) {
        executor.execute(() -> {
            notificationDao.deleteAll();
            supabaseAuth.deleteAllNotifications(userId, callback);
        });
    }

    private List<Notification> parseJson(String json) throws Exception {
        if (json == null || json.trim().isEmpty() || json.equals("[]")) return new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        List<Notification> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            Notification n = new Notification();
            n.setId(obj.optString("notification_id", obj.optString("id", "")));
            n.setTitle(obj.optString("notification_title", obj.optString("title", "Notification")));
            n.setMessage(obj.optString("notification_message", obj.optString("message", "")));
            
            String rawType = obj.optString("notification_type", obj.optString("type", "general"));
            if (rawType.contains(":")) {
                String[] parts = rawType.split(":", 2);
                n.setType(parts[0]);
                n.setRelatedId(parts[1]);
            } else {
                n.setType(rawType);
                n.setRelatedId(obj.optString("related_id", ""));
            }

            n.setRead(obj.optBoolean("is_read", false));
            n.setCreatedAt(obj.optString("created_at", ""));
            list.add(n);
        }
        return list;
    }

    private List<Notification> convertToModels(List<NotificationEntity> entities) {
        List<Notification> models = new ArrayList<>();
        for (NotificationEntity e : entities) {
            Notification n = new Notification();
            n.setId(e.getId());
            n.setTitle(e.getTitle());
            n.setMessage(e.getMessage());
            n.setType(e.getType());
            n.setRelatedId(e.getRelatedId());
            n.setRead(e.isRead());
            n.setCreatedAt(e.getCreatedAt());
            models.add(n);
        }
        return models;
    }

    private List<NotificationEntity> convertToEntities(List<Notification> models) {
        List<NotificationEntity> entities = new ArrayList<>();
        for (Notification m : models) {
            NotificationEntity e = new NotificationEntity();
            e.setId(m.getId());
            e.setTitle(m.getTitle());
            e.setMessage(m.getMessage());
            e.setType(m.getType());
            e.setRelatedId(m.getRelatedId());
            e.setRead(m.isRead());
            e.setCreatedAt(m.getCreatedAt());
            entities.add(e);
        }
        return entities;
    }
}
