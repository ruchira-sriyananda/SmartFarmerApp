package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.UserAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.User;
import com.example.smartfarmer.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class UserSelectionActivity extends BaseActivity {
    private RecyclerView rvUsers;
    private UserAdapter adapter;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private com.github.ybq.android.spinkit.SpinKitView pbUsers;
    private List<User> allUsers = new ArrayList<>();
    private com.google.android.material.button.MaterialButton btnConfirm;
    private boolean isGroupAdd;
    private boolean isNewGroup;
    private String roomId;
    private String roomName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_selection);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);
        isGroupAdd = getIntent().getBooleanExtra("is_group_add", false);
        isNewGroup = getIntent().getBooleanExtra("is_new_group", false);
        roomId = getIntent().getStringExtra("room_id");
        roomName = getIntent().getStringExtra("room_name");

        initViews();
        setupRecyclerView();
        fetchUsers();

        if (isGroupAdd) {
            com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarUserSelection);
            toolbar.setTitle(getString(R.string.add_members));
        }

        findViewById(R.id.toolbarUserSelection).setOnClickListener(null);
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.toolbarUserSelection)).setNavigationOnClickListener(v -> finish());
    }

    private void initViews() {
        rvUsers = findViewById(R.id.rvUsers);
        pbUsers = findViewById(R.id.pbUsers);
        btnConfirm = findViewById(R.id.btnConfirmSelection);
        android.widget.EditText etSearch = findViewById(R.id.etSearchUsers);
        
        if (isGroupAdd) {
            btnConfirm.setVisibility(View.VISIBLE);
            btnConfirm.setOnClickListener(v -> addSelectedMembers());
        }

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterUsers(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void setupRecyclerView() {
        adapter = new UserAdapter(new ArrayList<>(), new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                if (!isGroupAdd) {
                    startChatWithUser(user);
                }
            }

            @Override
            public void onSelectionChanged(int count) {
                btnConfirm.setText(getString(R.string.confirm_selection, count));
                btnConfirm.setEnabled(count > 0);
            }
        });
        
        if (isGroupAdd) {
            adapter.setMultiSelect(true);
            btnConfirm.setEnabled(false);
        }
        
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(adapter);
    }

    private void addSelectedMembers() {
        java.util.Set<String> selectedIds = adapter.getSelectedUserIds();
        if (selectedIds.isEmpty()) return;

        pbUsers.setVisibility(View.VISIBLE);
        btnConfirm.setEnabled(false);
        
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(selectedIds.size());
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger(0);

        for (String userId : selectedIds) {
            supabaseAuth.addParticipant(roomId, userId, "joined", "member", new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String data) {
                    success.incrementAndGet();
                    checkCompletion(count, success);
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(UserSelectionActivity.this, "Failed to add " + userId + ": " + error, Toast.LENGTH_SHORT).show());
                    checkCompletion(count, success);
                }
            });
        }
    }

    private void checkCompletion(java.util.concurrent.atomic.AtomicInteger count, java.util.concurrent.atomic.AtomicInteger success) {
        if (count.decrementAndGet() == 0) {
            runOnUiThread(() -> {
                pbUsers.setVisibility(View.GONE);
                Toast.makeText(this, getString(R.string.members_added, success.get()), Toast.LENGTH_SHORT).show();
                
                if (isNewGroup) {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("room_id", roomId);
                    intent.putExtra("room_name", roomName);
                    intent.putExtra("admin_id", sessionManager.getUserId());
                    startActivity(intent);
                }
                setResult(RESULT_OK);
                finish();
            });
        }
    }

    private void filterUsers(String query) {
        List<User> filteredList = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        for (User u : allUsers) {
            if (u.getFullName().toLowerCase().contains(lowerQuery) || 
                u.getEmail().toLowerCase().contains(lowerQuery)) {
                filteredList.add(u);
            }
        }
        adapter = new UserAdapter(filteredList, new UserAdapter.OnUserClickListener() {
            @Override
            public void onUserClick(User user) {
                if (!isGroupAdd) {
                    startChatWithUser(user);
                }
            }

            @Override
            public void onSelectionChanged(int count) {
                btnConfirm.setText(getString(R.string.confirm_selection, count));
                btnConfirm.setEnabled(count > 0);
            }
        });
        
        if (isGroupAdd) {
            adapter.setMultiSelect(true);
        }

        rvUsers.setAdapter(adapter);
    }

    private void fetchUsers() {
        pbUsers.setVisibility(View.VISIBLE);
        supabaseAuth.fetchAllUsers(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    pbUsers.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(json);
                        allUsers.clear();
                        String myId = sessionManager.getUserId();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            if (obj.getString("user_id").equals(myId)) continue;
                            
                            User u = new User();
                            u.setUserId(obj.getString("user_id"));
                            u.setFullName(obj.optString("full_name", "Anonymous"));
                            u.setEmail(obj.optString("email", ""));
                            u.setProfileImage(obj.optString("profile_image", ""));
                            allUsers.add(u);
                        }
                        adapter = new UserAdapter(allUsers, new UserAdapter.OnUserClickListener() {
                            @Override
                            public void onUserClick(User user) {
                                if (!isGroupAdd) {
                                    startChatWithUser(user);
                                }
                            }

                            @Override
                            public void onSelectionChanged(int count) {
                                btnConfirm.setText("Confirm (" + count + ")");
                                btnConfirm.setEnabled(count > 0);
                            }
                        });

                        if (isGroupAdd) {
                            adapter.setMultiSelect(true);
                        }

                        rvUsers.setAdapter(adapter);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbUsers.setVisibility(View.GONE);
                    Toast.makeText(UserSelectionActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startChatWithUser(User user) {
        // For direct messages, we don't necessarily need to create a "room" in the chat_rooms table 
        // if we are using the sender_id/receiver_id schema.
        // We just jump to ChatActivity with the other user's ID.
        Intent intent = new Intent(UserSelectionActivity.this, ChatActivity.class);
        intent.putExtra("other_user_id", user.getUserId());
        intent.putExtra("room_name", user.getFullName());
        startActivity(intent);
        finish();
    }
}
