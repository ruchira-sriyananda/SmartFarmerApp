package com.example.smartfarmer.auth;

import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import android.content.Context;
import com.example.smartfarmer.utils.SessionManager;

public class SupabaseAuthHelper {
    private static final String TAG = "SupabaseAuth";
    private static final String SUPABASE_URL = "https://uhrolwwkxenvcefnessp.supabase.co";
    private static final String SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVocm9sd3dreGVudmNlZm5lc3NwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzkxNjQwMjEsImV4cCI6MjA5NDc0MDAyMX0.3z5OUoIqetBI7OtyhlCrx-YxaIz1P1f6hUJ75BYzgxc";
    private OkHttpClient client;
    private String authToken = null;

    private Context context;

    public SupabaseAuthHelper() {
        this(null);
    }

    public SupabaseAuthHelper(Context context) {
        this.context = context;
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        if (context != null) {
            builder.addInterceptor(chain -> {
                Request originalRequest = chain.request();
                Response response = chain.proceed(originalRequest);

                // If unauthorized and looks like a JWT expiry
                if (response.code() == 401 && !originalRequest.url().toString().contains("/auth/v1/token")) {
                    String body = response.peekBody(2048).string();
                    if (body.contains("JWT expired") || body.contains("invalid_token")) {
                        response.close();
                        
                        SessionManager sm = new SessionManager(context);
                        String refreshToken = sm.getRefreshToken();
                        
                        if (refreshToken != null && !refreshToken.isEmpty()) {
                            // Synchronous refresh
                            String newToken = performSyncRefresh(refreshToken);
                            if (newToken != null) {
                                Request newRequest = originalRequest.newBuilder()
                                        .header("Authorization", "Bearer " + newToken)
                                        .build();
                                return chain.proceed(newRequest);
                            }
                        }
                    }
                }
                return response;
            });
            
            SessionManager sessionManager = new SessionManager(context);
            this.authToken = sessionManager.getSupabaseToken();
        }
        this.client = builder.build();
    }

