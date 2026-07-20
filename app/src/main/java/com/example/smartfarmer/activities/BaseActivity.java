package com.example.smartfarmer.activities;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smartfarmer.utils.LocaleHelper;
import com.example.smartfarmer.utils.SessionManager;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.utils.NotificationHelper;
import androidx.appcompat.app.AppCompatDelegate;
import org.json.JSONArray;
import org.json.JSONObject;

public class BaseActivity extends AppCompatActivity {
    private static Handler notificationHandler = new Handler(Looper.getMainLooper());
    private static Runnable notificationRunnable;
    private static final int POLL_INTERVAL = 8000; // 8 seconds for faster testing
    private static String lastCheckedNotificationId = null;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isDarkModeEnabled()) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
        super.onCreate(savedInstanceState);
        
        if (sessionManager.isLoggedIn()) {
            startNotificationPolling();
        }
    }

    private void startNotificationPolling() {
        if (notificationRunnable != null) return;

        notificationRunnable = new Runnable() {
            @Override
            public void run() {
                checkNewNotifications();
                notificationHandler.postDelayed(this, POLL_INTERVAL);
            }
        };
        notificationHandler.postDelayed(notificationRunnable, POLL_INTERVAL);
    }

    private void checkNewNotifications() {
        SessionManager sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) return;

        SupabaseAuthHelper supabaseAuth = new SupabaseAuthHelper(this);
        supabaseAuth.fetchNotifications(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                try {
                    JSONArray arr = new JSONArray(data);
                    if (arr.length() > 0) {
                        JSONObject firstNote = arr.getJSONObject(0);
                        String firstId = firstNote.optString("notification_id", firstNote.optString("id", ""));

                        if (lastCheckedNotificationId == null) {
                            lastCheckedNotificationId = firstId;
                            return;
                        }

                        if (firstId.equals(lastCheckedNotificationId)) return;

                        // Process all notifications until we hit the last checked ID
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject note = arr.getJSONObject(i);
                            String id = note.optString("notification_id", note.optString("id", ""));
                            
                            if (id.equals(lastCheckedNotificationId)) break;

                            boolean isRead = note.optBoolean("is_read", false);
                            if (!isRead) {
                                String title = note.optString("notification_title", note.optString("title", "Notification"));
                                String message = note.optString("notification_message", note.optString("message", ""));
                                
                                String rawType = note.optString("notification_type", note.optString("type", ""));
                                String type = rawType;
                                String relatedId = "";
                                
                                if (rawType.contains(":")) {
                                    String[] parts = rawType.split(":", 2);
                                    type = parts[0];
                                    relatedId = parts[1];
                                } else {
                                    relatedId = note.optString("related_id", "");
                                }
                                
                                final String finalType = type;
                                final String finalRelatedId = relatedId;
                                runOnUiThread(() -> {
                                    NotificationHelper.showNotification(BaseActivity.this, title, message, finalType, finalRelatedId);
                                    onNotificationReceived();
                                });
                            }
                        }
                        
                        lastCheckedNotificationId = firstId;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override public void onError(String error) {}
        });
    }

    /**
     * Override this in activities that need to update UI when a new notification arrives
     */
    protected void onNotificationReceived() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        SessionManager sessionManager = new SessionManager(this);
        if (sessionManager.isLoggedIn()) {
            startNotificationPolling();
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        SessionManager sessionManager = new SessionManager(newBase);
        String lang = sessionManager.getLanguage();
        super.attachBaseContext(LocaleHelper.setLocale(newBase, lang));
    }
}
