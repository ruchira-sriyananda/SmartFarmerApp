package com.smartfarmers.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.smartfarmers.auth.AuthManager;
import com.smartfarmers.databinding.ActivityForgotPasswordBinding;
import com.smartfarmers.utils.ValidationUtils;

public class ForgotPasswordActivity extends AppCompatActivity {
    private ActivityForgotPasswordBinding binding;
    private AuthManager authManager;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authManager = new AuthManager(this);

        binding.btnSendOTP.setOnClickListener(v -> sendOTP());
        binding.btnVerifyOTP.setOnClickListener(v -> verifyOTP());
        binding.btnResetPassword.setOnClickListener(v -> resetPassword());
    }

    private void sendOTP() {
        android.util.Log.d("FORGOT_DEBUG", "sendOTP() called");
        userEmail = binding.etEmail.getText().toString().trim();
        if (!ValidationUtils.isValidEmail(userEmail)) {
            Toast.makeText(this, "Valid email required", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSendOTP.setEnabled(false);
        
        authManager.sendOTP(userEmail, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                android.util.Log.d("FORGOT_DEBUG", "onSuccess: " + message);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSendOTP.setEnabled(true);
                        binding.layoutStep1.setVisibility(View.GONE);
                        binding.layoutStep2.setVisibility(View.VISIBLE);
                        Toast.makeText(ForgotPasswordActivity.this, "OTP sent! Please check your email inbox and spam folder.", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                android.util.Log.e("FORGOT_DEBUG", "onError: " + error);
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnSendOTP.setEnabled(true);
                        
                        String displayError = error;
                        if (error.toLowerCase().contains("rate limit")) {
                            displayError = "You've requested too many codes recently. Please wait a few minutes before trying again or check your email for any previous codes.";
                        }
                        
                        showErrorDialog("Failed to send OTP", displayError);
                    }
                });
            }
        });
    }

    private void verifyOTP() {
        String otp = binding.etOTP.getText().toString().trim();
        if (otp.length() < 6) {
            binding.etOTP.setError("Enter verification code");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnVerifyOTP.setEnabled(false);
        
        authManager.verifyOTP(userEmail, otp, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnVerifyOTP.setEnabled(true);
                        binding.layoutStep2.setVisibility(View.GONE);
                        binding.layoutStep3.setVisibility(View.VISIBLE);
                        Toast.makeText(ForgotPasswordActivity.this, "OTP Verified Successfully", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnVerifyOTP.setEnabled(true);
                        showErrorDialog("Verification Failed", error);
                    }
                });
            }
        });
    }

    private void resetPassword() {
        String newPass = binding.etNewPassword.getText().toString().trim();
        String confirmPass = binding.etConfirmPassword.getText().toString().trim();

        if (newPass.length() < 6) {
            binding.etNewPassword.setError("Password must be at least 6 characters");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnResetPassword.setEnabled(false);
        
        authManager.resetPassword(userEmail, newPass, new AuthManager.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnResetPassword.setEnabled(true);
                        Toast.makeText(ForgotPasswordActivity.this, "Password reset successful! You can now login.", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(ForgotPasswordActivity.this, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    if (!isFinishing()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnResetPassword.setEnabled(true);
                        showErrorDialog("Reset Failed", error);
                    }
                });
            }
        });
    }

    private void showErrorDialog(String title, String message) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }
}
