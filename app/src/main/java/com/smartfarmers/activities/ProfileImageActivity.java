package com.smartfarmers.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.Toast;
import com.smartfarmers.R;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.github.ybq.android.spinkit.SpinKitView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ProfileImageActivity extends BaseActivity {
    private ShapeableImageView ivProfileLarge;
    private FloatingActionButton fabEditImage;
    private MaterialButton btnContinue;
    private SpinKitView progressBar;
    private SessionManager sessionManager;
    private SupabaseAuthHelper supabaseAuth;
    private String selectedImageBase64 = null;
    private static final int PICK_IMAGE_REQUEST = 3001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = new SessionManager(this);
        supabaseAuth = new SupabaseAuthHelper(this);

        // Redirect if image already exists
        String existingImage = sessionManager.getProfileImage();
        boolean hasExistingImage = existingImage != null && !existingImage.trim().isEmpty() && !existingImage.equalsIgnoreCase("null");
        if (hasExistingImage) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_profile_image);

        // Enforce sequential update: Basic details MUST be filled before image
        if (!sessionManager.areBasicDetailsComplete()) {
            Toast.makeText(this, "Please complete your account details first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, RegisterActivity.class));
            finish();
            return;
        }

        ivProfileLarge = findViewById(R.id.ivProfileLarge);
        fabEditImage = findViewById(R.id.fabEditImage);
        btnContinue = findViewById(R.id.btnContinue);
        progressBar = findViewById(R.id.progressBar);

        fabEditImage.setOnClickListener(v -> pickImage());
        
        // Enforce image upload if missing
        if (!hasExistingImage) {
            btnContinue.setText("Upload Image to Continue");
            Toast.makeText(this, "Profile image is required", Toast.LENGTH_LONG).show();
        }

        btnContinue.setOnClickListener(v -> {
            String currentImage = sessionManager.getProfileImage();
            boolean hasImageNow = currentImage != null && !currentImage.trim().isEmpty() && !currentImage.equalsIgnoreCase("null");

            if (selectedImageBase64 != null) {
                saveImageAndNavigate();
            } else if (hasImageNow) {
                navigateToMain();
            } else {
                Toast.makeText(this, "Please select an image first", Toast.LENGTH_SHORT).show();
                pickImage();
            }
        });
    }

    @Override
    public void onBackPressed() {
        String existingImage = sessionManager.getProfileImage();
        boolean hasExistingImage = existingImage != null && !existingImage.trim().isEmpty() && !existingImage.equalsIgnoreCase("null");
        
        if (!hasExistingImage && selectedImageBase64 == null) {
            Toast.makeText(this, "Please upload a profile image to continue", Toast.LENGTH_SHORT).show();
            moveTaskToBack(true);
        } else {
            super.onBackPressed();
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                ivProfileLarge.setImageBitmap(bitmap);
                convertImageToBase64(bitmap);
                btnContinue.setText("Save and Finish");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void convertImageToBase64(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        byte[] bytes = baos.toByteArray();
        selectedImageBase64 = Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    private void saveImageAndNavigate() {
        showLoading(true);
        
        try {
            org.json.JSONObject updates = new org.json.JSONObject();
            updates.put("profile_image", selectedImageBase64);

            supabaseAuth.updateUserProfile(sessionManager.getUserId(), updates, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        sessionManager.setProfileImage(selectedImageBase64);
                        navigateToMain();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        showLoading(false);
                        Toast.makeText(ProfileImageActivity.this, "Error saving image: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (Exception e) {
            showLoading(false);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(ProfileImageActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showLoading(boolean show) {
        btnContinue.setEnabled(!show);
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
