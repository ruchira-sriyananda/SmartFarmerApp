package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.widget.Toolbar;
import com.example.smartfarmer.R;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.User;
import com.example.smartfarmer.utils.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import android.widget.Toast;
import com.example.smartfarmer.adapters.PostAdapter;
import com.example.smartfarmer.models.Post;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.ArrayList;
import java.util.List;
import androidx.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import org.json.JSONArray;
import org.json.JSONObject;

public class ProfileActivity extends BaseActivity {
    private TextView tvProfileName, tvDetailEmail, tvDetailPhone, tvDetailDistrict, tvDetailAddress, tvProfileStatus, tvEditAccountDetails, tvNoPosts;
    private com.google.android.material.imageview.ShapeableImageView ivProfileLarge;
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabEditImageProfile;
    private com.github.ybq.android.spinkit.SpinKitView pbProfileLarge, pbProfilePostsLoading;
    private com.google.android.material.button.MaterialButton btnSelectPostImage, btnSubmitPostProfile;
    private RecyclerView rvUserPosts, rvSelectedPostImages;
    private PostAdapter postAdapter;
    private SelectedImageAdapter selectedImageAdapter;
    private SessionManager sessionManager;
    private SupabaseAuthHelper supabaseAuth;
    private String targetUserId = null;
    private List<String> selectedImagesBase64 = new ArrayList<>();
    private android.widget.EditText etPostContentProfile;
    private com.google.android.material.imageview.ShapeableImageView ivUserSmallPost;
    private com.github.ybq.android.spinkit.SpinKitView pbCreatePost;
    private static final int PICK_IMAGE_PROFILE = 2001;
    private static final int PICK_IMAGE_POST = 2002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = new SessionManager(this);
        supabaseAuth = new SupabaseAuthHelper(this);
        
        targetUserId = getIntent().getStringExtra("user_id");
        String currentUserId = sessionManager.getUserId();

        initViews();
        setupRecyclerView();

        if (targetUserId == null || targetUserId.equals(currentUserId)) {
            // Own profile
            targetUserId = currentUserId;
            displayMyProfile();
        } else {
            // Someone else's profile
            displayOtherUserProfile(targetUserId);
        }

        fetchUserPosts(targetUserId);

