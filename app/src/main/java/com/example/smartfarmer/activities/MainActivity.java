package com.example.smartfarmer.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.PostAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.Post;
import com.example.smartfarmer.utils.SessionManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.example.smartfarmer.models.MobileAd;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends BaseActivity {
    private SessionManager sessionManager;
    private SupabaseAuthHelper supabaseAuth;
    private TextView tvWelcome;
    private BottomNavigationView bottomNavigation;
    private ExtendedFloatingActionButton fabAiAssistant;
    private com.google.android.material.imageview.ShapeableImageView ivProfileIcon;
    private com.github.ybq.android.spinkit.SpinKitView pbProfileHeader;
    private androidx.recyclerview.widget.RecyclerView rvPosts;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefreshLayout;
    private com.github.ybq.android.spinkit.SpinKitView pbPostsLoading;
    private PostAdapter postAdapter;
    private com.example.smartfarmer.models.PostRepository postRepository;
    private static boolean profilePromptedThisSession = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        supabaseAuth = new SupabaseAuthHelper(this);
        postRepository = new com.example.smartfarmer.models.PostRepository(this);
        
        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        if (!profilePromptedThisSession) {
            if (!sessionManager.areBasicDetailsComplete()) {
                profilePromptedThisSession = true;
                startActivity(new Intent(this, RegisterActivity.class));
                finish();
                return;
            } else if (sessionManager.getProfileImage().isEmpty() || sessionManager.getProfileImage().equalsIgnoreCase("null")) {
                profilePromptedThisSession = true;
                startActivity(new Intent(this, ProfileImageActivity.class));
                finish();
                return;
            }
        }

        tvWelcome = findViewById(R.id.tvWelcome);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        fabAiAssistant = findViewById(R.id.fabAiAssistant);
        ivProfileIcon = findViewById(R.id.ivProfileIcon);
        pbProfileHeader = findViewById(R.id.pbProfileHeader);
        pbPostsLoading = findViewById(R.id.pbPostsLoading);
        rvPosts = findViewById(R.id.rvPosts);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        ivProfileIcon.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
        });

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.ocean_blue));
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadProfileImage();
            fetchData();
        });

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setupToolbarMenu(toolbar);
        
        String currentDate = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date());
        toolbar.setSubtitle(currentDate);

        String email = sessionManager.getUserEmail();
        String name = sessionManager.getUserName();
        if (name != null && !name.isEmpty()) {
            tvWelcome.setText(getString(R.string.welcome_user, name.split(" ")[0]));
        } else if (email != null) {
            tvWelcome.setText(getString(R.string.welcome_user, email.split("@")[0]));
        }

        loadProfileImage();
        setupNavigation();
        setupAiAssistant();
        setupDashboardGrid();
        setupPostsRecyclerView();
        
        requestNotificationPermission();
        handleIntent(getIntent());
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, 
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String targetPostId = intent.getStringExtra("target_post_id");
        String otherUserId = intent.getStringExtra("other_user_id");

        if (targetPostId != null) {
            // Future: Scroll to specific post
            Toast.makeText(this, "Showing post related to notification", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchData() {
        fetchPosts();
        fetchAds();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Ensure Home icon is highlighted
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }

        loadProfileImage();
        updateNotificationBadge();
        
        // ONLY fetch if list is totally empty (e.g. first start)
        // Otherwise, let the user scroll or manually refresh
        if (postAdapter != null && postAdapter.getItemCount() == 0) {
            fetchData();
        }
    }

    @Override
    protected void onNotificationReceived() {
        super.onNotificationReceived();
        updateNotificationBadge();
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void updateNotificationBadge() {
        supabaseAuth.getUnreadNotificationsCount(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String count) {
                runOnUiThread(() -> {
                    try {
                        int c = Integer.parseInt(count);
                        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
                        if (toolbar != null) {
                            com.google.android.material.badge.BadgeDrawable badge = com.google.android.material.badge.BadgeDrawable.create(MainActivity.this);
                            if (c > 0) {
                                badge.setVisible(true);
                                badge.setNumber(c);
                                com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications);
                            } else {
                                // To remove/hide, we can detach or just make invisible
                                // Finding how to hide it once attached is tricky with BadgeUtils, 
                                // so we usually keep a reference or just re-attach with visibility false
                                badge.setVisible(false);
                                com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notifications);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            @Override public void onError(String error) {}
        });
    }

    private void setupToolbarMenu(com.google.android.material.appbar.MaterialToolbar toolbar) {
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
                return true;
            } else if (id == R.id.action_profile_nav) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void loadProfileImage() {
        // 1. Try to load from local session first for speed
        String localImage = sessionManager.getProfileImage();
        if (localImage != null && !localImage.isEmpty()) {
            showImageInHeader(localImage);
        }

        // 2. Fetch from Supabase to ensure it's up to date
        String email = sessionManager.getUserEmail();
        if (email == null || email.isEmpty()) return;

        pbProfileHeader.setVisibility(View.VISIBLE);
        ivProfileIcon.setAlpha(0.6f);

        supabaseAuth.getProfileImageByEmail(email, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String imageBase64) {
                runOnUiThread(() -> {
                    pbProfileHeader.setVisibility(View.GONE);
                    ivProfileIcon.setAlpha(1.0f);
                    if (imageBase64 != null && !imageBase64.isEmpty()) {
                        // Only update if it's different from local to save CPU
                        if (!imageBase64.equals(localImage)) {
                            showImageInHeader(imageBase64);
                            sessionManager.setProfileImage(imageBase64); // Save locally for next time
                        }
                    } else {
                        showImageInHeader(null);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbProfileHeader.setVisibility(View.GONE);
                    ivProfileIcon.setAlpha(1.0f);
                    showImageInHeader(null);
                });
            }
        });
    }

    private void showImageInHeader(String imageStr) {
        if (imageStr == null || imageStr.isEmpty() || imageStr.equalsIgnoreCase("null")) {
            ivProfileIcon.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .circleCrop()
                    .into(ivProfileIcon);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .circleCrop()
                        .into(ivProfileIcon);
            } catch (Exception e) {
                ivProfileIcon.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    private void setupNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_market) {
                startActivity(new Intent(this, MarketActivity.class));
                return true;
            } else if (id == R.id.nav_community) {
                startActivity(new Intent(this, CommunityActivity.class));
                return true;
            } else if (id == R.id.nav_barter) {
                startActivity(new Intent(this, BarterActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupAiAssistant() {
        fabAiAssistant.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, AiAssistantActivity.class));
        });
    }

    private void setupDashboardGrid() {
        findViewById(R.id.cardMarket).setOnClickListener(v -> 
            startActivity(new Intent(this, MarketActivity.class)));
        
        findViewById(R.id.cardCommunity).setOnClickListener(v -> 
            startActivity(new Intent(this, CommunityActivity.class)));
            
        findViewById(R.id.cardBarter).setOnClickListener(v -> 
            startActivity(new Intent(this, BarterActivity.class)));
    }

    private void setupPostsRecyclerView() {
        androidx.recyclerview.widget.LinearLayoutManager layoutManager = new androidx.recyclerview.widget.LinearLayoutManager(this);
        rvPosts.setLayoutManager(layoutManager);
        postAdapter = new PostAdapter(new ArrayList<>());
        rvPosts.setAdapter(postAdapter);
    }

    private void fetchPosts() {
        if (!swipeRefreshLayout.isRefreshing() && postAdapter.getItemCount() == 0) {
            pbPostsLoading.setVisibility(View.VISIBLE);
        }

        postRepository.getPosts(sessionManager.getUserId(), new com.example.smartfarmer.models.PostRepository.PostCallback() {
            @Override
            public void onPostsLoaded(List<Post> posts) {
                runOnUiThread(() -> {
                    pbPostsLoading.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    postAdapter.updatePosts(posts);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbPostsLoading.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                });
            }
        });
    }

    private void fetchAds() {
        supabaseAuth.fetchActiveAds(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(data);
                        List<MobileAd> ads = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            MobileAd ad = new MobileAd();
                            ad.setAdId(obj.getString("ad_id"));
                            ad.setTitle(obj.getString("title"));
                            ad.setDescription(obj.getString("description"));
                            ad.setImageUrl(obj.optString("image_url", ""));
                            ad.setClicks(obj.optInt("clicks", 0));
                            ads.add(ad);
                        }
                        postAdapter.updateAds(ads);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override public void onError(String error) {}
        });
    }
}


