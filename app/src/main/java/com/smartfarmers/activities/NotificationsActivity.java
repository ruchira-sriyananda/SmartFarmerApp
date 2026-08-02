package com.smartfarmers.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.smartfarmers.R;
import com.smartfarmers.adapters.NotificationAdapter;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.Notification;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends BaseActivity {
    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private LinearLayout layoutEmpty;
    private com.github.ybq.android.spinkit.SpinKitView pbLoading;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;

    private com.smartfarmers.models.NotificationRepository notificationRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);
        notificationRepository = new com.smartfarmers.models.NotificationRepository(this);

        initViews();
        setupRecyclerView();
        fetchNotifications();

        handleIncomingIntent(getIntent());
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent != null && intent.hasExtra("type") && intent.hasExtra("related_id")) {
            Notification temp = new Notification();
            temp.setType(intent.getStringExtra("type"));
            temp.setRelatedId(intent.getStringExtra("related_id"));
            temp.setRead(true); // Assuming clicking it implies reading it for this context
            
            // Re-use the existing redirection logic from the adapter listener
            onNotificationClick(temp);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void onNotificationClick(Notification notification) {
        // Navigate based on type
        Intent intent = null;
        String type = notification.getType();
        String relatedId = notification.getRelatedId();

        if ("market".equalsIgnoreCase(type)) {
            intent = new Intent(NotificationsActivity.this, MarketActivity.class);
            if (relatedId != null && !relatedId.isEmpty()) {
                if (relatedId.contains("|")) {
                    String[] parts = relatedId.split("\\|");
                    intent.putExtra("target_id", parts[0]);
                    if (parts.length > 1) intent.putExtra("other_user_id", parts[1]);
                } else {
                    intent.putExtra("target_id", relatedId);
                }
            }
        } else if ("community".equalsIgnoreCase(type)) {
            intent = new Intent(NotificationsActivity.this, MainActivity.class);
            if (relatedId != null && !relatedId.isEmpty()) {
                if (relatedId.contains("|")) {
                    String[] parts = relatedId.split("\\|");
                    intent.putExtra("target_post_id", parts[0]);
                    if (parts.length > 1) intent.putExtra("other_user_id", parts[1]);
                } else {
                    intent.putExtra("target_post_id", relatedId);
                }
            }
        } else if ("chat".equalsIgnoreCase(type)) {
            intent = new Intent(NotificationsActivity.this, ChatActivity.class);
            intent.putExtra("other_user_id", relatedId);
        } else if ("barter".equalsIgnoreCase(type)) {
            intent = new Intent(NotificationsActivity.this, BarterActivity.class);
            if (relatedId != null && !relatedId.isEmpty()) {
                if (relatedId.contains("|")) {
                    String[] parts = relatedId.split("\\|");
                    intent.putExtra("target_id", parts[0]);
                    if (parts.length > 1) intent.putExtra("other_user_id", parts[1]);
                } else {
                    intent.putExtra("target_id", relatedId);
                }
            }
        }
        
        if (intent != null) {
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarNotifications);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_notifications);
        
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear_notifications) {
                clearAllNotifications();
                return true;
            }
            return false;
        });

        rvNotifications = findViewById(R.id.rvNotifications);
        swipeRefresh = findViewById(R.id.swipeRefreshNotifications);
        layoutEmpty = findViewById(R.id.layoutEmptyNotifications);
        pbLoading = findViewById(R.id.pbNotifications);

        swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.ocean_blue));
        swipeRefresh.setOnRefreshListener(this::fetchNotifications);
    }

    private void setupRecyclerView() {
        adapter = new NotificationAdapter(new ArrayList<>(), new NotificationAdapter.OnNotificationClickListener() {
            @Override
            public void onNotificationClick(Notification notification) {
                if (!notification.isRead()) {
                    markAsRead(notification);
                }
                NotificationsActivity.this.onNotificationClick(notification);
            }

            @Override
            public void onDeleteClick(Notification notification) {
                deleteNotification(notification);
            }
        });
        rvNotifications.setLayoutManager(new LinearLayoutManager(this));
        rvNotifications.setAdapter(adapter);
    }

    private void deleteNotification(Notification notification) {
        notificationRepository.deleteNotification(notification.getId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    fetchNotifications();
                    Toast.makeText(NotificationsActivity.this, R.string.notification_deleted, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(NotificationsActivity.this, "Error deleting: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearAllNotifications() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_notifications_title)
                .setMessage(R.string.clear_notifications_confirm)
                .setPositiveButton(R.string.clear, (dialog, which) -> {
                    pbLoading.setVisibility(View.VISIBLE);
                    notificationRepository.clearAll(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            runOnUiThread(() -> {
                                pbLoading.setVisibility(View.GONE);
                                adapter.updateNotifications(new ArrayList<>());
                                layoutEmpty.setVisibility(View.VISIBLE);
                                Toast.makeText(NotificationsActivity.this, R.string.notifications_cleared, Toast.LENGTH_SHORT).show();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                pbLoading.setVisibility(View.GONE);
                                Toast.makeText(NotificationsActivity.this, "Error clearing: " + error, Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void fetchNotifications() {
        if (!swipeRefresh.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
        layoutEmpty.setVisibility(View.GONE);

        notificationRepository.getNotifications(sessionManager.getUserId(), new com.smartfarmers.models.NotificationRepository.NotificationCallback() {
            @Override
            public void onLoaded(List<Notification> notifications) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    if (notifications == null || notifications.isEmpty()) {
                        layoutEmpty.setVisibility(View.VISIBLE);
                        adapter.updateNotifications(new ArrayList<>());
                    } else {
                        layoutEmpty.setVisibility(View.GONE);
                        adapter.updateNotifications(notifications);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(NotificationsActivity.this, "Fetch Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void markAsRead(Notification notification) {
        notification.setRead(true);
        adapter.notifyDataSetChanged();
        
        notificationRepository.markAsRead(notification.getId(), new SupabaseAuthHelper.AuthCallback() {
            @Override public void onSuccess(String data) {}
            @Override public void onError(String error) {}
        });
    }
}
