package com.smartfarmers.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.smartfarmers.R;
import com.smartfarmers.adapters.OrdersAdapter;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.Order;
import com.smartfarmers.utils.SessionManager;
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
        ordersAdapter = new OrdersAdapter(new ArrayList<>(), order -> {
            // No action needed
        });
        rvOrders.setLayoutManager(new LinearLayoutManager(this));
        rvOrders.setAdapter(ordersAdapter);
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
