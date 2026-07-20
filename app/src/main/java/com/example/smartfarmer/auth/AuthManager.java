package com.example.smartfarmer.auth;

import android.content.Context;
import com.example.smartfarmer.models.User;
import com.example.smartfarmer.utils.SessionManager;

public class AuthManager {
    private Context context;
    private SupabaseAuthHelper supabaseHelper;
    private SessionManager sessionManager;

    public interface AuthCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public AuthManager(Context context) {
        this.context = context;
        this.supabaseHelper = new SupabaseAuthHelper(context);
        this.sessionManager = new SessionManager(context);
    }

    public void signUp(User user, String password, AuthCallback callback) {
        supabaseHelper.signUpWithEmail(user.getEmail(), password, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                user.setUserId(userId);
                // Important: Update session with email and ID immediately
                sessionManager.setUserEmail(user.getEmail());
                sessionManager.setUserId(userId);
                
                // After auth signup, save profile details to 'users' table
                saveProfile(user, callback);
            }

            @Override
            public void onError(String error) {
                // If user already exists in Auth but not in table, try saving profile anyway
                if (error.contains("already registered") || error.contains("already exists")) {
                    // We don't have the UUID here easily without a login, 
                    // but usually, this error means they should just log in.
                    callback.onError("User already exists. Please login.");
                } else {
                    callback.onError(error);
                }
            }
        });
    }

    private void saveProfile(User user, AuthCallback callback) {
        supabaseHelper.saveUserDetails(user, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                sessionManager.setLoggedIn(true);
                sessionManager.saveUser(user);
                callback.onSuccess("Registration Successful");
            }

            @Override
            public void onError(String error) {
                // Return the specific error from Supabase (e.g., table constraint)
                callback.onError("Account created, but profile failed: " + error);
            }
        });
    }

    public void login(String email, String password, AuthCallback callback) {
        supabaseHelper.loginWithEmail(email, password, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String userId) {
                // After login, fetch the full profile
                fetchProfile(userId, callback);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    private void fetchProfile(String userId, AuthCallback callback) {
        supabaseHelper.getUserProfile(userId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonProfile) {
                try {
                    org.json.JSONObject json = new org.json.JSONObject(jsonProfile);
                    User user = new User();
                    user.setUserId(json.getString("user_id"));
                    user.setEmail(json.getString("email"));
                    user.setFullName(json.optString("full_name", ""));
                    user.setPhoneNumber(json.optString("phone_number", ""));
                    user.setDistrict(json.optString("district", ""));
                    user.setAddress(json.optString("address", ""));
                    
                    sessionManager.setLoggedIn(true);
                    sessionManager.saveUser(user);
                    callback.onSuccess("Login Successful");
                } catch (Exception e) {
                    callback.onError("Error saving profile details locally");
                }
            }

            @Override
            public void onError(String error) {
                // If profile fetch fails, we still have the userId/Email from login
                sessionManager.setLoggedIn(true);
                sessionManager.setUserId(userId);
                callback.onSuccess("Login Successful (Profile sync delayed)");
            }
        });
    }

    public void signUp(String email, String password, AuthCallback callback) {
        // Kept for backward compatibility or simple email-only signup if needed
        User user = new User();
        user.setEmail(email);
        signUp(user, password, callback);
    }

    private String userAccessToken;

    public void sendOTP(String email, AuthCallback callback) {
        supabaseHelper.sendResetPasswordOTP(email, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                callback.onSuccess(message);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void verifyOTP(String email, String otp, AuthCallback callback) {
        supabaseHelper.verifyOTP(email, otp, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String accessToken) {
                userAccessToken = accessToken;
                callback.onSuccess("OTP Verified");
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }

    public void resetPassword(String email, String newPassword, AuthCallback callback) {
        if (userAccessToken == null) {
            callback.onError("Session expired. Please verify OTP again.");
            return;
        }
        supabaseHelper.updatePassword(userAccessToken, newPassword, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String message) {
                userAccessToken = null;
                callback.onSuccess(message);
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        });
    }
}
