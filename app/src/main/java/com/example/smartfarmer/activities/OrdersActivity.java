package com.example.smartfarmer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.OrdersAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.Order;
import com.example.smartfarmer.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrdersActivity extends BaseActivity {
    private RecyclerView rvOrders;
    private OrdersAdapter ordersAdapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvNoOrders;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);

        initViews();
        setupRecyclerView();
        fetchOrders();

        findViewById(R.id.toolbarOrders).setOnClickListener(v -> finish());
    }

    private void initViews() {
        rvOrders = findViewById(R.id.rvOrders);
        swipeRefresh = findViewById(R.id.swipeRefreshOrders);
        tvNoOrders = findViewById(R.id.tvNoOrders);

        swipeRefresh.setOnRefreshListener(this::fetchOrders);
    }

    private void setupRecyclerView() {
        ordersAdapter = new OrdersAdapter(new ArrayList<>(), this::showTrackingDialog);
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        rvOrders.setAdapter(ordersAdapter);
    }

    private void showTrackingDialog(Order order) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_track_order, null);
        TextView tvRef = dialogView.findViewById(R.id.tvTrackOrderRef);
        View dotProcessing = dialogView.findViewById(R.id.dotProcessing);
        View dotShipped = dialogView.findViewById(R.id.dotShipped);
        View dotDelivered = dialogView.findViewById(R.id.dotDelivered);
        View lineShipped = dialogView.findViewById(R.id.lineShipped);
        View lineDelivered = dialogView.findViewById(R.id.lineDelivered);
        
        tvRef.setText("Ref: " + order.getTransactionRef());

        // Dynamic status coloring based on order.getStatus()
        String status = order.getStatus().toLowerCase();
        int activeColor = android.graphics.Color.parseColor("#4CAF50");
        int inactiveColor = android.graphics.Color.parseColor("#EEEEEE");

        if ("processing".equals(status) || "shipped".equals(status) || "delivered".equals(status)) {
            dotProcessing.getBackground().setTint(activeColor);
        }

        if ("shipped".equals(status) || "delivered".equals(status)) {
            lineShipped.setBackgroundColor(activeColor);
            dotShipped.getBackground().setTint(activeColor);
        }

        if ("delivered".equals(status)) {
            lineDelivered.setBackgroundColor(activeColor);
            dotDelivered.getBackground().setTint(activeColor);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCloseTrack).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void fetchOrders() {
        swipeRefresh.setRefreshing(true);
        supabaseAuth.fetchUserOrders(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        List<Order> orders = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            
                            String ref = obj.optString("transaction_reference", "");
                            // Process both BUY (products) and SMFT (subscriptions)
                            if (!ref.startsWith("BUY-") && !ref.startsWith("SMFT-")) continue;

                            Order order = new Order();
                            order.setTransactionId(obj.optString("transaction_id", UUID.randomUUID().toString()));
                            order.setAmount(obj.optDouble("amount", 0.0));
                            order.setStatus(obj.optString("status", "pending"));
                            order.setDate(obj.optString("created_at", ""));
                            order.setTransactionRef(ref);

                            // Try to get product details from the join
                            if (obj.has("mobile_advertisements") && !obj.isNull("mobile_advertisements")) {
                                JSONObject ad = obj.getJSONObject("mobile_advertisements");
                                order.setProductName(ad.optString("title", "Product"));
                                order.setProductImageUrl(ad.optString("image_url", ""));
                            } else {
                                if (ref.startsWith("BUY-")) {
                                    order.setProductName("Product Purchase");
                                } else {
                                    order.setProductName("Ad Subscription");
                                }
                                order.setProductImageUrl("");
                            }

                            orders.add(order);
                        }

                        if (orders.isEmpty()) {
                            tvNoOrders.setVisibility(View.VISIBLE);
                            rvOrders.setVisibility(View.GONE);
                        } else {
                            tvNoOrders.setVisibility(View.GONE);
                            rvOrders.setVisibility(View.VISIBLE);
                            ordersAdapter.updateOrders(orders);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(OrdersActivity.this, "Error parsing orders", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(OrdersActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
