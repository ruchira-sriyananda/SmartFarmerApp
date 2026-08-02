package com.smartfarmers.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import com.bumptech.glide.Glide;
import com.smartfarmers.R;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;

public class SettingsActivity extends BaseActivity {
    private SessionManager sessionManager;
    private SwitchMaterial switchNotifications, switchOfflineMode, switchDarkMode;
    private TextView tvAppVersion, tvUserName, tvUserEmail;
    private ShapeableImageView ivProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sessionManager = new SessionManager(this);

        setupToolbar();
        initProfileSection();
        initSettingsRows();
        initSwitches();
        loadSettings();
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarSettings);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initProfileSection() {
        ivProfile = findViewById(R.id.ivProfile);
        tvUserName = findViewById(R.id.tvUserName);
        tvUserEmail = findViewById(R.id.tvUserEmail);

        findViewById(R.id.cardProfile).setOnClickListener(v -> {
            startActivity(new Intent(this, ProfileActivity.class));
        });

        updateProfileUI();
    }

    private void updateProfileUI() {
        tvUserName.setText(sessionManager.getUserName());
        tvUserEmail.setText(sessionManager.getUserEmail());

        String profileImage = sessionManager.getProfileImage();
        if (profileImage != null && !profileImage.isEmpty()) {
            Glide.with(this)
                .load(android.util.Base64.decode(profileImage, android.util.Base64.DEFAULT))
                .placeholder(R.drawable.ic_person)
                .into(ivProfile);
        }
    }

    private void initSettingsRows() {
        // Language
        setupRow(findViewById(R.id.layoutLanguage), R.drawable.ic_language, R.string.change_language, v -> {
            startActivity(new Intent(this, LanguageSelectionActivity.class));
        });

        // Contact Us
        setupRow(findViewById(R.id.layoutContactUs), R.drawable.ic_phone, R.string.contact_us, v -> {
            startActivity(new Intent(this, ContactUsActivity.class));
        });

        // About Us
        setupRow(findViewById(R.id.layoutAboutUs), android.R.drawable.ic_dialog_info, R.string.about_us, v -> {
            startActivity(new Intent(this, AboutUsActivity.class));
        });

        // Privacy Policy
        setupRow(findViewById(R.id.layoutPrivacyPolicy), android.R.drawable.ic_menu_agenda, R.string.privacy_policy, v -> {
            startActivity(new Intent(this, PrivacyPolicyActivity.class));
        });

        // Help & FAQ
        setupRow(findViewById(R.id.layoutHelpFaq), android.R.drawable.ic_menu_help, R.string.help_faq, v -> {
            Toast.makeText(this, "Opening Help Center...", Toast.LENGTH_SHORT).show();
        });

        // Rate App
        setupRow(findViewById(R.id.layoutRateApp), android.R.drawable.btn_star_big_on, R.string.rate_app, v -> {
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
            try {
                startActivity(goToMarket);
            } catch (Exception e) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + getPackageName())));
            }
        });

        // Share App
        setupRow(findViewById(R.id.layoutShareApp), android.R.drawable.ic_menu_share, R.string.share_app, v -> {
            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "Hey! Check out SmartFarmer app: https://play.google.com/store/apps/details?id=" + getPackageName());
            sendIntent.setType("text/plain");
            startActivity(Intent.createChooser(sendIntent, "Share via"));
        });
        
        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account)
                .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    Toast.makeText(this, "Request submitted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle(R.string.logout)
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton(R.string.logout, (dialog, which) -> {
                    // Sign out from Google to allow choosing another account next time
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestEmail()
                            .build();
                    GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(this, gso);
                    googleSignInClient.signOut().addOnCompleteListener(task -> {
                        sessionManager.clearSession();
                        Intent intent = new Intent(this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        });
    }

    private void setupRow(View row, int iconRes, int titleRes, View.OnClickListener listener) {
        ImageView iv = row.findViewById(R.id.ivIcon);
        TextView tv = row.findViewById(R.id.tvTitle);
        iv.setImageResource(iconRes);
        tv.setText(titleRes);
        row.setOnClickListener(listener);
    }

    private void initSwitches() {
        switchNotifications = findViewById(R.id.switchNotifications);
        switchOfflineMode = findViewById(R.id.switchOfflineMode);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        switchNotifications.setOnCheckedChangeListener((v, isChecked) -> sessionManager.setNotificationsEnabled(isChecked));
        switchOfflineMode.setOnCheckedChangeListener((v, isChecked) -> sessionManager.setOfflineModeEnabled(isChecked));
        
        switchDarkMode.setOnCheckedChangeListener((v, isChecked) -> {
            sessionManager.setDarkModeEnabled(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    private void loadSettings() {
        switchNotifications.setChecked(sessionManager.isNotificationsEnabled());
        switchOfflineMode.setChecked(sessionManager.isOfflineModeEnabled());
        switchDarkMode.setChecked(sessionManager.isDarkModeEnabled());

        tvAppVersion = findViewById(R.id.tvAppVersion);
        tvAppVersion.setText(getString(R.string.app_version, "1.0.0"));
    }
}
