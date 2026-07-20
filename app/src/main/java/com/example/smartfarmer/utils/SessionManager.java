package com.example.smartfarmer.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "SmartFarmerPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_EMAIL = "userEmail";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_SUPABASE_TOKEN = "supabaseToken";
    private static final String KEY_REFRESH_TOKEN = "refreshToken";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_PROFILE_IMAGE = "userProfileImage";
    private static final String KEY_LAST_EMAIL = "lastUserEmail";
    private static final String KEY_NOTIFICATIONS = "notificationsEnabled";
    private static final String KEY_OFFLINE_MODE = "offlineModeEnabled";
    private static final String KEY_DARK_MODE = "darkModeEnabled";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;
    private Context context;

    public SessionManager(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void setLoggedIn(boolean isLoggedIn) {
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public void setUserEmail(String email) {
        editor.putString(KEY_USER_EMAIL, email);
        editor.apply();
    }

    public String getUserEmail() {
        return sharedPreferences.getString(KEY_USER_EMAIL, null);
    }

    public void setUserId(String userId) {
        editor.putString(KEY_USER_ID, userId);
        editor.apply();
    }

    public void setSupabaseToken(String token) {
        editor.putString(KEY_SUPABASE_TOKEN, token);
        editor.apply();
    }

    public String getSupabaseToken() {
        return sharedPreferences.getString(KEY_SUPABASE_TOKEN, "");
    }

    public void setRefreshToken(String token) {
        editor.putString(KEY_REFRESH_TOKEN, token);
        editor.apply();
    }

    public String getRefreshToken() {
        return sharedPreferences.getString(KEY_REFRESH_TOKEN, "");
    }

    public void saveUser(com.example.smartfarmer.models.User user) {
        editor.putString(KEY_USER_ID, user.getUserId());
        editor.putString(KEY_USER_EMAIL, user.getEmail());
        editor.putString("userName", user.getFullName());
        editor.putString("userPhone", user.getPhoneNumber());
        editor.putString("userDistrict", user.getDistrict());
        editor.putString("userAddress", user.getAddress());
        editor.putString(KEY_PROFILE_IMAGE, user.getProfileImage());
        
        // Save as last user for login screen
        editor.putString(KEY_LAST_EMAIL, user.getEmail());
        
        editor.apply();
    }

    public void setProfileImage(String imageBase64) {
        editor.putString(KEY_PROFILE_IMAGE, imageBase64);
        editor.apply();
    }

    public String getProfileImage() {
        return sharedPreferences.getString(KEY_PROFILE_IMAGE, "");
    }

    public String getLastEmail() {
        return sharedPreferences.getString(KEY_LAST_EMAIL, "");
    }

    public String getUserPhone() {
        return sharedPreferences.getString("userPhone", "");
    }

    public String getUserDistrict() {
        return sharedPreferences.getString("userDistrict", "");
    }

    public String getUserAddress() {
        return sharedPreferences.getString("userAddress", "");
    }

    public String getUserName() {
        return sharedPreferences.getString("userName", "");
    }

    public String getUserId() {
        return sharedPreferences.getString(KEY_USER_ID, null);
    }

    public boolean isProfileComplete() {
        String name = getUserName();
        String phone = getUserPhone();
        String district = getUserDistrict();
        String address = getUserAddress();
        String image = getProfileImage();

        // Strict null and empty check for all mandatory fields
        if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase("null")) return false;
        if (phone == null || phone.trim().isEmpty() || phone.equalsIgnoreCase("null")) return false;
        if (district == null || district.trim().isEmpty() || district.equalsIgnoreCase("null")) return false;
        if (address == null || address.trim().isEmpty() || address.equalsIgnoreCase("null")) return false;
        if (image == null || image.trim().isEmpty() || image.equalsIgnoreCase("null")) return false;

        return true;
    }

    public boolean areBasicDetailsComplete() {
        String name = getUserName();
        String phone = getUserPhone();
        String district = getUserDistrict();
        String address = getUserAddress();

        if (name == null || name.trim().isEmpty() || name.equalsIgnoreCase("null")) return false;
        if (phone == null || phone.trim().isEmpty() || phone.equalsIgnoreCase("null")) return false;
        if (district == null || district.trim().isEmpty() || district.equalsIgnoreCase("null")) return false;
        if (address == null || address.trim().isEmpty() || address.equalsIgnoreCase("null")) return false;

        return true;
    }

    public void setLanguage(String language) {
        editor.putString(KEY_LANGUAGE, language);
        editor.apply();
    }

    public String getLanguage() {
        return sharedPreferences.getString(KEY_LANGUAGE, "en");
    }

    public boolean isFirstTime() {
        return !sharedPreferences.contains(KEY_LANGUAGE);
    }

    public void clearSession() {
        // Preserve global settings
        String currentLanguage = getLanguage();

        // Clear Room Database (Messages, Posts, Notifications)
        new Thread(() -> {
            try {
                com.example.smartfarmer.models.ChatDatabase db = com.example.smartfarmer.models.ChatDatabase.getInstance(context);
                db.messageDao().deleteAll();
                db.postDao().deleteAllPosts();
                db.notificationDao().deleteAll();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        editor.clear();
        // Restore preserved settings
        if (currentLanguage != null) {
            editor.putString(KEY_LANGUAGE, currentLanguage);
        }
        editor.apply();
    }

    public void setNotificationsEnabled(boolean enabled) {
        editor.putBoolean(KEY_NOTIFICATIONS, enabled);
        editor.apply();
    }

    public boolean isNotificationsEnabled() {
        return sharedPreferences.getBoolean(KEY_NOTIFICATIONS, true);
    }

    public void setOfflineModeEnabled(boolean enabled) {
        editor.putBoolean(KEY_OFFLINE_MODE, enabled);
        editor.apply();
    }

    public boolean isOfflineModeEnabled() {
        return sharedPreferences.getBoolean(KEY_OFFLINE_MODE, false);
    }

    public void setDarkModeEnabled(boolean enabled) {
        editor.putBoolean(KEY_DARK_MODE, enabled);
        editor.apply();
    }

    public boolean isDarkModeEnabled() {
        return sharedPreferences.getBoolean(KEY_DARK_MODE, false);
    }
}