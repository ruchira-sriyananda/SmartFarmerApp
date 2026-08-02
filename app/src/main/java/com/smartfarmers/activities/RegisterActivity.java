package com.smartfarmers.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;
import com.smartfarmers.R;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.User;
import com.smartfarmers.utils.SessionManager;
import com.smartfarmers.utils.ValidationUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.github.ybq.android.spinkit.SpinKitView;

public class RegisterActivity extends BaseActivity {
    private TextInputEditText etFullName, etEmail, etPhone, etAddress, etPassword;
    private AutoCompleteTextView etDistrict;
    private TextInputLayout tilFullName, tilEmail, tilPhone, tilDistrict, tilAddress, tilPassword;
    private MaterialButton btnRegister;
    private android.widget.ImageButton btnBack;
    private TextView tvLogin, tvTitle;
    private android.widget.LinearLayout layoutLoginLink;
    private SpinKitView progressBar;
    private SessionManager sessionManager;
    private SupabaseAuthHelper supabaseAuth;
    private boolean isCompletionMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        sessionManager = new SessionManager(this);
        supabaseAuth = new SupabaseAuthHelper(this);

        boolean isEditMode = getIntent().getBooleanExtra("is_edit_mode", false);

        // Redirect if already complete AND not explicitly in edit mode
        if (sessionManager.isLoggedIn() && sessionManager.areBasicDetailsComplete() && !isEditMode) {
            // Check if we need image
            if (sessionManager.getProfileImage().isEmpty() || sessionManager.getProfileImage().equalsIgnoreCase("null")) {
                startActivity(new Intent(this, ProfileImageActivity.class));
            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
            finish();
            return;
        }

        setContentView(R.layout.activity_register);
        
        initViews();
        setupDistrictSpinner();
        
        if (sessionManager.isLoggedIn()) {
            isCompletionMode = true;
            prepareCompletionMode();
        }

        btnRegister.setOnClickListener(v -> handleRegistration());
        tvLogin.setOnClickListener(v -> finish());
        btnBack.setOnClickListener(v -> finish());
    }

    private void initViews() {
        etFullName = findViewById(R.id.etFullName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etDistrict = findViewById(R.id.etDistrict);
        etAddress = findViewById(R.id.etAddress);
        etPassword = findViewById(R.id.etPassword);
        
        tilFullName = findViewById(R.id.tilFullName);
        tilEmail = findViewById(R.id.tilEmail);
        tilPhone = findViewById(R.id.tilPhone);
        tilDistrict = findViewById(R.id.tilDistrict);
        tilAddress = findViewById(R.id.tilAddress);
        tilPassword = findViewById(R.id.tilPassword);
        
        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);
        tvLogin = findViewById(R.id.tvLogin);
        tvTitle = findViewById(R.id.tvTitle);
        layoutLoginLink = findViewById(R.id.layoutLoginLink);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupDistrictSpinner() {
        String[] districts = {"Colombo", "Gampaha", "Kalutara", "Kandy", "Matale", "Nuwara Eliya", "Galle", "Matara", "Hambantota", "Jaffna", "Kilinochchi", "Mannar", "Vavuniya", "Mullaitivu", "Batticaloa", "Ampara", "Trincomalee", "Kurunegala", "Puttalam", "Anuradhapura", "Polonnaruwa", "Badulla", "Moneragala", "Ratnapura", "Kegalle"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, districts);
        etDistrict.setAdapter(adapter);
    }

    private void prepareCompletionMode() {
        tvTitle.setText(R.string.complete_your_profile);
        btnRegister.setText(R.string.save_details);
        layoutLoginLink.setVisibility(View.GONE);
        tilPassword.setVisibility(View.GONE);

        // Pre-fill existing details from session
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();
        String phone = sessionManager.getUserPhone();
        String district = sessionManager.getUserDistrict();
        String address = sessionManager.getUserAddress();

        if (name != null && !name.isEmpty()) etFullName.setText(name);
        if (email != null && !email.isEmpty()) {
            etEmail.setText(email);
            etEmail.setEnabled(false);
            tilEmail.setEnabled(false);
        }
        if (phone != null && !phone.isEmpty()) etPhone.setText(phone);
        if (district != null && !district.isEmpty()) {
            etDistrict.setText(district, false); // false to prevent filtering
        }
        if (address != null && !address.isEmpty()) etAddress.setText(address);
    }

    private void handleRegistration() {
        String name = etFullName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String district = etDistrict.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(name)) { tilFullName.setError(getString(R.string.name_required)); return; }
        if (!ValidationUtils.isValidEmail(email)) { tilEmail.setError(getString(R.string.invalid_email)); return; }
        if (TextUtils.isEmpty(phone)) { tilPhone.setError(getString(R.string.phone_required)); return; }
        if (TextUtils.isEmpty(district)) { tilDistrict.setError(getString(R.string.district_required)); return; }
        if (TextUtils.isEmpty(address)) { tilAddress.setError(getString(R.string.address_required)); return; }
        
        if (!isCompletionMode && TextUtils.isEmpty(password)) {
            tilPassword.setError(getString(R.string.password_required));
            return;
        }

        showLoading(true);

        if (isCompletionMode) {
            saveDetailsToSupabase(sessionManager.getUserId(), name, email, phone, district, address);
        } else {
            supabaseAuth.signUpWithEmail(email, password, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String userId) {
                    saveDetailsToSupabase(userId, name, email, phone, district, address);
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(RegisterActivity.this, error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private void saveDetailsToSupabase(String userId, String name, String email, String phone, String district, String address) {
        User user = new User();
        user.setUserId(userId);
        user.setFullName(name);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setDistrict(district);
        user.setAddress(address);

        supabaseAuth.saveUserDetails(user, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    showLoading(false);
                    sessionManager.setLoggedIn(true);
                    sessionManager.saveUser(user);
                    navigateToMain();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(RegisterActivity.this, getString(R.string.error_saving_profile, error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void navigateToMain() {
        Intent intent;
        String existingImage = sessionManager.getProfileImage();
        boolean hasExistingImage = existingImage != null && !existingImage.trim().isEmpty() && !existingImage.equalsIgnoreCase("null");

        if (hasExistingImage) {
            intent = new Intent(RegisterActivity.this, MainActivity.class);
        } else {
            intent = new Intent(RegisterActivity.this, ProfileImageActivity.class);
        }

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        btnRegister.setEnabled(!show);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
