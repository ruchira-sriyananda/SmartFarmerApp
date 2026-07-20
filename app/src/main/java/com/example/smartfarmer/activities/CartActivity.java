package com.example.smartfarmer.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.CartAdapter;
import com.example.smartfarmer.models.Product;
import com.example.smartfarmer.utils.CartManager;
import java.util.List;
import java.util.Locale;

public class CartActivity extends BaseActivity {
    private RecyclerView rvCartItems;
    private CartAdapter cartAdapter;
    private CartManager cartManager;
    private TextView tvTotal, tvSubtotal;
    private View layoutEmptyCart, cardSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        cartManager = new CartManager(this);

        initViews();
        setupRecyclerView();
        updateSummary();

        findViewById(R.id.toolbarCart).setOnClickListener(v -> finish());
    }

    private void initViews() {
        rvCartItems = findViewById(R.id.rvCartItems);
        tvTotal = findViewById(R.id.tvCartTotal);
        tvSubtotal = findViewById(R.id.tvCartSubtotal);
        layoutEmptyCart = findViewById(R.id.layoutEmptyCart);
        cardSummary = findViewById(R.id.cardSummary);
        View btnCheckout = findViewById(R.id.btnCheckoutAll);

        btnCheckout.setOnClickListener(v -> {
            if (cartManager.getCartCount() == 0) {
                Toast.makeText(this, "Your cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            processBulkCheckout();
        });

        findViewById(R.id.btnStartShopping).setOnClickListener(v -> finish());
    }

    private void processBulkCheckout() {
        // Bulk checkout simulation
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Order")
                .setMessage("Do you want to place orders for all " + cartManager.getCartCount() + " items?")
                .setPositiveButton("Yes, Pay Now", (dialog, which) -> {
                    Toast.makeText(this, "Redirecting to Payment Gateway...", Toast.LENGTH_SHORT).show();
                    // In real app: loop through items and record transactions
                    new android.os.Handler().postDelayed(() -> {
                        cartManager.clearCart();
                        refreshCart();
                        Toast.makeText(this, "Order Placed Successfully!", Toast.LENGTH_LONG).show();
                    }, 2000);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupRecyclerView() {
        List<Product> items = cartManager.getCartItems();
        cartAdapter = new CartAdapter(items, (product, position) -> {
            cartManager.removeFromCart(product.getId());
            refreshCart();
            Toast.makeText(this, "Item removed", Toast.LENGTH_SHORT).show();
        });
        rvCartItems.setLayoutManager(new LinearLayoutManager(this));
        rvCartItems.setAdapter(cartAdapter);

        // Swipe to delete
        new androidx.recyclerview.widget.ItemTouchHelper(new androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT | androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                Product p = cartManager.getCartItems().get(position);
                cartManager.removeFromCart(p.getId());
                refreshCart();
                Toast.makeText(CartActivity.this, "Item removed from cart", Toast.LENGTH_SHORT).show();
            }
        }).attachToRecyclerView(rvCartItems);
        
        toggleEmptyView(items.isEmpty());
    }

    private void refreshCart() {
        List<Product> items = cartManager.getCartItems();
        cartAdapter.updateItems(items);
        updateSummary();
        toggleEmptyView(items.isEmpty());
    }

    private void toggleEmptyView(boolean isEmpty) {
        layoutEmptyCart.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        rvCartItems.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        cardSummary.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void updateSummary() {
        List<Product> items = cartManager.getCartItems();
        double total = 0;
        for (Product p : items) {
            total += p.getPrice();
        }
        String formatted = String.format(Locale.getDefault(), "Rs. %.2f", total);
        tvTotal.setText(formatted);
        tvSubtotal.setText(formatted);
    }
}
