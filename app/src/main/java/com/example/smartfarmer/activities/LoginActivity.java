package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.example.smartfarmer.R;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.utils.SessionManager;
import com.example.smartfarmer.utils.ValidationUtils;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class LoginActivity extends BaseActivity {
    private static final String TAG = "LOGIN_DEBUG";
    private TextInputEditText etEmail, etPassword;
    private TextInputLayout tilEmail, tilPassword;
    private MaterialButton btnLogin;
    private com.google.android.gms.common.SignInButton btnGoogleSignIn;
    private TextView tvForgotPassword, tvRegister, tvError;
    private com.github.ybq.android.spinkit.SpinKitView progressBar;
    private android.widget.ImageButton btnChangeLanguage;

    private SessionManager sessionManager;
    private SupabaseAuthHelper supabaseAuth;
    private GoogleSignInClient googleSignInClient;
    private static final int RC_SIGN_IN = 9001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        
        // Handle Routing
        if (sessionManager.isFirstTime()) {
            startActivity(new Intent(this, LanguageSelectionActivity.class));
            finish();
            return;
        }
        
        if (sessionManager.isLoggedIn()) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_login);
        Log.d(TAG, "LoginActivity onCreate");

        initViews();
        initAuth();
        setupClickListeners();
    }

    private void initViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        tilEmail = findViewById(R.id.tilEmail);
        tilPassword = findViewById(R.id.tilPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        tvRegister = findViewById(R.id.tvRegister);
        tvError = findViewById(R.id.tvError);
        progressBar = findViewById(R.id.progressBar);
        btnChangeLanguage = findViewById(R.id.btnChangeLanguage);
    }

    private void initAuth() {
        sessionManager = new SessionManager(this);
        supabaseAuth = new SupabaseAuthHelper(this);

        // Use string resource for Web Client ID
        String webClientId = getString(R.string.google_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        btnGoogleSignIn.setOnClickListener(v -> {
            Log.d(TAG, "Google Sign-In button clicked");
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });
        tvForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        btnChangeLanguage.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, LanguageSelectionActivity.class);
            startActivity(intent);
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + ", res=" + resultCode);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    Log.d(TAG, "Google success! Email: " + account.getEmail());
                    handleSupabaseAuth(account.getIdToken(), account.getEmail());
                } else {
                    Log.e(TAG, "Account or Token is null");
                    showError(getString(R.string.google_error_no_token));
                }
            } catch (ApiException e) {
                Log.e(TAG, "Google API Error: " + e.getStatusCode(), e);
                if (e.getStatusCode() == 12501) {
                    // 12501 is SIGN_IN_CANCELLED, common if user taps outside the dialog
                    Log.d(TAG, "Google Sign-In cancelled by user");
                    // Optionally clear any existing error text
                    tvError.setVisibility(View.GONE);
                } else {
                    showError(getString(R.string.google_failed, String.valueOf(e.getStatusCode())));
                }
            }
        }
    }

    private void handleSupabaseAuth(String idToken, String email) {
        Log.d(TAG, "Starting Supabase Auth for: " + email);
        showLoading(true);

        OkHttpClient client = new OkHttpClient();
        JSONObject json = new JSONObject();
        try {
            json.put("provider", "google");
            json.put("id_token", idToken);
        } catch (Exception e) {
            Log.e(TAG, "JSON error", e);
        }

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                json.toString()
        );

        Request request = new Request.Builder()
                .url("https://uhrolwwkxenvcefnessp.supabase.co/auth/v1/token?grant_type=id_token")
                .header("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVocm9sd3dreGVudmNlZm5lc3NwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzkxNjQwMjEsImV4cCI6MjA5NDc0MDAyMX0.3z5OUoIqetBI7OtyhlCrx-YxaIz1P1f6hUJ75BYzgxc")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network fail", e);
                runOnUiThread(() -> {
                    showLoading(false);
                    showError(getString(R.string.network_failed, e.getMessage()));
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                Log.d(TAG, "Supabase Response (" + response.code() + "): " + responseBody);
                
                runOnUiThread(() -> {
                    showLoading(false);
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String userId = jsonResponse.getJSONObject("user").getString("id");
                            String token = jsonResponse.optString("access_token", "");
                            String refreshToken = jsonResponse.optString("refresh_token", "");
                            Log.d(TAG, "Login Successful! Status: " + response.code());
                            
                            sessionManager.setLoggedIn(true);
                            sessionManager.setUserEmail(email);
                            sessionManager.setUserId(userId);
                            sessionManager.setSupabaseToken(token);
                            sessionManager.setRefreshToken(refreshToken);
                            supabaseAuth.setAuthToken(token);
                            
                            if (response.code() == 201) {
                                // New Google User created
                                navigateToMain(); // Will go to RegisterActivity
                            } else {
                                // Existing user
                                fetchProfileAndNavigate(userId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Parse error", e);
                            showError(getString(R.string.login_error_parse));
                        }
                    } else {
                        showError(getString(R.string.supabase_error, String.valueOf(response.code())));
                    }
                });
            }
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError(getString(R.string.email_required));
            return;
        }

        showLoading(true);
        supabaseAuth.loginWithEmail(email, password, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        String userId = json.getJSONObject("user").getString("id");
                        String token = json.getString("access_token");
                        String refreshToken = json.optString("refresh_token", "");

                        sessionManager.setLoggedIn(true);
                        sessionManager.setUserEmail(email);
                        sessionManager.setUserId(userId);
                        sessionManager.setSupabaseToken(token);
                        sessionManager.setRefreshToken(refreshToken);
                        supabaseAuth.setAuthToken(token);
                        
                        fetchProfileAndNavigate(userId);
                    } catch (Exception e) {
                        showLoading(false);
                        showError("Login error: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    showError(error);
                });
            }
        });
    }

    private void fetchProfileAndNavigate(String userId) {
        supabaseAuth.getUserProfile(userId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String profileJson) {
                runOnUiThread(() -> {
                    try {
                        JSONObject profile = new JSONObject(profileJson);
                        com.example.smartfarmer.models.User user = new com.example.smartfarmer.models.User();
                        user.setUserId(profile.getString("user_id"));
                        user.setFullName(profile.optString("full_name", ""));
                        user.setEmail(profile.optString("email", ""));
                        user.setPhoneNumber(profile.optString("phone_number", ""));
                        user.setDistrict(profile.optString("district", ""));
                        user.setAddress(profile.optString("address", ""));
                        user.setProfileImage(profile.optString("profile_image", ""));

                        sessionManager.saveUser(user);
                    } catch (Exception e) {
                        Log.e(TAG, "Profile parse error", e);
                    }
                    showLoading(false);
                    navigateToMain();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    navigateToMain(); // Go anyway, RegisterActivity will handle if incomplete
                });
            }
        });
    }

    private void navigateToMain() {
        Intent intent;
        if (!sessionManager.areBasicDetailsComplete()) {
            intent = new Intent(LoginActivity.this, RegisterActivity.class);
        } else if (sessionManager.getProfileImage().isEmpty() || sessionManager.getProfileImage().equalsIgnoreCase("null")) {
            intent = new Intent(LoginActivity.this, ProfileImageActivity.class);
        } else {
            intent = new Intent(LoginActivity.this, MainActivity.class);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        btnLogin.setEnabled(!show);
        btnGoogleSignIn.setEnabled(!show);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }
}