package com.example.smartfarmer.adapters;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.PopupMenu;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.models.Post;
import com.example.smartfarmer.models.MobileAd;
import com.google.android.material.button.MaterialButton;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class PostAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_POST = 0;
    private static final int TYPE_AD_ROW = 1;
    private static final int AD_INTERVAL = 6;

    private List<Post> posts;
    private List<MobileAd> activeAds = new ArrayList<>();
    private int lastPosition = -1;
    private com.example.smartfarmer.auth.SupabaseAuthHelper supabaseAuth;
    private com.example.smartfarmer.utils.SessionManager sessionManager;
    private OnPostActionListener postActionListener;

    public interface OnPostActionListener {
        void onPostDeleted();
    }

    public void setOnPostActionListener(OnPostActionListener listener) {
        this.postActionListener = listener;
    }

    public PostAdapter(List<Post> posts) {
        this.posts = posts;
        this.supabaseAuth = new com.example.smartfarmer.auth.SupabaseAuthHelper();
    }

    public void updateAds(List<MobileAd> ads) {
        this.activeAds = ads;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position > 0 && position % (AD_INTERVAL + 1) == AD_INTERVAL && !activeAds.isEmpty()) {
            return TYPE_AD_ROW;
        }
        return TYPE_POST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_AD_ROW) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ad_row, parent, false);
            return new AdRowViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        if (sessionManager == null) {
            sessionManager = new com.example.smartfarmer.utils.SessionManager(parent.getContext());
        }
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof AdRowViewHolder) {
            ((AdRowViewHolder) holder).bind(activeAds, supabaseAuth);
            return;
        }

        PostViewHolder postHolder = (PostViewHolder) holder;
        int postIndex = position - (position / (AD_INTERVAL + 1));
        if (postIndex >= posts.size()) return;
        
        Post post = posts.get(postIndex);
        
        // User Info
        postHolder.tvUserName.setText(post.getUserName() != null ? post.getUserName() : "Anonymous Farmer");
        loadProfileImage(post.getUserProfileImage(), postHolder.ivUserImage);

        // Post Info
        postHolder.tvDate.setText(post.getCreatedAt().split("T")[0]);
        postHolder.tvContent.setText(post.getContent());
        postHolder.tvCommentsCount.setText(postHolder.itemView.getContext().getString(R.string.comments_count, post.getCommentsCount()));
        
        // Setup Comments View
        postHolder.layoutComments.setVisibility(View.GONE);
        postHolder.rvComments.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(postHolder.itemView.getContext()));

        // Post Image
        if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
            if (post.getImageUrl().contains("|")) {
                // Multiple images
                postHolder.ivPostImage.setVisibility(View.GONE);
                postHolder.rvPostImages.setVisibility(View.VISIBLE);
                List<String> images = Arrays.asList(post.getImageUrl().split("\\|"));
                postHolder.rvPostImages.setAdapter(new PostImageAdapter(images));
                postHolder.btnSaveImage.setVisibility(View.GONE); 
            } else {
                // Single image
                postHolder.rvPostImages.setVisibility(View.GONE);
                postHolder.ivPostImage.setVisibility(View.VISIBLE);
                postHolder.btnSaveImage.setVisibility(View.VISIBLE);
                
                loadPostImage(post.getImageUrl(), postHolder.ivPostImage);
            }
        } else {
            postHolder.ivPostImage.setVisibility(View.GONE);
            postHolder.rvPostImages.setVisibility(View.GONE);
            postHolder.btnSaveImage.setVisibility(View.GONE);
        }
        postHolder.tvImageError.setVisibility(View.GONE);

        // Profile Click Listener
        View.OnClickListener profileClick = v -> {
            Intent intent = new Intent(v.getContext(), com.example.smartfarmer.activities.ProfileActivity.class);
            intent.putExtra("user_id", post.getUserId());
            v.getContext().startActivity(intent);
        };
        postHolder.ivUserImage.setOnClickListener(profileClick);
        postHolder.tvUserName.setOnClickListener(profileClick);

        // Action Listeners
        updateLikeButton(postHolder, post);
        postHolder.btnLike.setOnClickListener(v -> {
            toggleLike(postHolder, post);
        });

        View.OnClickListener toggleComments = v -> {
            if (postHolder.layoutComments.getVisibility() == View.VISIBLE) {
                postHolder.layoutComments.setVisibility(View.GONE);
            } else {
                loadComments(v.getContext(), post, postHolder);
            }
        };
        postHolder.btnComment.setOnClickListener(toggleComments);
        postHolder.tvCommentsCount.setOnClickListener(toggleComments);

        // Set current user image for comment box
        String currentUserImg = sessionManager.getProfileImage();
        loadProfileImage(currentUserImg, postHolder.ivCommentUserSmall);

        postHolder.btnSendQuickComment.setOnClickListener(v -> {
            String commentText = postHolder.etQuickComment.getText().toString().trim();
            if (commentText.isEmpty()) {
                return;
            }

            String userId = sessionManager.getUserId();
            if (userId == null) {
                Toast.makeText(v.getContext(), "Please login to comment", Toast.LENGTH_SHORT).show();
                return;
            }

            postHolder.btnSendQuickComment.setEnabled(false);
            supabaseAuth.addComment(post.getPostId(), userId, commentText, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    int newCount = post.getCommentsCount() + 1;
                    supabaseAuth.updatePostInteractions(post.getPostId(), "comments_count", newCount, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            if (v.getContext() instanceof android.app.Activity) {
                                ((android.app.Activity) v.getContext()).runOnUiThread(() -> {
                                    postHolder.btnSendQuickComment.setEnabled(true);
                                    postHolder.etQuickComment.setText("");
                                    post.setCommentsCount(newCount);
                                    postHolder.tvCommentsCount.setText(v.getContext().getString(R.string.comments_count, newCount));
                                    loadComments(v.getContext(), post, postHolder);
                                    Toast.makeText(v.getContext(), "Comment added", Toast.LENGTH_SHORT).show();
                                });
                            }
                            
                            // Notify post owner (Removed self-check for easier testing/verification)
                            String notificationMsg = v.getContext().getString(R.string.notification_comment_msg, sessionManager.getUserName(), commentText);
                            supabaseAuth.createNotification(
                                post.getUserId(),
                                v.getContext().getString(R.string.notification_community_title),
                                notificationMsg,
                                "community",
                                post.getPostId() + "|" + userId,
                                new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                                    @Override public void onSuccess(String data) {}
                                    @Override public void onError(String error) {}
                                }
                            );
                        }
                        @Override public void onError(String error) {
                            if (v.getContext() instanceof android.app.Activity) {
                                ((android.app.Activity) v.getContext()).runOnUiThread(() -> postHolder.btnSendQuickComment.setEnabled(true));
                            }
                        }
                    });
                }
                @Override
                public void onError(String error) {
                    if (v.getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) v.getContext()).runOnUiThread(() -> {
                            postHolder.btnSendQuickComment.setEnabled(true);
                            Toast.makeText(v.getContext(), "Error: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });

        postHolder.btnShare.setOnClickListener(v -> {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, post.getTitle());
            shareIntent.putExtra(Intent.EXTRA_TEXT, post.getTitle() + "\n\n" + post.getContent());
            v.getContext().startActivity(Intent.createChooser(shareIntent, "Share post via"));

            // Increment and save share count
            int newCount = post.getSharesCount() + 1;
            post.setSharesCount(newCount);
            
            supabaseAuth.updatePostInteractions(post.getPostId(), "shares_count", newCount, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {}
                @Override public void onError(String error) {}
            });
        });

        postHolder.btnSaveImage.setOnClickListener(v -> {
            if (post.getImageUrl() != null && !post.getImageUrl().isEmpty()) {
                saveImageToGallery(v.getContext(), post.getImageUrl(), "SmartFarmer_" + post.getPostId());
            }
        });

        // Double Tap to Like/Unlike on Image
        GestureDetector gestureDetector = new GestureDetector(postHolder.itemView.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                toggleLike(postHolder, post);
                return true;
            }
        });

        postHolder.ivPostImage.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return true;
        });

        // Overflow menu for Profile page
        String currentUserId = sessionManager.getUserId();
        boolean isProfilePage = postHolder.itemView.getContext() instanceof com.example.smartfarmer.activities.ProfileActivity;
        
        if (isProfilePage && currentUserId != null && currentUserId.equals(post.getUserId())) {
            postHolder.btnPostMenu.setVisibility(View.VISIBLE);
            postHolder.btnPostMenu.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(v.getContext(), v);
                popup.getMenu().add("Edit");
                
                SpannableString deleteText = new SpannableString("Delete");
                deleteText.setSpan(new ForegroundColorSpan(ContextCompat.getColor(v.getContext(), R.color.red)), 0, deleteText.length(), 0);
                popup.getMenu().add(0, 1, 0, deleteText);
                
                popup.setOnMenuItemClickListener(item -> {
                    String title = item.getTitle() != null ? item.getTitle().toString() : "";
                    if ("Edit".equals(title)) {
                        showEditDialog(post, position, v.getContext());
                    } else if ("Delete".equals(title)) {
                        showDeleteConfirm(post, position, v.getContext());
                    }
                    return true;
                });
                popup.show();
            });
        } else {
            postHolder.btnPostMenu.setVisibility(View.GONE);
        }

        setAnimation(postHolder.itemView, position);
    }

    private void showEditDialog(Post post, int position, android.content.Context context) {
        android.view.View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_edit_post, null);
        com.google.android.material.textfield.TextInputEditText etContent = dialogView.findViewById(R.id.etEditPostContent);
        MaterialButton btnUpdate = dialogView.findViewById(R.id.btnUpdatePost);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelEdit);
        android.widget.ProgressBar progressBar = dialogView.findViewById(R.id.pbEditPost);

        etContent.setText(post.getContent());

        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(context)
                .setView(dialogView)
                .create();
        
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnUpdate.setOnClickListener(v -> {
            if (etContent.getText() == null) return;
            String newContent = etContent.getText().toString().trim();
            if (newContent.isEmpty()) {
                etContent.setError("Content cannot be empty");
                return;
            }

            btnUpdate.setEnabled(false);
            btnCancel.setEnabled(false);
            progressBar.setVisibility(View.VISIBLE);

            supabaseAuth.updatePost(post.getPostId(), newContent, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            post.setContent(newContent);
                            notifyItemChanged(position);
                            progressBar.setVisibility(View.GONE);
                            dialog.dismiss();
                            Toast.makeText(context, "Post updated", Toast.LENGTH_SHORT).show();
                        });
                    }
                }

                @Override
                public void onError(String error) {
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() -> {
                            btnUpdate.setEnabled(true);
                            btnCancel.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            Toast.makeText(context, "Update failed: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            });
        });

        dialog.show();
    }

    private void showDeleteConfirm(Post post, int position, android.content.Context context) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    supabaseAuth.deletePost(post.getPostId(), new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    posts.remove(position);
                                    notifyItemRemoved(position);
                                    notifyItemRangeChanged(position, posts.size());
                                    if (postActionListener != null) {
                                        postActionListener.onPostDeleted();
                                    }
                                    Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show();
                                });
                            }
                        }

                        @Override
                        public void onError(String error) {
                            if (context instanceof android.app.Activity) {
                                ((android.app.Activity) context).runOnUiThread(() -> {
                                    Toast.makeText(context, "Delete failed: " + error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleLike(PostViewHolder holder, Post post) {
        String userId = sessionManager.getUserId();
        if (userId == null) {
            Toast.makeText(holder.itemView.getContext(), "Please login to like posts", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean newState = !post.isLiked();
        int newCount = post.getLikesCount() + (newState ? 1 : -1);
        if (newCount < 0) newCount = 0;

        // UI Optimistic Update
        post.setLiked(newState);
        post.setLikesCount(newCount);
        updateLikeButton(holder, post);
        
        // Save to Supabase with User-Specific Tracking
        supabaseAuth.togglePostLike(post.getPostId(), userId, newState, newCount, new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
            @Override public void onSuccess(String data) {}
            @Override public void onError(String error) {
                // Revert UI on failure
                ((android.app.Activity)holder.itemView.getContext()).runOnUiThread(() -> {
                    post.setLiked(!newState);
                    post.setLikesCount(post.getLikesCount() + (newState ? -1 : 1));
                    updateLikeButton(holder, post);
                    Toast.makeText(holder.itemView.getContext(), "Sync Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });

        if (newState) {
            Toast.makeText(holder.itemView.getContext(), "Liked!", Toast.LENGTH_SHORT).show();
            
            // Notify post owner (Removed self-check for easier testing/verification)
            String notificationMsg = holder.itemView.getContext().getString(R.string.notification_like_msg, sessionManager.getUserName());
            supabaseAuth.createNotification(
                post.getUserId(),
                holder.itemView.getContext().getString(R.string.notification_community_title),
                notificationMsg,
                "community",
                post.getPostId() + "|" + userId,
                new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                    @Override public void onSuccess(String data) {}
                    @Override public void onError(String error) {}
                }
            );
        }
    }

    private void loadComments(android.content.Context context, Post post, PostViewHolder holder) {
        supabaseAuth.fetchComments(post.getPostId(), new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    org.json.JSONArray arr = new org.json.JSONArray(json);
                    java.util.List<com.example.smartfarmer.models.Comment> commentList = new java.util.ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject obj = arr.getJSONObject(i);
                        com.example.smartfarmer.models.Comment c = new com.example.smartfarmer.models.Comment();
                        // Priority to 'comment_id' based on project conventions
                        String commentId = obj.optString("comment_id", "");
                        if (commentId.isEmpty()) {
                            commentId = obj.optString("id", "");
                        }
                        c.setId(commentId);

                        c.setUserId(obj.optString("user_id", ""));
                        c.setPostId(obj.optString("post_id", ""));
                        
                        // Try both 'parent_comment_id' and 'parent_id'
                        String parentId = obj.optString("parent_comment_id", "");
                        if (parentId.isEmpty() || parentId.equals("null")) {
                            parentId = obj.optString("parent_id", "");
                        }
                        c.setParentId(parentId);

                        c.setContent(obj.optString("comment_text", ""));
                        c.setCreatedAt(obj.optString("created_at", ""));
                        
                        if (obj.has("users") && !obj.isNull("users")) {
                            org.json.JSONObject user = obj.getJSONObject("users");
                            c.setUserName(user.optString("full_name", "Anonymous"));
                            c.setUserProfileImage(user.optString("profile_image", ""));
                        }
                        commentList.add(c);
                    }
                    
                    // Organize comments: Parents first, then their replies
                    java.util.List<com.example.smartfarmer.models.Comment> organizedList = new java.util.ArrayList<>();
                    for (com.example.smartfarmer.models.Comment parent : commentList) {
                        String pId = parent.getParentId();
                        if (pId == null || pId.isEmpty() || pId.equals("null")) {
                            organizedList.add(parent);
                            // Add replies for this parent
                            for (com.example.smartfarmer.models.Comment reply : commentList) {
                                String rPId = reply.getParentId();
                                if (rPId != null && parent.getId().equals(rPId)) {
                                    organizedList.add(reply);
                                }
                            }
                        }
                    }
                    
                    ((android.app.Activity)context).runOnUiThread(() -> {
                        holder.layoutComments.setVisibility(View.VISIBLE);
                        if (organizedList.isEmpty()) {
                            holder.rvComments.setVisibility(View.GONE);
                        } else {
                            holder.rvComments.setVisibility(View.VISIBLE);
                            holder.rvComments.setAdapter(new CommentAdapter(organizedList, new CommentAdapter.OnCommentInteractionListener() {
                                @Override
                                public void onCommentUpdated() {
                                    // Optionally refresh
                                }

                                @Override
                                public void onCommentDeleted() {
                                    loadComments(context, post, holder);
                                }
                            }));
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(String error) {
                ((android.app.Activity)context).runOnUiThread(() -> {
                    holder.layoutComments.setVisibility(View.GONE);
                });
            }
        });
    }



    private void updateLikeButton(PostViewHolder holder, Post post) {
        holder.tvLikesCount.setText(holder.itemView.getContext().getString(R.string.likes_count, post.getLikesCount()));
        if (post.isLiked()) {
            holder.btnLike.setIconResource(R.drawable.ic_thumb_up);
            holder.btnLike.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.primary_blue));
            holder.btnLike.setIconTintResource(R.color.primary_blue);
        } else {
            holder.btnLike.setIconResource(R.drawable.ic_thumb_up);
            holder.btnLike.setTextColor(holder.itemView.getContext().getResources().getColor(R.color.gray));
            holder.btnLike.setIconTintResource(R.color.gray);
        }
    }

    private void saveImageToGallery(android.content.Context context, String base64, String fileName) {
        try {
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            
            File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File file = new File(path, fileName + ".jpg");
            
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
            
            // Trigger gallery scan
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(file);
            mediaScanIntent.setData(contentUri);
            context.sendBroadcast(mediaScanIntent);
            
            Toast.makeText(context, "Image saved to Gallery", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadProfileImage(String imageStr, ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = Base64.decode(imageStr, Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    private void loadPostImage(String imageStr, ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setVisibility(View.GONE);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(imageView.getContext())
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .override(800, 800)
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = Base64.decode(imageStr, Base64.DEFAULT);
                com.bumptech.glide.Glide.with(imageView.getContext())
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                        .override(800, 800)
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    private void setAnimation(View viewToAnimate, int position) {
        if (position > lastPosition) {
            Animation animation = AnimationUtils.loadAnimation(viewToAnimate.getContext(), android.R.anim.slide_in_left);
            viewToAnimate.startAnimation(animation);
            lastPosition = position;
        }
    }

    @Override
    public int getItemCount() {
        if (posts.isEmpty()) return 0;
        int adCount = activeAds.isEmpty() ? 0 : posts.size() / AD_INTERVAL;
        return posts.size() + adCount;
    }

    public void updatePosts(List<Post> newPosts) {
        this.posts = new ArrayList<>(newPosts);
        notifyDataSetChanged();
    }

    public void addPosts(List<Post> morePosts) {
        if (morePosts == null || morePosts.isEmpty()) return;
        
        // Use a set of existing IDs to prevent duplicates
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        for (Post p : this.posts) {
            existingIds.add(p.getPostId());
        }

        List<Post> trulyNewPosts = new ArrayList<>();
        for (Post p : morePosts) {
            if (!existingIds.contains(p.getPostId())) {
                trulyNewPosts.add(p);
            }
        }

        if (trulyNewPosts.isEmpty()) return;

        this.posts.addAll(trulyNewPosts);
        // Since we have ads mixed in, notifyDataSetChanged is safer for position mapping
        notifyDataSetChanged();
    }

    static class AdRowViewHolder extends RecyclerView.ViewHolder {
        RecyclerView rvHorizontalAds;

        public AdRowViewHolder(@NonNull View itemView) {
            super(itemView);
            rvHorizontalAds = itemView.findViewById(R.id.rvHorizontalAds);
            rvHorizontalAds.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(
                    itemView.getContext(), RecyclerView.HORIZONTAL, false));
        }

        public void bind(List<MobileAd> ads, com.example.smartfarmer.auth.SupabaseAuthHelper supabaseAuth) {
            AdAdapter adAdapter = new AdAdapter(ads, ad -> {
                // Record click
                supabaseAuth.recordAdInteraction(ad.getAdId(), "clicks", ad.getClicks(), new com.example.smartfarmer.auth.SupabaseAuthHelper.AuthCallback() {
                    @Override public void onSuccess(String data) {}
                    @Override public void onError(String error) {}
                });
            });
            rvHorizontalAds.setAdapter(adAdapter);
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvUserName, tvDate, tvContent, tvLikesCount, tvImageError, tvCommentsCount;
        EditText etQuickComment;
        ImageView ivUserImage, ivPostImage, ivCommentUserSmall;
        ImageButton btnSaveImage, btnSendQuickComment, btnPostMenu;
        MaterialButton btnLike, btnComment, btnShare;
        android.view.View layoutComments, layoutQuickComment;
        androidx.recyclerview.widget.RecyclerView rvComments, rvPostImages;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvDate = itemView.findViewById(R.id.tvPostDate);
            tvContent = itemView.findViewById(R.id.tvPostContent);
            tvLikesCount = itemView.findViewById(R.id.tvLikesCount);
            tvCommentsCount = itemView.findViewById(R.id.tvCommentsCount);
            etQuickComment = itemView.findViewById(R.id.etQuickComment);
            tvImageError = itemView.findViewById(R.id.tvImageError);
            ivUserImage = itemView.findViewById(R.id.ivUserImage);
            ivPostImage = itemView.findViewById(R.id.ivPostImage);
            rvPostImages = itemView.findViewById(R.id.rvPostImages);
            ivCommentUserSmall = itemView.findViewById(R.id.ivCommentUserSmall);
            btnSaveImage = itemView.findViewById(R.id.btnSaveImage);
            btnSendQuickComment = itemView.findViewById(R.id.btnSendQuickComment);
            btnPostMenu = itemView.findViewById(R.id.btnPostMenu);
            btnLike = itemView.findViewById(R.id.btnLike);
            btnComment = itemView.findViewById(R.id.btnComment);
            btnShare = itemView.findViewById(R.id.btnShare);
            layoutComments = itemView.findViewById(R.id.layoutComments);
            layoutQuickComment = itemView.findViewById(R.id.layoutQuickComment);
            rvComments = itemView.findViewById(R.id.rvComments);
        }
    }
}
