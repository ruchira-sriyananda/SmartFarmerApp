package com.smartfarmers.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.smartfarmers.R;
import com.smartfarmers.adapters.BarterAdapter;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.BarterListing;
import com.smartfarmers.models.BarterRequest;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BarterActivity extends BaseActivity {
    private RecyclerView rvBarter;
    private SwipeRefreshLayout swipeRefresh;
    private BarterAdapter adapter;
    private List<BarterListing> listings = new ArrayList<>();
    private List<BarterListing> filteredList = new ArrayList<>();
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private android.widget.EditText etSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barter);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);

        initViews();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchListings();
    }

    private void initViews() {
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarBarter);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.barter_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_my_barters) {
                startActivity(new Intent(this, MyBartersActivity.class));
                return true;
            }
            return false;
        });
        
        etSearch = findViewById(R.id.etSearchBarter);
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        findViewById(R.id.btnFilterBarter).setOnClickListener(v -> showFilterDialog());

        rvBarter = findViewById(R.id.rvBarterListings);
        rvBarter.setLayoutManager(new GridLayoutManager(this, 2));
        
        adapter = new BarterAdapter(filteredList, this::showRequestDialog);
        adapter.setCurrentUserId(sessionManager.getUserId());
        rvBarter.setAdapter(adapter);

        swipeRefresh = findViewById(R.id.swipeRefreshBarter);
        swipeRefresh.setOnRefreshListener(this::fetchListings);

        findViewById(R.id.fabCreateBarter).setOnClickListener(v -> {
            startActivity(new Intent(this, CreateBarterActivity.class));
        });
    }

    private void filter(String query) {
        filteredList.clear();
        if (query.isEmpty()) {
            filteredList.addAll(listings);
        } else {
            for (BarterListing item : listings) {
                if (item.getTitle().toLowerCase().contains(query.toLowerCase()) ||
                    item.getDescription().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(item);
                }
            }
        }
        adapter.updateList(filteredList);
    }

    private void showFilterDialog() {
        // Get unique districts from listings
        java.util.Set<String> districts = new java.util.HashSet<>();
        districts.add(getString(R.string.all_districts));
        for (BarterListing item : listings) {
            if (item.getDistrict() != null && !item.getDistrict().isEmpty()) {
                districts.add(item.getDistrict());
            }
        }
        
        String[] districtArray = districts.toArray(new String[0]);
        java.util.Arrays.sort(districtArray);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.filter_by_district)
                .setItems(districtArray, (dialog, which) -> {
                    String selected = districtArray[which];
                    if (selected.equals(getString(R.string.all_districts))) {
                        filteredList.clear();
                        filteredList.addAll(listings);
                        adapter.updateList(filteredList);
                    } else {
                        filteredList.clear();
                        for (BarterListing item : listings) {
                            if (selected.equals(item.getDistrict())) {
                                filteredList.add(item);
                            }
                        }
                        adapter.updateList(filteredList);
                    }
                })
                .show();
    }

    private void fetchListings() {
        swipeRefresh.setRefreshing(true);
        
        // Fetch existing requests first to identify duplicates
        supabaseAuth.fetchMyBarterRequests(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String requestsJson) {
                java.util.Set<String> requestedIds = new java.util.HashSet<>();
                try {
                    JSONArray reqArr = new JSONArray(requestsJson);
                    for (int i = 0; i < reqArr.length(); i++) {
                        requestedIds.add(reqArr.getJSONObject(i).optString("listing_id"));
                    }
                } catch (Exception e) {}
                
                runOnUiThread(() -> {
                    if (adapter != null) adapter.setRequestedListingIds(requestedIds);
                });
                
                // Now fetch all listings
                fetchAllListings();
            }

            @Override
            public void onError(String error) {
                // If fetching requests fails, just fetch listings anyway
                fetchAllListings();
            }
        });
    }

    private void fetchAllListings() {
        supabaseAuth.fetchBarterListings(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                android.util.Log.d("BarterActivity", "Received listings: " + json);
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    try {
                        JSONArray arr = new JSONArray(json);
                        List<BarterListing> newList = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            BarterListing item = new BarterListing();
                            
                            item.setListingId(obj.optString("listing_id", obj.optString("id", "")));
                            item.setUserId(obj.optString("user_id", ""));
                            item.setTitle(obj.optString("title", "No Title"));
                            item.setDescription(obj.optString("description", ""));
                            item.setQuantity(obj.optDouble("quantity", 0));
                            item.setUnit(obj.optString("unit", ""));
                            item.setImageUrl(obj.optString("image_url", ""));
                            item.setStatus(obj.optString("status", "Available"));
                            item.setType(obj.optString("type", "Goods"));
                            item.setModerationStatus(obj.optString("moderation_status", "Pending"));
                            
                            if (obj.has("users") && !obj.isNull("users")) {
                                try {
                                    JSONObject user = obj.getJSONObject("users");
                                    item.setUserName(user.optString("full_name", getString(R.string.unknown_farmer)));
                                    item.setDistrict(user.optString("district", ""));
                                } catch (Exception e) {
                                    item.setUserName(getString(R.string.unknown_farmer));
                                }
                            }
                            newList.add(item);
                        }
                        listings.clear();
                        listings.addAll(newList);
                        
                        // Apply current search if any
                        filter(etSearch.getText().toString());
                        
                        if (listings.isEmpty()) {
                            Toast.makeText(BarterActivity.this, R.string.no_barter_items, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(BarterActivity.this, R.string.data_parsing_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(BarterActivity.this, "Failed to fetch: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showRequestDialog(BarterListing listing) {
        if (listing.getUserId().equals(sessionManager.getUserId())) {
            Toast.makeText(this, R.string.cannot_request_own_item, Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_make_barter_request, null);
        TextInputEditText etOffer = dialogView.findViewById(R.id.etOfferedItem);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.make_barter_request)
                .setView(dialogView)
                .setPositiveButton(R.string.send_request, (dialog, which) -> {
                    String offer = etOffer.getText().toString().trim();
                    if (offer.isEmpty()) {
                        Toast.makeText(this, R.string.describe_offer_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendRequest(listing, offer);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendRequest(BarterListing listing, String offer) {
        BarterRequest req = new BarterRequest();
        req.setListingId(listing.getListingId());
        req.setRequesterId(sessionManager.getUserId());
        req.setOfferedItem(offer);

        supabaseAuth.createBarterRequest(req, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                // Notify Listing Owner
                supabaseAuth.createNotification(
                    listing.getUserId(),
                    getString(R.string.notification_barter_title),
                    sessionManager.getUserName() + " wants to barter for your " + listing.getTitle(),
                    "barter",
                    listing.getListingId() + "|" + sessionManager.getUserId(),
                    new SupabaseAuthHelper.AuthCallback() {
                        @Override public void onSuccess(String data) {}
                        @Override public void onError(String error) {}
                    }
                );
                runOnUiThread(() -> {
                    Toast.makeText(BarterActivity.this, R.string.request_sent, Toast.LENGTH_SHORT).show();
                    // Update UI to show requested status
                    fetchListings(); // Refresh to update button states
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(BarterActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
