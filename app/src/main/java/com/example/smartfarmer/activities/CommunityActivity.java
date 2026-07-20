package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.ChatRoomAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.ChatRoom;
import com.example.smartfarmer.utils.SessionManager;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class CommunityActivity extends BaseActivity {
    private RecyclerView rvChatRooms;
    private ChatRoomAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TabLayout tabLayout;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private List<ChatRoom> allRooms = new ArrayList<>();
    private com.example.smartfarmer.models.ChatDatabase localDb;
    private com.github.ybq.android.spinkit.SpinKitView pbLoading;
    private View layoutPrivacyInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_community);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);
        localDb = com.example.smartfarmer.models.ChatDatabase.getInstance(this);

        initViews();
        setupRecyclerView();
        setupTabs();
        setupToolbar();
        fetchChatRooms();

        findViewById(R.id.fabNewChat).setOnClickListener(v -> {
            startActivity(new Intent(this, UserSelectionActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchChatRooms();
    }

    private void initViews() {
        rvChatRooms = findViewById(R.id.rvChatRooms);
        swipeRefresh = findViewById(R.id.swipeRefreshCommunity);
        tabLayout = findViewById(R.id.tabLayoutCommunity);
        pbLoading = findViewById(R.id.pbCommunity);
        layoutPrivacyInfo = findViewById(R.id.layoutPrivacyInfo);
        swipeRefresh.setOnRefreshListener(this::fetchChatRooms);
    }

    private void setupToolbar() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarCommunity);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_community);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_create_group) {
                showCreateGroupDialog();
                return true;
            }
            return false;
        });
    }

    private void showCreateGroupDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_create_group, null);
        android.widget.EditText input = view.findViewById(R.id.etGroupName);
        com.google.android.material.switchmaterial.SwitchMaterial swPublic = view.findViewById(R.id.swPublicGroup);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.create_new_group)
                .setView(view)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    boolean isPublic = swPublic.isChecked();
                    if (!name.isEmpty()) createGroupWithName(name, isPublic);
                    else Toast.makeText(this, R.string.name_cannot_be_empty, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createGroupWithName(String name, boolean isPublic) {
        String newGroupId = "GRP-" + java.util.UUID.randomUUID().toString();
        
        // Register the room in Supabase chat_rooms table
        try {
            org.json.JSONObject roomJson = new org.json.JSONObject();
            roomJson.put("room_id", newGroupId);
            roomJson.put("name", name);
            roomJson.put("created_by", sessionManager.getUserId());
            roomJson.put("is_group", true);
            roomJson.put("is_public", isPublic);

            supabaseAuth.createChatRoom(roomJson, new SupabaseAuthHelper.AuthCallback() {
                @Override
                public void onSuccess(String realRoomId) {
                    runOnUiThread(() -> {
                        Toast.makeText(CommunityActivity.this, R.string.group_created_select_members, Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(CommunityActivity.this, UserSelectionActivity.class);
                        intent.putExtra("is_group_add", true);
                        intent.putExtra("room_id", realRoomId);
                        intent.putExtra("room_name", name);
                        intent.putExtra("is_new_group", true); // Flag to navigate to chat after
                        startActivity(intent);
                        fetchChatRooms();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(CommunityActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                fetchChatRooms();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void filterChats(int position) {
        List<ChatRoom> filtered = new ArrayList<>();
        boolean showGroups = position == 1;
        for (ChatRoom room : allRooms) {
            if (room.isGroup() == showGroups) {
                filtered.add(room);
            }
        }
        adapter.updateRooms(filtered);
    }

    private void setupRecyclerView() {
        adapter = new ChatRoomAdapter(new ArrayList<>(), room -> {
            if (room.isGroup()) {
                // For groups, check if we should join or open chat
                // We'll let GroupDetailsActivity handle the "Join" state if needed,
                // but if we are already a member, we go to ChatActivity.
                // Actually, a simpler way is to check if it's a 'discover' room by a flag or by checking if we have it in our local joined list.
                // For now, let's just always open GroupDetailsActivity for groups if they are clicked from 'Discover'
                // But wait, the adapter click currently doesn't know if it's 'Discover' or 'My Groups'.
                
                // If it ends with a certain marker or if it's from the discover fetch:
                if (room.isDiscover()) {
                    Intent intent = new Intent(this, GroupDetailsActivity.class);
                    intent.putExtra("room_id", room.getId());
                    intent.putExtra("room_name", room.getName());
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, ChatActivity.class);
                    intent.putExtra("room_id", room.getId());
                    intent.putExtra("admin_id", room.getAdminId());
                    intent.putExtra("room_name", room.getName());
                    startActivity(intent);
                }
            } else {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra("other_user_id", room.getId());
                intent.putExtra("room_name", room.getName());
                startActivity(intent);
            }
        });
        rvChatRooms.setLayoutManager(new LinearLayoutManager(this));
        rvChatRooms.setAdapter(adapter);
    }

    private void fetchChatRooms() {
        if (!swipeRefresh.isRefreshing()) {
            pbLoading.setVisibility(View.VISIBLE);
        }
        swipeRefresh.setRefreshing(true);
        int currentTab = tabLayout.getSelectedTabPosition();
        
        if (layoutPrivacyInfo != null) {
            layoutPrivacyInfo.setVisibility(currentTab == 0 ? View.VISIBLE : View.GONE);
        }
        
        if (currentTab == 0) { // Personal
            fetchPersonalChats();
        } else { // Groups
            fetchGroups();
        }
    }

    private void fetchPersonalChats() {
        // First, build rooms from local messages for instant offline display
        new Thread(() -> {
            List<com.example.smartfarmer.models.MessageEntity> allLocal = localDb.messageDao().getAllMessages();
            java.util.Map<String, ChatRoom> localUnique = new java.util.LinkedHashMap<>();
            String myId = sessionManager.getUserId();
            
            for (com.example.smartfarmer.models.MessageEntity msg : allLocal) {
                // IMPORTANT: Only show messages where I am involved (sender or receiver)
                boolean isMine = myId.equals(msg.getSenderId()) || myId.equals(msg.getReceiverId());
                if (!isMine) continue;

                // Ensure this is a personal message and NOT a group message
                String receiverId = msg.getReceiverId();
                if (receiverId != null && receiverId.startsWith("GRP-")) continue;

                String otherId = msg.getSenderId().equals(myId) ? receiverId : msg.getSenderId();
                if (otherId == null || otherId.isEmpty() || otherId.equals(myId)) continue;
                
                if (!localUnique.containsKey(otherId)) {
                    ChatRoom room = new ChatRoom();
                    room.setId(otherId);
                    room.setName("Loading..."); // Will refresh with real name
                    room.setGroup(false);
                    String seed = com.example.smartfarmer.utils.EncryptionUtils.getConversationSeed(myId, otherId);
                    room.setLastMessage(com.example.smartfarmer.utils.EncryptionUtils.decrypt(msg.getMessageText(), seed));
                    room.setLastMessageTime(msg.getSentAt());
                    localUnique.put(otherId, room);
                    
                    // Fetch real user details
                    fetchUserDetails(otherId, room);
                }
            }
            
            if (!localUnique.isEmpty()) {
                runOnUiThread(() -> {
                    List<ChatRoom> localRooms = new ArrayList<>(localUnique.values());
                    // Sort by time descending to ensure latest is on top
                    java.util.Collections.sort(localRooms, (r1, r2) -> {
                        String t1 = r1.getLastMessageTime();
                        String t2 = r2.getLastMessageTime();
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    });
                    adapter.updateRooms(localRooms);
                });
            }
        }).start();

        supabaseAuth.fetchPersonalChatList(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    if (tabLayout.getSelectedTabPosition() != 0) return;
                    swipeRefresh.setRefreshing(false);
                    try {
                        JSONArray arr = new JSONArray(json);
                        List<ChatRoom> personalRooms = new ArrayList<>();
                        java.util.Map<String, ChatRoom> uniquePersonal = new java.util.HashMap<>();
                        String myId = sessionManager.getUserId();

                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String senderId = obj.optString("sender_id", "");
                            String receiverId = obj.optString("receiver_id", "");
                            
                            // Skip if it's a group message (handled in Groups tab)
                            if (receiverId.startsWith("GRP-")) continue;

                            // Determine who the "other" person is
                            String otherId;
                            if (senderId.equals(myId)) {
                                otherId = receiverId;
                            } else {
                                otherId = senderId;
                            }

                            // Only add if we have a valid otherId and haven't added this user yet
                            if (!otherId.isEmpty() && !otherId.equals(myId) && !uniquePersonal.containsKey(otherId)) {
                                ChatRoom room = new ChatRoom();
                                room.setId(otherId);
                                room.setName("Loading...");
                                room.setGroup(false);
                                String seed = com.example.smartfarmer.utils.EncryptionUtils.getConversationSeed(myId, otherId);
                                room.setLastMessage(com.example.smartfarmer.utils.EncryptionUtils.decrypt(obj.optString("message_text", ""), seed));
                                room.setLastMessageTime(obj.optString("sent_at", ""));
                                uniquePersonal.put(otherId, room);
                                personalRooms.add(room);

                                fetchUserDetails(otherId, room);
                            }
                        }
                        
                        // Sort by time descending to ensure latest is on top
                        java.util.Collections.sort(personalRooms, (r1, r2) -> {
                            String t1 = r1.getLastMessageTime();
                            String t2 = r2.getLastMessageTime();
                            if (t1 == null) return 1;
                            if (t2 == null) return -1;
                            return t2.compareTo(t1);
                        });

                        adapter.updateRooms(personalRooms);
                    } catch (Exception e) { e.printStackTrace(); }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> { 
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false); 
                    Toast.makeText(CommunityActivity.this, error, Toast.LENGTH_SHORT).show(); 
                });
            }
        });
    }

    private void fetchUserDetails(String userId, ChatRoom room) {
        supabaseAuth.getUserProfile(userId, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                try {
                    JSONObject user = new JSONObject(json);
                    String name = user.optString("full_name", "Farmer " + userId.substring(0, 4));
                    String image = user.optString("profile_image", "");
                    
                    runOnUiThread(() -> {
                        room.setName(name);
                        room.setImageUrl(image);
                        adapter.notifyDataSetChanged();
                    });
                } catch (Exception e) {}
            }
            @Override public void onError(String error) {}
        });
    }

    private void fetchGroups() {
        if (!swipeRefresh.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.fetchMyGroups(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String myGroupsJson) {
                supabaseAuth.fetchDiscoverGroups(new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String discoverJson) {
                        runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            if (tabLayout.getSelectedTabPosition() != 1) return;
                            swipeRefresh.setRefreshing(false);
                            try {
                                List<ChatRoom> allGroupRooms = new ArrayList<>();
                                java.util.Set<String> myGroupIds = new java.util.HashSet<>();

                                // Parse My Groups
                                JSONArray myArr = new JSONArray(myGroupsJson);
                                for (int i = 0; i < myArr.length(); i++) {
                                    JSONObject obj = myArr.getJSONObject(i);
                                    if (obj.has("chat_rooms")) {
                                        JSONObject roomObj = obj.getJSONObject("chat_rooms");
                                        ChatRoom room = parseRoom(roomObj);
                                        if (room != null) {
                                            allGroupRooms.add(room);
                                            myGroupIds.add(room.getId());
                                        }
                                    }
                                }

                                // Parse Discover Groups (only if not already a member)
                                JSONArray discArr = new JSONArray(discoverJson);
                                for (int i = 0; i < discArr.length(); i++) {
                                    JSONObject roomObj = discArr.getJSONObject(i);
                                    String roomId = roomObj.getString("room_id");
                                    if (!myGroupIds.contains(roomId)) {
                                        ChatRoom room = parseRoom(roomObj);
                                        if (room != null) {
                                            room.setDiscover(true);
                                            allGroupRooms.add(room);
                                        }
                                    }
                                }
                                
                                adapter.updateRooms(allGroupRooms);
                            } catch (Exception e) { e.printStackTrace(); }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            pbLoading.setVisibility(View.GONE);
                            swipeRefresh.setRefreshing(false);
                            // If discover fails, just show my groups
                            try {
                                List<ChatRoom> myRooms = new ArrayList<>();
                                JSONArray myArr = new JSONArray(myGroupsJson);
                                for (int i = 0; i < myArr.length(); i++) {
                                    JSONObject obj = myArr.getJSONObject(i);
                                    if (obj.has("chat_rooms")) {
                                        ChatRoom r = parseRoom(obj.getJSONObject("chat_rooms"));
                                        if (r != null) myRooms.add(r);
                                    }
                                }
                                adapter.updateRooms(myRooms);
                            } catch (Exception e) {}
                        });
                    }
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> { 
                    pbLoading.setVisibility(View.GONE);
                    swipeRefresh.setRefreshing(false); 
                    Toast.makeText(CommunityActivity.this, "Error fetching groups: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private ChatRoom parseRoom(JSONObject roomObj) throws org.json.JSONException {
        if (roomObj.optBoolean("is_group", false) || roomObj.getString("room_id").startsWith("GRP-")) {
            ChatRoom room = new ChatRoom();
            room.setId(roomObj.getString("room_id"));
            room.setName(roomObj.getString("name"));
            room.setAdminId(roomObj.optString("created_by", ""));
            String roomImg = roomObj.optString("profile_image", 
                            roomObj.optString("image_url", 
                            roomObj.optString("avatar_url", 
                            roomObj.optString("image", ""))));
            room.setImageUrl(roomImg);
            room.setGroup(true);
            return room;
        }
        return null;
    }

}