    private String performSyncRefresh(String refreshToken) {
        try {
            JSONObject json = new JSONObject();
            json.put("refresh_token", refreshToken);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request refreshRequest = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .post(body).build();

            // We use a separate client without interceptor for the refresh call to avoid recursion
            OkHttpClient basicClient = new OkHttpClient();
            Response response = basicClient.newCall(refreshRequest).execute();
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseBody);
                String newToken = jsonResponse.getString("access_token");
                String newRefreshToken = jsonResponse.optString("refresh_token", "");
                
                this.authToken = newToken;
                if (context != null) {
                    SessionManager sm = new SessionManager(context);
                    sm.setSupabaseToken(newToken);
                    if (!newRefreshToken.isEmpty()) {
                        sm.setRefreshToken(newRefreshToken);
                    }
                }
                return newToken;
            }
        } catch (Exception e) {
            Log.e(TAG, "Sync refresh failed", e);
        }
        return null;
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    private String getAuthHeader() {
        return (authToken != null && !authToken.isEmpty()) ? "Bearer " + authToken : "Bearer " + SUPABASE_ANON_KEY;
    }

    public interface AuthCallback {
        void onSuccess(String data);
        void onError(String error);
    }

    private String parseSupabaseError(String responseBody, int statusCode) {
        try {
            if (responseBody == null || responseBody.isEmpty()) return "Error code: " + statusCode;
            JSONObject json = new JSONObject(responseBody);
            if (json.has("error_description")) return json.getString("error_description");
            if (json.has("msg")) return json.getString("msg");
            if (json.has("code")) {
                String code = json.getString("code");
                String message = json.optString("message", "");
                String details = json.optString("details", "");
                String hint = json.optString("hint", "");

                StringBuilder sb = new StringBuilder();
                switch (code) {
                    case "23505": sb.append("Record already exists. "); break;
                    case "23503": sb.append("Foreign key violation (Check if ID exists). "); break;
                    case "23502": sb.append("Missing required information. "); break;
                    case "P0001": sb.append("Database function error. "); break;
                    default: sb.append(message.isEmpty() ? "Database error (" + code + ") " : message + " ");
                }
                if (!details.isEmpty()) sb.append("\nDetails: ").append(details);
                if (!hint.isEmpty()) sb.append("\nHint: ").append(hint);
                return sb.toString().trim();
            }
            if (json.has("message")) return json.getString("message");
        } catch (Exception e) {
            Log.e(TAG, "Error parsing error response", e);
        }
        return "Error occurred (Status " + statusCode + "): " + responseBody;
    }

    // --- Authentication ---

    public void loginWithEmail(String email, String password, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=password")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        callback.onSuccess(responseBody);
                    } else { callback.onError(parseSupabaseError(responseBody, response.code())); }
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void refreshSession(String refreshToken, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("refresh_token", refreshToken);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/token?grant_type=refresh_token")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String newToken = jsonResponse.getString("access_token");
                            String newRefreshToken = jsonResponse.optString("refresh_token", "");
                            
                            authToken = newToken;
                            if (context != null) {
                                SessionManager sm = new SessionManager(context);
                                sm.setSupabaseToken(newToken);
                                if (!newRefreshToken.isEmpty()) {
                                    sm.setRefreshToken(newRefreshToken);
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse refresh response", e);
                        }
                        callback.onSuccess(responseBody);
                    } else { callback.onError(parseSupabaseError(responseBody, response.code())); }
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void signUpWithEmail(String email, String password, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/signup")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    if (response.isSuccessful()) {
                        try {
                            JSONObject res = new JSONObject(responseBody);
                            String id = res.has("id") ? res.getString("id") : res.getJSONObject("user").getString("id");
                            
                            // Try to get and save tokens if they exist (auto-confirm enabled)
                            String token = res.optString("access_token", "");
                            String refreshToken = res.optString("refresh_token", "");
                            if (!token.isEmpty()) {
                                authToken = token;
                                if (context != null) {
                                    SessionManager sm = new SessionManager(context);
                                    sm.setSupabaseToken(token);
                                    if (!refreshToken.isEmpty()) {
                                        sm.setRefreshToken(refreshToken);
                                    }
                                }
                            }

                            callback.onSuccess(id);
                        } catch (Exception e) { callback.onError("Parse error"); }
                    } else { callback.onError(parseSupabaseError(responseBody, response.code())); }
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void saveUserDetails(com.example.smartfarmer.models.User user, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", user.getUserId());
            json.put("full_name", user.getFullName());
            json.put("email", user.getEmail());
            json.put("phone_number", user.getPhoneNumber());
            json.put("district", user.getDistrict());
            json.put("address", user.getAddress());
            json.put("profile_image", user.getProfileImage());
            json.put("status", "active");
            json.put("is_verified", false);
            json.put("role_id", JSONObject.NULL);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .header("Prefer", "resolution=merge-duplicates, on_conflict=user_id")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess(user.getUserId());
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void sendResetPasswordOTP(String email, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            // Use 'recovery' for password reset OTP
            json.put("type", "recovery");

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    json.toString()
            );

            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/otp")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Content-Type", "application/json")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error: " + e.getMessage()); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body().string();
                    Log.d(TAG, "OTP Response: " + responseBody);
                    if (response.isSuccessful()) {
                        callback.onSuccess("OTP sent successfully");
                    } else {
                        callback.onError(parseSupabaseError(responseBody, response.code()));
                    }
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void verifyOTP(String email, String token, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("token", token);
            json.put("type", "recovery");
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/verify")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String res = response.body().string();
                    if (response.isSuccessful()) {
                        try { callback.onSuccess(new JSONObject(res).getString("access_token")); }
                        catch (Exception e) { callback.onError("Session error"); }
                    } else callback.onError(parseSupabaseError(res, response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void updatePassword(String token, String newPass, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("password", newPass);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/auth/v1/user")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", "Bearer " + token)
                    .put(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    // --- Profile & Users ---

    public void updateUserProfile(String userId, JSONObject updates, AuthCallback callback) {
        try {
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/users?user_id=eq." + userId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void getUserProfile(String userId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/users?user_id=eq." + userId + "&select=*")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) callback.onSuccess(arr.getJSONObject(0).toString());
                        else callback.onError("Profile not found");
                    } catch (Exception e) { callback.onError("Parse error"); }
                } else callback.onError("Fetch error");
            }
        });
    }

    public void getProfileImageByEmail(String email, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/users?email=eq." + email + "&select=profile_image")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) callback.onSuccess(arr.getJSONObject(0).optString("profile_image", ""));
                        else callback.onError("Not found");
                    } catch (Exception e) { callback.onError("Parse error"); }
                } else callback.onError("Fetch error");
            }
        });
    }

    public void fetchAllUsers(AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/users?select=*")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    // --- Posts & Social ---

    public void fetchPosts(String currentUserId, AuthCallback callback) {
        fetchPostsPaginated(currentUserId, 0, 200, callback);
    }

    public void fetchPostsPaginated(String currentUserId, int offset, int limit, AuthCallback callback) {
        // Optimization: Specify columns to reduce JSON size
        String select = "post_id,user_id,title,content,image_url,created_at,likes_count,comments_count,shares_count," +
                "users(full_name,profile_image),post_likes(user_id)";

        String url = SUPABASE_URL + "/rest/v1/posts?select=" + select;
        if (currentUserId != null && !currentUserId.isEmpty()) {
            url += "&post_likes.user_id=eq." + currentUserId;
        }
        url += "&order=created_at.desc&offset=" + offset + "&limit=" + limit;

        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(responseBody);
                else callback.onError(parseSupabaseError(responseBody, response.code()));
            }
        });
    }

    public void createPost(String userId, String content, String imageUrl, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("content", content);
            json.put("image_url", imageUrl != null ? imageUrl : "");
            json.put("visibility_status", "public");

            // Add required title (default to first few words of content)
            String title = content.length() > 30 ? content.substring(0, 27) + "..." : content;
            if (title.isEmpty()) title = "Post by " + userId.substring(0, 5);
            json.put("title", title);

            // Add required timestamps
            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            json.put("created_at", currentTime);
            json.put("updated_at", currentTime);

            // Interaction defaults
            json.put("likes_count", 0);
            json.put("comments_count", 0);
            json.put("shares_count", 0);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/posts")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Post created");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void updatePost(String postId, String content, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("content", content);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/posts?post_id=eq." + postId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Post updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void deletePost(String postId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/posts?post_id=eq." + postId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Post deleted");
                else callback.onError("Failed to delete post");
            }
        });
    }

    public void togglePostLike(String postId, String userId, boolean isLiked, int newLikesCount, AuthCallback callback) {
        if (isLiked) {
            try {
                JSONObject json = new JSONObject();
                json.put("post_id", postId);
                json.put("user_id", userId);
                RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/post_likes")
                        .header("apikey", SUPABASE_ANON_KEY)
                        .header("Authorization", getAuthHeader())
                        .post(body).build();
                client.newCall(request).enqueue(new Callback() {
                    @Override public void onFailure(Call call, IOException e) {}
                    @Override public void onResponse(Call call, Response response) throws IOException { response.close(); }
                });
            } catch (Exception e) {}
        } else {
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/post_likes?post_id=eq." + postId + "&user_id=eq." + userId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .delete().build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {}
                @Override public void onResponse(Call call, Response response) throws IOException { response.close(); }
            });
        }
        updatePostInteractions(postId, "likes_count", newLikesCount, callback);
    }

    public void addComment(String postId, String userId, String content, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("post_id", postId);
            json.put("user_id", userId);
            json.put("comment_text", content);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/post_comments")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Comment added");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void fetchComments(String postId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/post_comments?post_id=eq." + postId + "&select=*,users(full_name,profile_image)&order=created_at.asc";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void updatePostInteractions(String postId, String column, int newValue, AuthCallback callback) {
        try {
            JSONObject updates = new JSONObject();
            updates.put(column, newValue);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/posts?post_id=eq." + postId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void fetchUserPosts(String userId, String currentUserId, AuthCallback callback) {
        String select = "*,users(full_name,profile_image),post_likes(user_id)";
        String url = SUPABASE_URL + "/rest/v1/posts?user_id=eq." + userId + "&select=" + select;
        if (currentUserId != null && !currentUserId.isEmpty()) {
            url += "&post_likes.user_id=eq." + currentUserId;
        }
        url += "&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void deleteComment(String commentId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/post_comments?comment_id=eq." + commentId)
                .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).delete().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Deleted");
                else callback.onError("Failed to delete comment");
            }
        });
    }

    public void updateComment(String commentId, String newContent, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("comment_text", newContent);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/post_comments?comment_id=eq." + commentId)
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).patch(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void updateMessage(String messageId, String newContent, String seed, AuthCallback callback) {
        if (messageId == null || messageId.isEmpty() || messageId.equalsIgnoreCase("null")) {
            callback.onError("Invalid message ID");
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("message_text", com.example.smartfarmer.utils.EncryptionUtils.encrypt(newContent, seed));
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/messages?message_id=eq." + messageId)
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).patch(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Message updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void deleteMessage(String messageId, AuthCallback callback) {
        if (messageId == null || messageId.isEmpty() || messageId.equalsIgnoreCase("null")) {
            callback.onError("Invalid message ID");
            return;
        }
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/messages?message_id=eq." + messageId)
                .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).delete().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Message deleted");
                else callback.onError("Failed to delete message");
            }
        });
    }

    // --- Marketplace & Ads ---

    public void fetchMarketProducts(AuthCallback callback) {
        fetchMarketProductsWithLimit(0, callback);
    }

    public void fetchMarketProductsWithLimit(int limit, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/mobile_advertisements?status=eq.active&select=*&order=created_at.desc";
        if (limit > 0) {
            url += "&limit=" + limit;
        }
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchActiveAds(AuthCallback callback) {
        fetchMarketProductsWithLimit(10, callback);
    }

    public void recordAdInteraction(String adId, String column, int currentValue, AuthCallback callback) {
        try {
            JSONObject updates = new JSONObject();
            updates.put(column, currentValue + 1);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/mobile_advertisements?ad_id=eq." + adId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError("Update failed");
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void addProduct(JSONObject adJson, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), adJson.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/mobile_advertisements")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Product added");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void updateMobileAd(String adId, JSONObject updates, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/mobile_advertisements?ad_id=eq." + adId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .patch(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Updated");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void fetchSubscriptionPackages(AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/subscription_packages?is_active=eq.true&select=*&order=display_order.asc";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void recordTransaction(JSONObject transJson, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), transJson.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/payment_transactions")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Recorded");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void fetchUserOrders(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/payment_transactions?user_id=eq." + userId + "&select=*,mobile_advertisements!ad_id(*)&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchSellerOrders(String sellerId, AuthCallback callback) {
        // Fetch transactions where the ad belongs to the seller
        String url = SUPABASE_URL + "/rest/v1/payment_transactions?select=*,mobile_advertisements!ad_id!inner(*)&mobile_advertisements.user_id=eq." + sellerId + "&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void updateOrderStatus(String transactionId, String newStatus, AuthCallback callback) {
        try {
            JSONObject updates = new JSONObject();
            updates.put("status", newStatus);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/payment_transactions?transaction_id=eq." + transactionId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    // --- Community & Group Management ---

    public void createChatRoom(JSONObject roomJson, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), roomJson.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/chat_rooms")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .header("Prefer", "return=representation")
                .post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject createdRoom = arr.getJSONObject(0);
                            String roomId = createdRoom.getString("room_id");
                            String userId = createdRoom.getString("created_by");
                            addParticipant(roomId, userId, "joined", "admin", callback);
                        } else {
                            callback.onError("Failed to create room: No data returned");
                        }
                    } catch (Exception e) {
                        // Fallback to original roomId if parsing fails
                        try {
                            String roomId = roomJson.getString("room_id");
                            String userId = roomJson.getString("created_by");
                            addParticipant(roomId, userId, "joined", "admin", callback);
                        } catch (Exception ex) { callback.onError("Parsing error: " + e.getMessage()); }
                    }
                } else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchMyJoinRequests(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/chat_participants?user_id=eq." + userId + "&status=eq.pending&select=*";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void addParticipant(String roomId, String userId, String status, String role, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("room_id", roomId);
            json.put("user_id", userId);
            json.put("status", status);
            json.put("role", role);

            // Add joined_at timestamp as it might be required (NOT NULL)
            String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(new java.util.Date());
            json.put("joined_at", currentTime);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/chat_participants")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .header("Prefer", "resolution=merge-duplicates, on_conflict=room_id,user_id")
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error: " + e.getMessage()); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful() || response.code() == 201 || response.code() == 204 || response.code() == 409) {
                        callback.onSuccess(roomId);
                    } else {
                        callback.onError(parseSupabaseError(responseBody, response.code()));
                    }
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void fetchMyGroups(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/chat_participants?user_id=eq." + userId + "&status=eq.joined&select=*,chat_rooms(*)";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchDiscoverGroups(AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/chat_rooms?is_group=eq.true&is_public=eq.true&select=*";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchPersonalChatList(String userId, AuthCallback callback) {
        // Only fetch messages where the user is either sender or receiver
        // We filter for personal chats by ensuring receiver_id does NOT start with GRP-
        String filter = "and=(or(sender_id.eq." + userId + ",receiver_id.eq." + userId + "),receiver_id.not.like.GRP-*)";
        String url = SUPABASE_URL + "/rest/v1/messages?" + filter + "&select=*&order=sent_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchDirectMessages(String myId, String otherId, AuthCallback callback) {
        String filter = "or=(and(sender_id.eq." + myId + ",receiver_id.eq." + otherId + ")," +
                        "and(sender_id.eq." + otherId + ",receiver_id.eq." + myId + "))";
        String url = SUPABASE_URL + "/rest/v1/messages?" + filter + "&select=*&order=sent_at.asc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void sendDirectMessage(JSONObject msgJson, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), msgJson.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/messages").header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).post(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Sent");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void fetchRoomMessages(String roomId, AuthCallback callback) {
        // For groups, we fetch messages where receiver_id matches the group ID
        String url = SUPABASE_URL + "/rest/v1/messages?receiver_id=eq." + roomId + "&select=*&order=sent_at.asc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchGroupMembers(String roomId, AuthCallback callback) {
        // Fetch only joined participants
        String url = SUPABASE_URL + "/rest/v1/chat_participants?room_id=eq." + roomId + "&status=eq.joined&select=*";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchUsersByIds(java.util.List<String> userIds, AuthCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            callback.onSuccess("[]");
            return;
        }

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < userIds.size(); i++) {
            ids.append(userIds.get(i));
            if (i < userIds.size() - 1) ids.append(",");
        }

        String url = SUPABASE_URL + "/rest/v1/users?user_id=in.(" + ids.toString() + ")&select=*";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchChatRoomDetails(String roomId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/chat_rooms?room_id=eq." + roomId + "&select=*";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) {
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) callback.onSuccess(arr.getJSONObject(0).toString());
                        else callback.onError("Room not found");
                    } catch (Exception e) { callback.onError("Parsing error"); }
                } else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void removeParticipant(String roomId, String userId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/chat_participants?room_id=eq." + roomId + "&user_id=eq." + userId)
                .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Removed");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void deleteChatRoom(String roomId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/chat_rooms?room_id=eq." + roomId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Deleted");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void deleteRoomMessages(String roomId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/messages?receiver_id=eq." + roomId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Messages deleted");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void deleteRoomParticipants(String roomId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/chat_participants?room_id=eq." + roomId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Participants removed");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    public void updateChatRoom(String roomId, JSONObject updates, AuthCallback callback) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/chat_rooms?room_id=eq." + roomId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .patch(body).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Updated");
                else callback.onError(parseSupabaseError(response.body().string(), response.code()));
            }
        });
    }

    // --- Barter System ---

    public void fetchBarterListings(AuthCallback callback) {
        // Only fetch listings that are marked as 'Available'
        String url = SUPABASE_URL + "/rest/v1/barter_listings?status=eq.Available&select=*,users(full_name,profile_image,district)&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchMyBarterListings(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/barter_listings?user_id=eq." + userId + "&select=*,users(full_name,profile_image,district)&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void createBarterListing(com.example.smartfarmer.models.BarterListing listing, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", listing.getUserId());
            json.put("title", listing.getTitle());
            json.put("description", listing.getDescription());
            json.put("quantity", listing.getQuantity());
            json.put("unit", listing.getUnit());
            json.put("image_url", listing.getImageUrl());

            // Using capitalized values as they might be required by the DB constraints
            json.put("status", "Available");
            json.put("moderation_status", "Pending");

            if (listing.getType() != null) {
                json.put("type", listing.getType()); // Already capitalized from Activity
            } else {
                json.put("type", "Goods");
            }

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/barter_listings")
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Listing created");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void createBarterRequest(com.example.smartfarmer.models.BarterRequest barterReq, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("listing_id", barterReq.getListingId());
            json.put("requester_id", barterReq.getRequesterId());
            json.put("offered_item", barterReq.getOfferedItem());
            json.put("request_status", "pending");

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/barter_requests")
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).post(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Request sent");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void fetchRequestsForMyListings(String userId, AuthCallback callback) {
        // First get listing IDs owned by the user
        String url = SUPABASE_URL + "/rest/v1/barter_requests?select=*,users:requester_id(full_name,profile_image),barter_listings!inner(user_id,title)&barter_listings.user_id=eq." + userId + "&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void fetchMyBarterRequests(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/barter_requests?requester_id=eq." + userId + "&select=*,barter_listings(title,user_id)&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url).header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).get().build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void updateBarterRequestStatus(String requestId, String status, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("request_status", status);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/barter_requests?request_id=eq." + requestId)
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).patch(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void updateListingStatus(String listingId, String status, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("status", status);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/barter_listings?id=eq." + listingId)
                    .header("apikey", SUPABASE_ANON_KEY).header("Authorization", getAuthHeader()).patch(body).build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Listing status updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    // --- Notifications ---

    public void fetchNotifications(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/notifications?user_id=eq." + userId + "&select=*&order=created_at.desc";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    public void getUnreadNotificationsCount(String userId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/notifications?user_id=eq." + userId + "&is_read=eq.false&select=count";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .header("Prefer", "count=exact")
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String countStr = response.header("Content-Range");
                    if (countStr != null && countStr.contains("/")) {
                        callback.onSuccess(countStr.split("/")[1]);
                    } else {
                        callback.onSuccess("0");
                    }
                } else {
                    callback.onError("Failed to get count");
                }
            }
        });
    }

    public void createNotification(String userId, String title, String message, String type, String relatedId, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("user_id", userId);
            json.put("notification_title", title);
            json.put("notification_message", message);
            
            // If the DB doesn't have related_id, we append it to the type
            String typeWithTypeInfo = type;
            if (relatedId != null && !relatedId.isEmpty()) {
                typeWithTypeInfo = type + ":" + relatedId;
            }
            json.put("notification_type", typeWithTypeInfo);

            json.put("is_read", false);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/notifications")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Notification created");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void markNotificationAsRead(String notificationId, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("is_read", true);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/notifications?notification_id=eq." + notificationId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Marked as read");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void deleteAllNotifications(String userId, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/notifications?user_id=eq." + userId)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Deleted all");
                else callback.onError("Failed to delete");
            }
        });
    }

    public void deleteNotification(String id, AuthCallback callback) {
        Request request = new Request.Builder()
                .url(SUPABASE_URL + "/rest/v1/notifications?notification_id=eq." + id)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .delete().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) callback.onSuccess("Deleted");
                else callback.onError("Failed to delete");
            }
        });
    }

    public void updateParticipantStatus(String roomId, String userId, String status, AuthCallback callback) {
        try {
            JSONObject updates = new JSONObject();
            updates.put("status", status);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), updates.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/rest/v1/chat_participants?room_id=eq." + roomId + "&user_id=eq." + userId)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .patch(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) callback.onSuccess("Updated");
                    else callback.onError(parseSupabaseError(response.body().string(), response.code()));
                }
            });
        } catch (Exception e) { callback.onError(e.getMessage()); }
    }

    public void fetchPendingParticipants(String roomId, AuthCallback callback) {
        String url = SUPABASE_URL + "/rest/v1/chat_participants?room_id=eq." + roomId + "&status=eq.pending&select=*";
        Request request = new Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", getAuthHeader())
                .get().build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { callback.onError("Network error"); }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                if (response.isSuccessful()) callback.onSuccess(body);
                else callback.onError(parseSupabaseError(body, response.code()));
            }
        });
    }

    // --- Stripe Integration ---

    /**
     * Initiates a Stripe payment by calling a backend service (e.g., Supabase Edge Function).
     * This is the secure way to handle payments without exposing the Secret Key in the app.
     */
    public void initiateStripePayment(double amount, String currency, String description, AuthCallback callback) {
        try {
            JSONObject json = new JSONObject();
            json.put("amount", (long) (amount * 100)); // Amount in cents
            json.put("currency", currency.toLowerCase());
            json.put("description", description);

            RequestBody body = RequestBody.create(MediaType.parse("application/json"), json.toString());
            Request request = new Request.Builder()
                    .url(SUPABASE_URL + "/functions/v1/stripe-payment")
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header("Authorization", getAuthHeader())
                    .post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) { callback.onError("Network failure: " + e.getMessage()); }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String body = response.body().string();
                    if (response.isSuccessful()) {
                        callback.onSuccess(body);
                    } else {
                        callback.onError("Backend error: " + parseSupabaseError(body, response.code()));
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request creation failed: " + e.getMessage());
        }
    }
}
