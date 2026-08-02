package com.smartfarmers.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.smartfarmers.R;
import com.smartfarmers.adapters.BarterAdapter;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.BarterListing;
import com.smartfarmers.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MyBarterListingsFragment extends Fragment {
    private RecyclerView rvListings;
    private BarterAdapter adapter;
    private List<BarterListing> listings = new ArrayList<>();
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_barter_requests, container, false);
        rvListings = view.findViewById(R.id.rvBarterRequests);
        rvListings.setLayoutManager(new LinearLayoutManager(getContext()));
        
        supabaseAuth = new SupabaseAuthHelper(getContext());
        sessionManager = new SessionManager(getContext());

        adapter = new BarterAdapter(listings, null); // No action listener needed for my own listings here
        adapter.setCurrentUserId(sessionManager.getUserId());
        rvListings.setAdapter(adapter);
        
        fetchMyListings();
        return view;
    }

    private void fetchMyListings() {
        supabaseAuth.fetchMyBarterListings(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
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
                            
                            // Try to get user info if joined
                            if (obj.has("users") && !obj.isNull("users")) {
                                try {
                                    JSONObject user = obj.getJSONObject("users");
                                    item.setUserName(user.optString("full_name", "Me"));
                                    item.setDistrict(user.optString("district", ""));
                                } catch (Exception e) {
                                    item.setUserName("Me");
                                }
                            } else {
                                item.setUserName("Me");
                            }
                            
                            newList.add(item);
                        }
                        listings.clear();
                        listings.addAll(newList);
                        adapter.updateList(listings);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}
