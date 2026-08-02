package com.smartfarmers.models;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.smartfarmers.auth.SupabaseAuthHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostRepository {
    private final PostDao postDao;
    private final SupabaseAuthHelper supabaseAuth;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface PostCallback {
        void onPostsLoaded(List<Post> posts);
        void onError(String error);
    }

    public PostRepository(Context context) {
        this.postDao = ChatDatabase.getInstance(context).postDao();
        this.supabaseAuth = new SupabaseAuthHelper(context);
    }

    public void getPosts(String userId, PostCallback callback) {
        getPostsPaginated(userId, 0, 1000, callback);
    }

    public void getPostsPaginated(String userId, int offset, int limit, PostCallback callback) {
        // 1. Load from Cache immediately in parallel (only for first page)
        if (offset == 0) {
            executor.execute(() -> {
                try {
                    List<PostEntity> cachedEntities = postDao.getAllPosts();
                    if (!cachedEntities.isEmpty()) {
                        List<Post> cachedPosts = convertToModels(cachedEntities);
                        mainHandler.post(() -> callback.onPostsLoaded(cachedPosts));
                    }
                } catch (android.database.sqlite.SQLiteBlobTooBigException e) {
                    postDao.deleteAllPosts();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        // 2. Fetch from Network in parallel
        supabaseAuth.fetchPostsPaginated(userId, offset, limit, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                executor.execute(() -> {
                    try {
                        List<Post> networkPosts = parseJsonResponse(jsonResponse, userId);
                        // 3. Update Cache (only for first page to keep it clean)
                        if (offset == 0) {
                            postDao.deleteAllPosts();
                            postDao.insertPosts(convertToEntities(networkPosts));
                        }
                        // 4. Update UI
                        mainHandler.post(() -> callback.onPostsLoaded(networkPosts));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Parse error: " + e.getMessage()));
                    }
                });
            }

            @Override
            public void onError(String error) {
                mainHandler.post(() -> callback.onError(error));
            }
        });
    }

    private List<Post> parseJsonResponse(String json, String currentUserId) throws Exception {
        JSONArray arr = new JSONArray(json);
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            Post post = new Post();
            post.setPostId(obj.getString("post_id"));
            post.setUserId(obj.getString("user_id"));
            post.setTitle(obj.optString("title", ""));
            post.setContent(obj.optString("content", ""));
            post.setImageUrl(obj.optString("image_url", ""));
            post.setCreatedAt(obj.optString("created_at", ""));
            post.setLikesCount(obj.optInt("likes_count", 0));
            post.setCommentsCount(obj.optInt("comments_count", 0));
            post.setSharesCount(obj.optInt("shares_count", 0));

            if (obj.has("post_likes") && !obj.isNull("post_likes")) {
                JSONArray likes = obj.getJSONArray("post_likes");
                boolean likedByMe = false;
                for (int j = 0; j < likes.length(); j++) {
                    if (likes.getJSONObject(j).optString("user_id", "").equals(currentUserId)) {
                        likedByMe = true;
                        break;
                    }
                }
                post.setLiked(likedByMe);
            }

            if (obj.has("users") && !obj.isNull("users")) {
                JSONObject userObj = obj.getJSONObject("users");
                post.setUserName(userObj.optString("full_name", "Anonymous Farmer"));
                post.setUserProfileImage(userObj.optString("profile_image", ""));
            }
            posts.add(post);
        }
        return posts;
    }

    private List<Post> convertToModels(List<PostEntity> entities) {
        List<Post> models = new ArrayList<>();
        for (PostEntity e : entities) {
            Post m = new Post();
            m.setPostId(e.getPostId());
            m.setUserId(e.getUserId());
            m.setTitle(e.getTitle());
            m.setContent(e.getContent());
            m.setImageUrl(e.getImageUrl());
            m.setCreatedAt(e.getCreatedAt());
            m.setLikesCount(e.getLikesCount());
            m.setCommentsCount(e.getCommentsCount());
            m.setSharesCount(e.getSharesCount());
            m.setLiked(e.isLiked());
            m.setUserName(e.getUserName());
            m.setUserProfileImage(e.getUserProfileImage());
            models.add(m);
        }
        return models;
    }

    private List<PostEntity> convertToEntities(List<Post> models) {
        List<PostEntity> entities = new ArrayList<>();
        for (Post m : models) {
            PostEntity e = new PostEntity();
            e.setPostId(m.getPostId());
            e.setUserId(m.getUserId());
            e.setTitle(m.getTitle());
            e.setContent(m.getContent());
            e.setImageUrl(m.getImageUrl());
            e.setCreatedAt(m.getCreatedAt());
            e.setLikesCount(m.getLikesCount());
            e.setCommentsCount(m.getCommentsCount());
            e.setSharesCount(m.getSharesCount());
            e.setLiked(m.isLiked());
            e.setUserName(m.getUserName());
            e.setUserProfileImage(m.getUserProfileImage());
            entities.add(e);
        }
        return entities;
    }
}
