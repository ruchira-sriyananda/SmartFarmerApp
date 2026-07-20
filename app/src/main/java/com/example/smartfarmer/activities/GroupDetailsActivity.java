package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.JoinRequestAdapter;
import com.example.smartfarmer.adapters.UserAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.User;
import com.example.smartfarmer.utils.SessionManager;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class GroupDetailsActivity extends BaseActivity {
    private RecyclerView rvMembers, rvPending;
    private UserAdapter adapter;
    private JoinRequestAdapter joinRequestAdapter;
    private String roomId, roomName, adminId;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private ExtendedFloatingActionButton fabAdd;
    private com.google.android.material.button.MaterialButton btnLeave, btnJoin;
    private TextView tvMemberCount, tvParticipantsBadge, tvGroupCategory;
    private ImageView ivGroup, ivGroupAvatar;
    private View btnEditImage;
    private com.github.ybq.android.spinkit.SpinKitView pbLoading, pbMembersLoading;
    private boolean isPublicGroup = true;
    private boolean isMember = false;
    private static final int PICK_IMAGE_REQUEST = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_details);

        roomId = getIntent().getStringExtra("room_id");
        roomName = getIntent().getStringExtra("room_name");
        adminId = getIntent().getStringExtra("admin_id");

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);

        initViews();
        fetchMembers();
        loadGroupDetails();

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarGroupDetails);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void loadGroupDetails() {
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.fetchChatRoomDetails(roomId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONObject obj = new JSONObject(json);
                    // Use profile_image primarily as per the recommended SQL fix
                    String url = obj.optString("profile_image", 
                                 obj.optString("image_url", 
                                 obj.optString("avatar_url", 
                                 obj.optString("image", ""))));
                    
                    isPublicGroup = obj.optBoolean("is_public", true);
                    adminId = obj.optString("created_by", adminId);
                    final String finalAdminId = adminId;
                    
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        loadGroupImage(url, ivGroup);
                        loadGroupImage(url, ivGroupAvatar);
                        
                        tvGroupCategory.setText(isPublicGroup ? "Public Group" : "Personal Group");
                        tvGroupCategory.setTextColor(android.graphics.Color.WHITE);

                        TextView tvGroupName = findViewById(R.id.tvGroupNameLarge);
                        if (sessionManager.getUserId().equals(finalAdminId)) {
                            fabAdd.setVisibility(View.VISIBLE);
                            btnEditImage.setVisibility(View.VISIBLE);
                            tvGroupName.setOnClickListener(v -> showEditGroupNameDialog());
                            tvGroupName.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_edit, 0);
                            tvGroupName.setCompoundDrawablePadding(8);
                        } else {
                            fabAdd.setVisibility(View.GONE);
                            btnEditImage.setVisibility(View.GONE);
                            tvGroupName.setOnClickListener(null);
                            tvGroupName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
                }
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
            }
        });
    }

    private void loadGroupImage(String imageStr, ImageView imageView) {
        if (imageStr == null || imageStr.isEmpty()) {
            imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            return;
        }

        if (imageStr.startsWith("http")) {
            com.bumptech.glide.Glide.with(this)
                    .load(imageStr)
                    .placeholder(android.R.drawable.ic_menu_myplaces)
                    .error(android.R.drawable.ic_menu_myplaces)
                    .circleCrop()
                    .into(imageView);
        } else {
            try {
                byte[] imageBytes = android.util.Base64.decode(imageStr, android.util.Base64.DEFAULT);
                com.bumptech.glide.Glide.with(this)
                        .load(imageBytes)
                        .placeholder(android.R.drawable.ic_menu_myplaces)
                        .error(android.R.drawable.ic_menu_myplaces)
                        .circleCrop()
                        .into(imageView);
            } catch (Exception e) {
                imageView.setImageResource(android.R.drawable.ic_menu_myplaces);
            }
        }
    }

    private void initViews() {
        rvMembers = findViewById(R.id.rvGroupMembers);
        rvPending = findViewById(R.id.rvPendingRequests);
        fabAdd = findViewById(R.id.fabAddMember);
        btnEditImage = findViewById(R.id.btnEditImage);
        btnLeave = findViewById(R.id.btnLeaveGroup);
        btnJoin = findViewById(R.id.btnJoinGroup);
        com.google.android.material.button.MaterialButton btnAskAi = findViewById(R.id.btnAskAiGroup);
        
        btnAskAi.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiAssistantActivity.class);
            startActivity(intent);
        });

        tvMemberCount = findViewById(R.id.tvMemberCount);

        tvParticipantsBadge = findViewById(R.id.tvParticipantsBadge);
        tvGroupCategory = findViewById(R.id.tvGroupCategory);
        ivGroup = findViewById(R.id.ivGroupLarge);
        ivGroupAvatar = findViewById(R.id.ivGroupAvatar);
        pbLoading = findViewById(R.id.pbGroupDetails);
        pbMembersLoading = findViewById(R.id.pbMembersLoading);
        
        TextView tvGroupName = findViewById(R.id.tvGroupNameLarge);
        tvGroupName.setText(roomName);

        if (sessionManager.getUserId().equals(adminId)) {
            fabAdd.setVisibility(View.VISIBLE);
            btnEditImage.setVisibility(View.VISIBLE);
            tvGroupName.setOnClickListener(v -> showEditGroupNameDialog());
            btnEditImage.setOnClickListener(v -> showEditGroupImageDialog());
            ivGroup.setOnClickListener(v -> showEditGroupImageDialog());
            findViewById(R.id.layoutGroupCategory).setOnClickListener(v -> showCategoryDialog());
        }

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, UserSelectionActivity.class);
            intent.putExtra("is_group_add", true);
            intent.putExtra("room_id", roomId);
            intent.putExtra("room_name", roomName);
            startActivityForResult(intent, 100);
        });

        btnLeave.setOnClickListener(v -> showLeaveGroupDialog());

        btnJoin.setOnClickListener(v -> requestToJoin());
    }

    private void requestToJoin() {
        if (!isPublicGroup) {
            Toast.makeText(this, "This is a private group", Toast.LENGTH_SHORT).show();
            return;
        }

        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.addParticipant(roomId, sessionManager.getUserId(), "pending", "member", new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(GroupDetailsActivity.this, "Join request sent!", Toast.LENGTH_SHORT).show();
                    btnJoin.setText("Request Pending");
                    btnJoin.setEnabled(false);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(GroupDetailsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLeaveGroupDialog() {
        boolean isAdmin = sessionManager.getUserId().equals(adminId);
        String title = isAdmin ? "Delete Group" : "Leave Group";
        String message = isAdmin ? 
                "As the admin, leaving will permanently delete this group and all its messages. Continue?" :
                "Are you sure you want to leave " + roomName + "?";

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(isAdmin ? "Delete & Leave" : "Leave", (dialog, which) -> {
                    pbLoading.setVisibility(View.VISIBLE);
                    if (isAdmin) {
                        deleteGroupCompletely();
                    } else {
                        leaveGroupNormally();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteGroupCompletely() {
        // 1. Delete Messages
        supabaseAuth.deleteRoomMessages(roomId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                // 2. Delete Participants
                supabaseAuth.deleteRoomParticipants(roomId, new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String data) {
                        // 3. Delete Room itself
                        supabaseAuth.deleteChatRoom(roomId, new SupabaseAuthHelper.AuthCallback() {
                            @Override
                            public void onSuccess(String data) {
                                runOnUiThread(() -> {
                                    pbLoading.setVisibility(View.GONE);
                                    Toast.makeText(GroupDetailsActivity.this, "Group deleted completely", Toast.LENGTH_SHORT).show();
                                    navigateToCommunity();
                                });
                            }
                            @Override public void onError(String error) { handleError(error); }
                        });
                    }
                    @Override public void onError(String error) { handleError(error); }
                });
            }
            @Override public void onError(String error) { handleError(error); }
        });
    }

    private void leaveGroupNormally() {
        supabaseAuth.removeParticipant(roomId, sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(GroupDetailsActivity.this, "Left group successfully", Toast.LENGTH_SHORT).show();
                    navigateToCommunity();
                });
            }
            @Override public void onError(String error) { handleError(error); }
        });
    }

    private void navigateToCommunity() {
        Intent intent = new Intent(GroupDetailsActivity.this, CommunityActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void handleError(String error) {
        runOnUiThread(() -> {
            pbLoading.setVisibility(View.GONE);
            Toast.makeText(GroupDetailsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK) {
            fetchMembers();
        } else if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            handleImageSelection(data.getData());
        }
    }

    private void handleImageSelection(android.net.Uri uri) {
        try {
            android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            // Resize if too large
            if (bitmap.getWidth() > 1024 || bitmap.getHeight() > 1024) {
                bitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, 1024, 1024, true);
            }
            
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
            byte[] imageBytes = baos.toByteArray();
            String base64Image = android.util.Base64.encodeToString(imageBytes, android.util.Base64.DEFAULT);
            
            updateGroupDetails(null, base64Image);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchMembers() {
        pbMembersLoading.setVisibility(View.VISIBLE);

        if (sessionManager.getUserId().equals(adminId)) {
            fetchPendingRequests();
        }

        supabaseAuth.fetchGroupMembers(roomId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray participantsArr = new JSONArray(json);
                    List<String> userIds = new ArrayList<>();
                    java.util.Map<String, String> userRoles = new java.util.HashMap<>();
                    
                    for (int i = 0; i < participantsArr.length(); i++) {
                        JSONObject obj = participantsArr.getJSONObject(i);
                        String uid = obj.getString("user_id");
                        userIds.add(uid);
                        userRoles.put(uid, obj.optString("role", "member"));
                    }
                    
                    if (userIds.isEmpty()) {
                        runOnUiThread(() -> pbMembersLoading.setVisibility(View.GONE));
                        return;
                    }
                    
                    // Step 2: Fetch profile details for these IDs
                    supabaseAuth.fetchUsersByIds(userIds, new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String usersJson) {
                            runOnUiThread(() -> {
                                pbMembersLoading.setVisibility(View.GONE);
                                try {
                                    JSONArray usersArr = new JSONArray(usersJson);
                                    List<User> members = new ArrayList<>();
                                    
                                    // Match profile data with roles
                                    User adminUser = null;
                                    isMember = false;
                                    for (int i = 0; i < usersArr.length(); i++) {
                                        JSONObject userObj = usersArr.getJSONObject(i);
                                        String uid = userObj.getString("user_id");
                                        
                                        if (uid.equals(sessionManager.getUserId())) {
                                            isMember = true;
                                        }

                                        User u = new User();
                                        u.setUserId(uid);
                                        u.setFullName(userObj.optString("full_name", "Farmer " + uid.substring(0, 4)));
                                        u.setProfileImage(userObj.optString("profile_image", ""));
                                        
                                        String role = userRoles.get(uid);
                                        boolean isAdmin = uid.equals(adminId) || "admin".equalsIgnoreCase(role);
                                        u.setEmail(isAdmin ? "ADMIN" : (role != null ? role.toUpperCase() : "MEMBER"));
                                        
                                        if (isAdmin) {
                                            adminUser = u;
                                        } else {
                                            members.add(u);
                                        }
                                    }

                                    // Ensure admin is at the top
                                    if (adminUser != null) {
                                        members.add(0, adminUser);
                                    }
                                    
                                    tvMemberCount.setText(members.size() + " members");
                                    tvParticipantsBadge.setText(String.valueOf(members.size()));
                                    setupRecyclerView(members);
                                    updateMembershipUI();
                                    
                                } catch (Exception e) { 
                                    e.printStackTrace(); 
                                }
                            });
                        }
                        
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> {
                                pbMembersLoading.setVisibility(View.GONE);
                                isMember = false;
                                updateMembershipUI();
                            });
                        }
                    });
                    
                } catch (Exception e) {
                    runOnUiThread(() -> pbMembersLoading.setVisibility(View.GONE));
                    e.printStackTrace();
                }
            }

            @Override 
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbMembersLoading.setVisibility(View.GONE);
                    isMember = false;
                    updateMembershipUI();
                });
            }
        });
    }

    private void updateMembershipUI() {
        if (isMember) {
            btnJoin.setVisibility(View.GONE);
            btnLeave.setVisibility(View.VISIBLE);
            if (sessionManager.getUserId().equals(adminId)) {
                fabAdd.setVisibility(View.VISIBLE);
            }
        } else {
            btnLeave.setVisibility(View.GONE);
            fabAdd.setVisibility(View.GONE);
            if (isPublicGroup) {
                btnJoin.setVisibility(View.VISIBLE);
                // Check if already requested
                checkPendingStatus();
            } else {
                btnJoin.setVisibility(View.GONE);
            }
        }
    }

    private void checkPendingStatus() {
        supabaseAuth.fetchMyJoinRequests(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject obj = arr.getJSONObject(i);
                        if (obj.getString("room_id").equals(roomId)) {
                            runOnUiThread(() -> {
                                btnJoin.setText("Request Pending");
                                btnJoin.setEnabled(false);
                            });
                            break;
                        }
                    }
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {}
        });
    }

    private void setupRecyclerView(List<User> members) {
        adapter = new UserAdapter(members, user -> {
            if (sessionManager.getUserId().equals(adminId) && !user.getUserId().equals(adminId)) {
                showRemoveMemberDialog(user);
            } else if (sessionManager.getUserId().equals(adminId) && user.getUserId().equals(adminId)) {
                Toast.makeText(this, "Admin cannot be removed", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Only admins can manage members", Toast.LENGTH_SHORT).show();
            }
        });
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        rvMembers.setAdapter(adapter);
    }

    private void showRemoveMemberDialog(User user) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Are you sure you want to remove " + user.getFullName() + " from this group?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    supabaseAuth.removeParticipant(roomId, user.getUserId(), new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            runOnUiThread(() -> {
                                Toast.makeText(GroupDetailsActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                                fetchMembers();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(GroupDetailsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditGroupNameDialog() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setText(roomName);
        input.setSelection(roomName.length());
        
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Group Name")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateGroupDetails(newName, null);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showEditGroupImageDialog() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Group Image"), PICK_IMAGE_REQUEST);
    }

    private void showCategoryDialog() {
        String[] options = {"Public Group", "Private Group"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Group Privacy")
                .setItems(options, (dialog, which) -> {
                    updateGroupCategory(which == 0);
                })
                .show();
    }

    private void fetchPendingRequests() {
        supabaseAuth.fetchPendingParticipants(roomId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    List<String> userIds = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        userIds.add(arr.getJSONObject(i).getString("user_id"));
                    }

                    if (userIds.isEmpty()) {
                        runOnUiThread(() -> findViewById(R.id.layoutPendingRequests).setVisibility(View.GONE));
                        return;
                    }

                    supabaseAuth.fetchUsersByIds(userIds, new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String usersJson) {
                            runOnUiThread(() -> {
                                try {
                                    JSONArray usersArr = new JSONArray(usersJson);
                                    List<User> pendingUsers = new ArrayList<>();
                                    for (int i = 0; i < usersArr.length(); i++) {
                                        JSONObject userObj = usersArr.getJSONObject(i);
                                        User u = new User();
                                        u.setUserId(userObj.getString("user_id"));
                                        u.setFullName(userObj.optString("full_name", "Farmer"));
                                        u.setProfileImage(userObj.optString("profile_image", ""));
                                        pendingUsers.add(u);
                                    }

                                    if (!pendingUsers.isEmpty()) {
                                        findViewById(R.id.layoutPendingRequests).setVisibility(View.VISIBLE);
                                        setupPendingRecyclerView(pendingUsers);
                                    } else {
                                        findViewById(R.id.layoutPendingRequests).setVisibility(View.GONE);
                                    }
                                } catch (Exception e) { e.printStackTrace(); }
                            });
                        }
                        @Override public void onError(String error) {}
                    });
                } catch (Exception e) { e.printStackTrace(); }
            }
            @Override public void onError(String error) {}
        });
    }

    private void setupPendingRecyclerView(List<User> requests) {
        joinRequestAdapter = new JoinRequestAdapter(requests, new JoinRequestAdapter.OnRequestClickListener() {
            @Override
            public void onAccept(User user) {
                handleRequest(user, "joined");
            }

            @Override
            public void onReject(User user) {
                handleRequest(user, "rejected");
            }
        });
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvPending.setAdapter(joinRequestAdapter);
    }

    private void handleRequest(User user, String status) {
        pbLoading.setVisibility(View.VISIBLE);
        if ("joined".equals(status)) {
            supabaseAuth.updateParticipantStatus(roomId, user.getUserId(), status, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(GroupDetailsActivity.this, "Request accepted", Toast.LENGTH_SHORT).show();
                        fetchMembers(); // Refresh both lists
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(GroupDetailsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            supabaseAuth.removeParticipant(roomId, user.getUserId(), new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(GroupDetailsActivity.this, "Request rejected", Toast.LENGTH_SHORT).show();
                        fetchPendingRequests();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        pbLoading.setVisibility(View.GONE);
                        Toast.makeText(GroupDetailsActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void updateGroupCategory(boolean isPublic) {
        try {
            JSONObject updates = new JSONObject();
            updates.put("is_public", isPublic);
            supabaseAuth.updateChatRoom(roomId, updates, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        isPublicGroup = isPublic;
                        tvGroupCategory.setText(isPublic ? "Public Group" : "Private Group");
                        Toast.makeText(GroupDetailsActivity.this, "Privacy updated", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(GroupDetailsActivity.this, error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {}
    }

    private void updateGroupDetails(String name, String base64Image) {
        try {
            JSONObject updates = new JSONObject();
            if (name != null) updates.put("name", name);
            if (base64Image != null) {
                // Using profile_image as added via SQL
                updates.put("profile_image", base64Image); 
            }

            supabaseAuth.updateChatRoom(roomId, updates, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    setResult(RESULT_OK);
                    runOnUiThread(() -> {
                        Toast.makeText(GroupDetailsActivity.this, "Group updated successfully", Toast.LENGTH_SHORT).show();
                        // Re-fetch from Supabase to show the live state
                        loadGroupDetails();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(GroupDetailsActivity.this, "Update failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
