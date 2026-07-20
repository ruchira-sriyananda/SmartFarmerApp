package com.example.smartfarmer.fragments;

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
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.BarterRequestAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.BarterRequest;
import com.example.smartfarmer.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class BarterRequestFragment extends Fragment {
    private RecyclerView rvRequests;
    private BarterRequestAdapter adapter;
    private List<BarterRequest> requests = new ArrayList<>();
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private boolean isReceivedType;

    public static BarterRequestFragment newInstance(boolean isReceived) {
        BarterRequestFragment fragment = new BarterRequestFragment();
        Bundle args = new Bundle();
        args.putBoolean("is_received", isReceived);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isReceivedType = getArguments().getBoolean("is_received");
        }
        supabaseAuth = new SupabaseAuthHelper(getContext());
        sessionManager = new SessionManager(getContext());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_barter_requests, container, false);
        rvRequests = view.findViewById(R.id.rvBarterRequests);
        rvRequests.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new BarterRequestAdapter(requests, isReceivedType, new BarterRequestAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(BarterRequest request) {
                updateStatus(request, "accepted");
            }

            @Override
            public void onReject(BarterRequest request) {
                updateStatus(request, "rejected");
            }
        });
        rvRequests.setAdapter(adapter);
        
        fetchRequests();
        return view;
    }

    private void fetchRequests() {
        SupabaseAuthHelper.AuthCallback callback = new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String json) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(json);
                        requests.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            BarterRequest req = new BarterRequest();
                            
                            if (obj.has("request_id")) {
                                req.setRequestId(obj.getString("request_id"));
                            } else if (obj.has("id")) {
                                req.setRequestId(obj.getString("id"));
                            }

                            req.setListingId(obj.optString("listing_id", ""));
                            req.setRequesterId(obj.optString("requester_id", ""));
                            req.setOfferedItem(obj.optString("offered_item", ""));
                            req.setRequestStatus(obj.optString("request_status", "pending"));
                            req.setCreatedAt(obj.optString("created_at", ""));
                            
                            if (obj.has("users") && !obj.isNull("users")) {
                                JSONObject user = obj.getJSONObject("users");
                                req.setRequesterName(user.optString("full_name", "Unknown Farmer"));
                                req.setRequesterProfileImage(user.optString("profile_image", ""));
                            }
                            
                            if (obj.has("barter_listings") && !obj.isNull("barter_listings")) {
                                JSONObject listing = obj.getJSONObject("barter_listings");
                                req.setListingTitle(listing.optString("title", "Item"));
                                
                                // If we don't have requester name (meaning it's a 'Sent' request), try to get owner name from listing
                                if (!isReceivedType && listing.has("users") && !listing.isNull("users")) {
                                    JSONObject owner = listing.getJSONObject("users");
                                    req.setRequesterName(owner.optString("full_name", "Unknown Farmer"));
                                    req.setRequesterProfileImage(owner.optString("profile_image", ""));
                                }
                            } else if (obj.has("barter_listings_title")) { // Fallback if handled differently in SQL join
                                req.setListingTitle(obj.optString("barter_listings_title", "Item"));
                            }
                            
                            requests.add(req);
                        }
                        adapter.updateList(requests);
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
        };

        if (isReceivedType) {
            supabaseAuth.fetchRequestsForMyListings(sessionManager.getUserId(), callback);
        } else {
            supabaseAuth.fetchMyBarterRequests(sessionManager.getUserId(), callback);
        }
    }

    private void updateStatus(BarterRequest request, String status) {
        supabaseAuth.updateBarterRequestStatus(request.getRequestId(), status, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                if (getActivity() == null) return;
                
                // Notify Requester
                String notificationMsg = "Your barter request for " + request.getListingTitle() + " was " + status;
                supabaseAuth.createNotification(
                    request.getRequesterId(),
                    getString(R.string.notification_barter_title),
                    notificationMsg,
                    "barter",
                    request.getListingId(),
                    new SupabaseAuthHelper.AuthCallback() {
                        @Override public void onSuccess(String data) {}
                        @Override public void onError(String error) {}
                    }
                );

                // If the request was accepted, mark the listing as 'Exchanged' so it hides from the hub
                if ("accepted".equalsIgnoreCase(status)) {
                    supabaseAuth.updateListingStatus(request.getListingId(), "Exchanged", new SupabaseAuthHelper.AuthCallback() {
                        @Override
                        public void onSuccess(String data) {
                            refreshUI(status);
                        }

                        @Override
                        public void onError(String error) {
                            refreshUI(status); // Still refresh even if listing update fails
                        }
                    });
                } else {
                    refreshUI(status);
                }
            }

            @Override
            public void onError(String error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void refreshUI(String status) {
        getActivity().runOnUiThread(() -> {
            Toast.makeText(getContext(), "Request " + status, Toast.LENGTH_SHORT).show();
            fetchRequests();
        });
    }
}