        tvEditAccountDetails.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, RegisterActivity.class);
            intent.putExtra("is_edit_mode", true);
            startActivity(intent);
        });



        Toolbar toolbar = findViewById(R.id.toolbarProfile);
        if (toolbar instanceof MaterialToolbar) {
            setupToolbarMenu((MaterialToolbar) toolbar);
        }
    }

    private void setupToolbarMenu(MaterialToolbar toolbar) {
        toolbar.inflateMenu(R.menu.top_app_bar_menu);

        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_notifications) {
                startActivity(new Intent(this, NotificationsActivity.class));
                return true;
            } else if (id == R.id.action_profile_nav) {
                // Already on profile
                return true;
            } else if (id == R.id.action_change_language) {
                startActivity(new Intent(this, LanguageSelectionActivity.class));
                return true;
            } else if (id == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            }
            return false;
        });
    }

    private void initViews() {
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileStatus = findViewById(R.id.tvProfileStatus);
        ivProfileLarge = findViewById(R.id.ivProfileLarge);
        fabEditImageProfile = findViewById(R.id.fabEditImageProfile);
        pbProfileLarge = findViewById(R.id.pbProfileLarge);
        pbProfilePostsLoading = findViewById(R.id.pbProfilePostsLoading);
        tvDetailEmail = findViewById(R.id.tvDetailEmail);
        tvDetailPhone = findViewById(R.id.tvDetailPhone);
        tvDetailDistrict = findViewById(R.id.tvDetailDistrict);
        tvDetailAddress = findViewById(R.id.tvDetailAddress);
        tvEditAccountDetails = findViewById(R.id.tvEditAccountDetails);
        rvUserPosts = findViewById(R.id.rvUserPosts);
        tvNoPosts = findViewById(R.id.tvNoPosts);

        // Create Post Views
        etPostContentProfile = findViewById(R.id.etPostContentProfile);
        ivUserSmallPost = findViewById(R.id.ivUserSmallPost);
        rvSelectedPostImages = findViewById(R.id.rvSelectedPostImages);
        btnSelectPostImage = findViewById(R.id.btnSelectPostImage);
        btnSubmitPostProfile = findViewById(R.id.btnSubmitPostProfile);
        pbCreatePost = findViewById(R.id.pbCreatePost);

        selectedImageAdapter = new SelectedImageAdapter(selectedImagesBase64, position -> {
            selectedImagesBase64.remove(position);
            selectedImageAdapter.notifyItemRemoved(position);
            if (selectedImagesBase64.isEmpty()) {
                rvSelectedPostImages.setVisibility(android.view.View.GONE);
            }
        });
        rvSelectedPostImages.setAdapter(selectedImageAdapter);

        fabEditImageProfile.setOnClickListener(v -> pickProfileImage());
        btnSelectPostImage.setOnClickListener(v -> pickPostImage());
        btnSubmitPostProfile.setOnClickListener(v -> submitPost());
    }

    private void pickPostImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_POST);
    }

    private void removePostImage() {
        selectedImagesBase64.clear();
        selectedImageAdapter.notifyDataSetChanged();
        rvSelectedPostImages.setVisibility(android.view.View.GONE);
    }

    private void submitPost() {
        String content = etPostContentProfile.getText().toString().trim();
        if (content.isEmpty() && selectedImagesBase64.isEmpty()) {
            Toast.makeText(this, R.string.please_write_something_or_add_image, Toast.LENGTH_SHORT).show();
            return;
        }

        pbCreatePost.setVisibility(android.view.View.VISIBLE);
        btnSubmitPostProfile.setEnabled(false);

        // Join multiple images with a separator (e.g., "|")
        StringBuilder imagesBuilder = new StringBuilder();
        for (int i = 0; i < selectedImagesBase64.size(); i++) {
            imagesBuilder.append(selectedImagesBase64.get(i));
            if (i < selectedImagesBase64.size() - 1) {
                imagesBuilder.append("|");
            }
        }
        String joinedImages = imagesBuilder.toString();

        supabaseAuth.createPost(sessionManager.getUserId(), content, joinedImages.isEmpty() ? null : joinedImages, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbCreatePost.setVisibility(android.view.View.GONE);
                    btnSubmitPostProfile.setEnabled(true);
                    etPostContentProfile.setText("");
                    removePostImage();
                    Toast.makeText(ProfileActivity.this, R.string.post_created_successfully, Toast.LENGTH_SHORT).show();
                    fetchUserPosts(sessionManager.getUserId());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbCreatePost.setVisibility(android.view.View.GONE);
                    btnSubmitPostProfile.setEnabled(true);
                    Toast.makeText(ProfileActivity.this, getString(R.string.error) + ": " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setupRecyclerView() {
        rvUserPosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new PostAdapter(new ArrayList<>());
        postAdapter.setOnPostActionListener(() -> {
            if (postAdapter.getItemCount() == 0) {
                tvNoPosts.setVisibility(View.VISIBLE);
                rvUserPosts.setVisibility(View.GONE);
            }
        });
        rvUserPosts.setAdapter(postAdapter);
    }

    private void fetchUserPosts(String userId) {
        if (postAdapter.getItemCount() == 0) {
            pbProfilePostsLoading.setVisibility(View.VISIBLE);
        }
        tvNoPosts.setVisibility(View.GONE);

        supabaseAuth.fetchUserPosts(userId, sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    pbProfilePostsLoading.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        List<Post> posts = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            Post post = new Post();
                            post.setPostId(obj.getString("post_id"));
                            post.setUserId(obj.getString("user_id"));
                            post.setContent(obj.optString("content", ""));
                            post.setImageUrl(obj.optString("image_url", ""));
                            post.setCreatedAt(obj.optString("created_at", ""));
                            post.setLikesCount(obj.optInt("likes_count", 0));
                            post.setCommentsCount(obj.optInt("comments_count", 0));
                            
                            if (obj.has("post_likes") && !obj.isNull("post_likes")) {
                                post.setLiked(obj.getJSONArray("post_likes").length() > 0);
                            }

                            if (obj.has("users") && !obj.isNull("users")) {
                                JSONObject userObj = obj.getJSONObject("users");
                                post.setUserName(userObj.optString("full_name", "Anonymous"));
                                post.setUserProfileImage(userObj.optString("profile_image", ""));
                            }
                            posts.add(post);
                        }
                        
                        if (posts.isEmpty()) {
                            tvNoPosts.setVisibility(android.view.View.VISIBLE);
                            rvUserPosts.setVisibility(android.view.View.GONE);
                        } else {
                            tvNoPosts.setVisibility(android.view.View.GONE);
                            rvUserPosts.setVisibility(android.view.View.VISIBLE);
                            postAdapter.updatePosts(posts);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbProfilePostsLoading.setVisibility(View.GONE);
                    Toast.makeText(ProfileActivity.this, getString(R.string.error_fetching_posts, error), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }


    private void pickProfileImage() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_PROFILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_IMAGE_PROFILE && data.getData() != null) {
                uploadProfileImage(data.getData());
            } else if (requestCode == PICK_IMAGE_POST) {
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        handlePostImageSelection(data.getClipData().getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    handlePostImageSelection(data.getData());
                }
            }
        }
    }

    private void handlePostImageSelection(android.net.Uri uri) {
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);

            selectedImagesBase64.add(base64);
            selectedImageAdapter.notifyItemInserted(selectedImagesBase64.size() - 1);
            rvSelectedPostImages.setVisibility(android.view.View.VISIBLE);
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadProfileImage(android.net.Uri uri) {
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] bytes = baos.toByteArray();
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);

            pbProfileLarge.setVisibility(android.view.View.VISIBLE);
            ivProfileLarge.setAlpha(0.5f);

            User user = new User();
            user.setUserId(sessionManager.getUserId());
            user.setFullName(sessionManager.getUserName());
            user.setEmail(sessionManager.getUserEmail());
            user.setPhoneNumber(sessionManager.getUserPhone());
            user.setDistrict(sessionManager.getUserDistrict());
            user.setAddress(sessionManager.getUserAddress());
            user.setProfileImage(base64);

            supabaseAuth.saveUserDetails(user, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        pbProfileLarge.setVisibility(android.view.View.GONE);
                        ivProfileLarge.setAlpha(1.0f);
                        ivProfileLarge.setImageBitmap(bitmap);
                        sessionManager.setProfileImage(base64);
                        android.widget.Toast.makeText(ProfileActivity.this, R.string.profile_image_updated, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        pbProfileLarge.setVisibility(android.view.View.GONE);
                        ivProfileLarge.setAlpha(1.0f);
                        android.widget.Toast.makeText(ProfileActivity.this, getString(R.string.update_failed, error), android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
    }

    private void displayMyProfile() {
        fabEditImageProfile.setVisibility(android.view.View.VISIBLE);
        findViewById(R.id.cardCreatePost).setVisibility(android.view.View.VISIBLE);
        String name = sessionManager.getUserName();
        String email = sessionManager.getUserEmail();
        String phone = sessionManager.getUserPhone();
        String district = sessionManager.getUserDistrict();
        String address = sessionManager.getUserAddress();

        tvProfileName.setText(name != null && !name.isEmpty() ? name : getString(R.string.user_name_placeholder));
        tvDetailEmail.setText(email != null && !email.isEmpty() ? email : getString(R.string.not_available));
        tvDetailPhone.setText(phone != null && !phone.isEmpty() ? phone : getString(R.string.not_available));
        tvDetailDistrict.setText(district != null && !district.isEmpty() ? district : getString(R.string.not_available));
        tvDetailAddress.setText(address != null && !address.isEmpty() ? address : getString(R.string.not_available));

        // 1. Try to load from local session first
        String localImage = sessionManager.getProfileImage();
        if (localImage != null && !localImage.isEmpty()) {
            showImageLarge(localImage);
            showImageSmall(localImage);
        }

        // 2. Fetch from Supabase
        if (email != null && !email.isEmpty()) {
            pbProfileLarge.setVisibility(android.view.View.VISIBLE);
            ivProfileLarge.setAlpha(0.5f);
            
            supabaseAuth.getProfileImageByEmail(email, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String imageBase64) {
                    runOnUiThread(() -> {
                        pbProfileLarge.setVisibility(android.view.View.GONE);
                        ivProfileLarge.setAlpha(1.0f);
                        if (imageBase64 != null && !imageBase64.isEmpty()) {
                            if (!imageBase64.equals(localImage)) {
                                showImageLarge(imageBase64);
                                showImageSmall(imageBase64);
                                sessionManager.setProfileImage(imageBase64); // Cache locally
                            }
                        } else {
                            showImageLarge(null);
                            showImageSmall(null);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        pbProfileLarge.setVisibility(android.view.View.GONE);
                        ivProfileLarge.setAlpha(1.0f);
                        showImageLarge(null);
                        showImageSmall(null);
                    });
                }
            });
        }
        animateName();
    }

    private void displayOtherUserProfile(String userId) {
        // Hide edit options for other's profile
        tvEditAccountDetails.setVisibility(android.view.View.GONE);
        fabEditImageProfile.setVisibility(android.view.View.GONE);
        findViewById(R.id.cardCreatePost).setVisibility(android.view.View.GONE);

        pbProfileLarge.setVisibility(android.view.View.VISIBLE);
        ivProfileLarge.setAlpha(0.5f);

        supabaseAuth.getUserProfile(userId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    try {
                        JSONObject profile = new JSONObject(data);
                        String name = profile.optString("full_name", getString(R.string.anonymous_farmer));
                        String email = profile.optString("email", getString(R.string.not_shared));
                        String phone = profile.optString("phone_number", getString(R.string.not_available));
                        String district = profile.optString("district", getString(R.string.not_available));
                        String address = profile.optString("address", getString(R.string.not_available));
                        String imageBase64 = profile.optString("profile_image", "");

                        tvProfileName.setText(name);
                        tvDetailEmail.setText(email);
                        tvDetailPhone.setText(phone);
                        tvDetailDistrict.setText(district);
                        tvDetailAddress.setText(address);

                        if (!imageBase64.isEmpty()) {
                            showImageLarge(imageBase64);
                        }

                        pbProfileLarge.setVisibility(android.view.View.GONE);
                        ivProfileLarge.setAlpha(1.0f);
                        animateName();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbProfileLarge.setVisibility(android.view.View.GONE);
                    ivProfileLarge.setAlpha(1.0f);
                    tvProfileName.setText(R.string.profile_not_found);
                });
            }
        });
    }

    private void animateName() {
        tvProfileName.setAlpha(0f);
        tvProfileName.animate().alpha(1f).setDuration(500).start();
    }

    private void displayUserDetails() {
        // Method replaced by displayMyProfile and displayOtherUserProfile
    }

    private void showImageLarge(String imageStr) {
        loadProfileImage(imageStr, ivProfileLarge);
    }

    private void showImageSmall(String imageStr) {
        loadProfileImage(imageStr, ivUserSmallPost);
    }

    private void loadProfileImage(String imageStr, android.widget.ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageStr)
                    .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(imageBytes)
                        .placeholder(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .error(R.drawable.thumb_show_fotor_bg_remover_20260709171323)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }
    }

    // Inner class for Selected Image Adapter
    private class SelectedImageAdapter extends RecyclerView.Adapter<SelectedImageAdapter.ViewHolder> {
        private List<String> images;
        private OnRemoveClickListener listener;

        interface OnRemoveClickListener {
            void onRemove(int position);
        }

        SelectedImageAdapter(List<String> images, OnRemoveClickListener listener) {
            this.images = images;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            android.view.View view = getLayoutInflater().inflate(R.layout.item_selected_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String base64 = images.get(position);
            byte[] decodedString = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            holder.ivImage.setImageBitmap(bitmap);
            holder.btnRemove.setOnClickListener(v -> listener.onRemove(holder.getAdapterPosition()));
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView ivImage;
            android.widget.ImageButton btnRemove;

            ViewHolder(android.view.View itemView) {
                super(itemView);
                ivImage = itemView.findViewById(R.id.ivSelectedImage);
                btnRemove = itemView.findViewById(R.id.btnRemoveImage);
            }
        }
    }
}
