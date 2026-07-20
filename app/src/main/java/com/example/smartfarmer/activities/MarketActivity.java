package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.smartfarmer.R;
import com.example.smartfarmer.adapters.AdAdapter;
import com.example.smartfarmer.adapters.MarketAdapter;
import com.example.smartfarmer.adapters.PackageAdapter;
import com.example.smartfarmer.adapters.SalesAdapter;
import com.example.smartfarmer.auth.SupabaseAuthHelper;
import com.example.smartfarmer.models.MobileAd;
import com.example.smartfarmer.models.Order;
import com.example.smartfarmer.models.Product;
import com.example.smartfarmer.models.SubscriptionPackage;
import com.example.smartfarmer.utils.SessionManager;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import com.example.smartfarmer.utils.CartManager;
import android.widget.ImageButton;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MarketActivity extends BaseActivity {
    private RecyclerView rvMarket, rvAds, rvSales;
    private MarketAdapter marketAdapter;
    private AdAdapter adAdapter;
    private SalesAdapter salesAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TabLayout tabLayout;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private CartManager cartManager;
    private List<Product> allProducts = new ArrayList<>();
    private com.github.ybq.android.spinkit.SpinKitView pbLoading;
    private TextView tvFeaturedLabel, tvCartCount;
    private ImageButton btnViewCart;
    private String selectedProductImageBase64 = "";
    private static final int PICK_PRODUCT_IMAGE = 3001;
    private static final int LOAD_PAYMENT_DATA_REQUEST_CODE = 991;

    // Stripe
    private PaymentSheet paymentSheet;
    private String stripePublishableKey = "pk_test_51Tt9NCLlCDwDo6Fpk84D1mGj2BupNAdknCeCoUxAnntyDrOHkwYUrYVM0cMrLEFOvZ3UMmK3fktk2tiVl2ysGS4t00Yu3s7Wql";
    
    private Product pendingProduct;
    private SubscriptionPackage pendingPackage;

    private final String[] categories = {
            "Vegetables", "Fruits", "Grains & Pulses", "Spices",
            "Dairy Products", "Meat & Poultry", "Fertilizers",
            "Seeds", "Farm Tools", "Live Stock", "Organic Products",
            "Agri Machinery", "Land for Rent", "Farm Services", "Agri Consulting", "Export Crops", "Other"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_market);

        supabaseAuth = new SupabaseAuthHelper(this);
        sessionManager = new SessionManager(this);
        cartManager = new CartManager(this);

        initStripe();
        initViews();
        setupRecyclerViews();
        setupTabs();
        fetchData();
        updateCartBadge();

        findViewById(R.id.toolbarMarket).setOnClickListener(v -> finish());
        findViewById(R.id.fabAddProduct).setOnClickListener(v -> showSubscriptionDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateCartBadge();
    }

    private void initViews() {
        rvMarket = findViewById(R.id.rvMarket);
        rvAds = findViewById(R.id.rvAds);
        rvSales = findViewById(R.id.rvSales);
        tvFeaturedLabel = findViewById(R.id.tvFeaturedLabel);
        tvCartCount = findViewById(R.id.tvCartCount);
        btnViewCart = findViewById(R.id.btnViewCart);
        pbLoading = findViewById(R.id.pbMarket);
        ImageButton btnTrack = findViewById(R.id.btnTrackOrdersMarket);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshMarket);
        tabLayout = findViewById(R.id.tabLayoutMarket);
        android.widget.EditText etSearch = findViewById(R.id.etSearch);

        swipeRefreshLayout.setOnRefreshListener(this::fetchData);

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchProducts(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnViewCart.setOnClickListener(v -> {
            Intent intent = new Intent(this, CartActivity.class);
            startActivity(intent);
        });

        btnTrack.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrdersActivity.class);
            startActivity(intent);
        });
    }

    private void updateCartBadge() {
        int count = cartManager.getCartCount();
        if (count > 0) {
            tvCartCount.setText(String.valueOf(count));
            tvCartCount.setVisibility(View.VISIBLE);
        } else {
            tvCartCount.setVisibility(View.GONE);
        }
    }

    private void searchProducts(String query) {
        List<Product> searchResults = new ArrayList<>();
        String lowerQuery = query.toLowerCase().trim();
        
        for (Product p : allProducts) {
            if (p.getName().toLowerCase().contains(lowerQuery) || 
                p.getCategory().toLowerCase().contains(lowerQuery) ||
                p.getDescription().toLowerCase().contains(lowerQuery)) {
                
                // Also respect current tab filter
                int tabIndex = tabLayout.getSelectedTabPosition();
                if (tabIndex == 0 || p.getSellerId().equals(sessionManager.getUserId())) {
                    searchResults.add(p);
                }
            }
        }
        marketAdapter.updateProducts(searchResults);
    }

    private void setupRecyclerViews() {
        // Market List
        marketAdapter = new MarketAdapter(new ArrayList<>(), sessionManager.getUserId(), new MarketAdapter.OnProductClickListener() {
            @Override
            public void onBuyClick(Product product) {
                showCheckoutDialog(product);
            }

            @Override
            public void onAddToCartClick(Product product) {
                cartManager.addToCart(product);
                updateCartBadge();
                Toast.makeText(MarketActivity.this, getString(R.string.added_to_cart, product.getName()), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onMessageSellerClick(Product product) {
                if (product.getSellerId().isEmpty()) return;
                
                Intent intent = new Intent(MarketActivity.this, ChatActivity.class);
                intent.putExtra("other_user_id", product.getSellerId());
                intent.putExtra("room_name", "Seller");
                startActivity(intent);
            }

            @Override
            public void onEditClick(Product product) {
                showEditProductDialog(product);
            }
        });
        rvMarket.setLayoutManager(new LinearLayoutManager(this));
        rvMarket.setAdapter(marketAdapter);

        // Sales List
        salesAdapter = new SalesAdapter(new ArrayList<>(), this::showUpdateStatusDialog);
        rvSales.setLayoutManager(new LinearLayoutManager(this));
        rvSales.setAdapter(salesAdapter);

        // Featured Ads
        adAdapter = new AdAdapter(new ArrayList<>(), ad -> {
            Toast.makeText(this, getString(R.string.ad_clicked, ad.getTitle()), Toast.LENGTH_SHORT).show();
            supabaseAuth.recordAdInteraction(ad.getAdId(), "clicks", ad.getClicks(), new SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {}
                @Override public void onError(String error) {}
            });
        });
        rvAds.setAdapter(adAdapter);
    }

    private void showCheckoutDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkout, null);
        com.google.android.material.imageview.ShapeableImageView ivProduct = dialogView.findViewById(R.id.ivCheckoutProduct);
        TextView tvName = dialogView.findViewById(R.id.tvCheckoutProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvCheckoutPrice);
        TextView tvTotal = dialogView.findViewById(R.id.tvCheckoutTotal);
        com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmCheckout);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelCheckout);

        tvName.setText(product.getName());
        String formattedPrice = String.format("Rs. %.2f", product.getPrice());
        tvPrice.setText(formattedPrice);
        tvTotal.setText(formattedPrice);

        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(product.getImageUrl(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivProduct.setImageBitmap(bitmap);
            } catch (Exception e) {}
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            showPaymentMethodDialogForProduct(product);
        });

        dialog.show();
    }

    private void showPaymentMethodDialogForProduct(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_method, null);
        TextView tvAmount = dialogView.findViewById(R.id.tvPaymentAmount);
        tvAmount.setText(getString(R.string.total_amount, product.getPrice()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnStripe).setOnClickListener(v -> {
            dialog.dismiss();
            pendingProduct = product;
            startStripePayment(product);
        });

        dialogView.findViewById(R.id.btnCancelPayment).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void processProductPayment(String method, Product product) {
        Toast.makeText(this, getString(R.string.processing_payment, method), Toast.LENGTH_SHORT).show();
        
        new android.os.Handler().postDelayed(() -> {
            recordProductTransaction(product, method);
            
            String title = getString(R.string.notification_market_title);
            String message = "You successfully purchased " + product.getName() + " for Rs. " + product.getPrice();

            // Create Notification in DB
            supabaseAuth.createNotification(
                sessionManager.getUserId(),
                title,
                message,
                "market",
                product.getId() + "|" + product.getSellerId(),
                new SupabaseAuthHelper.AuthCallback() {
                    @Override public void onSuccess(String data) {}
                    @Override public void onError(String error) {}
                }
            );

            // Show Local Notification
            com.example.smartfarmer.utils.NotificationHelper.showNotification(this, title, message);

            com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), 
                R.string.purchase_successful, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                .setAction(R.string.track, v -> {
                    startActivity(new Intent(this, OrdersActivity.class));
                }).show();
        }, 2000);
    }

    private void recordProductTransaction(final Product product, String method) {
        try {
            JSONObject transJson = new JSONObject();
            transJson.put("user_id", sessionManager.getUserId()); // Buyer
            transJson.put("ad_id", product.getId()); // The product listing ID
            transJson.put("amount", product.getPrice());
            transJson.put("payment_method", method);
            transJson.put("transaction_reference", "BUY-" + System.currentTimeMillis());
            transJson.put("status", "completed");
            
            supabaseAuth.recordTransaction(transJson, new SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {
                    // Notify Seller
                    String sellerId = product.getSellerId();
                    if (sellerId != null && !sellerId.isEmpty()) {
                        supabaseAuth.createNotification(
                            sellerId,
                            getString(R.string.notification_market_title),
                            "Someone purchased your product: " + product.getName(),
                            "market",
                            product.getId() + "|" + sessionManager.getUserId(),
                            new SupabaseAuthHelper.AuthCallback() {
                                @Override public void onSuccess(String data) {}
                                @Override public void onError(String error) {}
                            }
                        );
                    }
                }
                @Override public void onError(String error) {}
            });
        } catch (Exception e) {}
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                filterProducts(tab.getPosition());
                // Hide ads on "My Listings" tab
                boolean isShop = tab.getPosition() == 0;
                rvAds.setVisibility(isShop ? View.VISIBLE : View.GONE);
                tvFeaturedLabel.setVisibility(isShop && adAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void fetchData() {
        swipeRefreshLayout.setRefreshing(true);
        fetchProducts();
        fetchAds();
        fetchSales();
    }

    private void fetchSales() {
        supabaseAuth.fetchSellerOrders(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        List<Order> sales = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            Order sale = new Order();
                            sale.setTransactionId(obj.getString("transaction_id"));
                            sale.setAmount(obj.getDouble("amount"));
                            sale.setStatus(obj.getString("status"));
                            sale.setDate(obj.getString("created_at"));
                            sale.setTransactionRef(obj.getString("transaction_reference"));
                            sale.setBuyerId(obj.getString("user_id"));
                            
                            if (obj.has("mobile_advertisements")) {
                                JSONObject ad = obj.getJSONObject("mobile_advertisements");
                                sale.setProductName(ad.optString("title", "Product"));
                                sale.setProductImageUrl(ad.optString("image_url", ""));
                            } else {
                                sale.setProductName("Market Product");
                            }
                            sales.add(sale);
                        }
                        salesAdapter.updateSales(sales);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            @Override public void onError(String error) {}
        });
    }

    private void showUpdateStatusDialog(Order sale) {
        String[] statuses = {"Pending", "Processing", "Shipped", "Delivered", "Completed", "Cancelled"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_order_status)
                .setItems(statuses, (dialog, which) -> {
                    updateSaleStatus(sale, statuses[which].toLowerCase());
                })
                .show();
    }

    private void updateSaleStatus(Order sale, String newStatus) {
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.updateOrderStatus(sale.getTransactionId(), newStatus, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                // Notify Buyer
                if (sale.getBuyerId() != null && !sale.getBuyerId().isEmpty()) {
                    supabaseAuth.createNotification(
                        sale.getBuyerId(),
                        getString(R.string.notification_market_title),
                        "Your order for " + sale.getProductName() + " status is now: " + newStatus.toUpperCase(),
                        "market",
                        sale.getTransactionRef(),
                        new SupabaseAuthHelper.AuthCallback() {
                            @Override public void onSuccess(String data) {}
                            @Override public void onError(String error) {}
                        }
                    );
                }

                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, R.string.order_status_updated, Toast.LENGTH_SHORT).show();
                    fetchSales();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchProducts() {
        if (!swipeRefreshLayout.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.fetchMarketProducts(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        allProducts.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            Product p = new Product();
                            // Map mobile_advertisements columns to Product model
                            p.setId(obj.optString("ad_id", UUID.randomUUID().toString()));
                            p.setName(obj.optString("title", "Unnamed Product"));
                            p.setDescription(obj.optString("description", ""));
                            p.setPrice(obj.optDouble("amount_paid", 0.0));
                            p.setCategory(obj.optString("ad_type", "General"));
                            p.setImageUrl(obj.optString("image_url", ""));
                            p.setAd(true); // Every entry in this table is an ad/listing
                            p.setSellerId(obj.optString("user_id", ""));
                            allProducts.add(p);
                        }
                        filterProducts(tabLayout.getSelectedTabPosition());
                    } catch (Exception e) {
                        e.printStackTrace();
                        Toast.makeText(MarketActivity.this, R.string.data_parsing_error, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(MarketActivity.this, "Market: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void fetchAds() {
        if (!swipeRefreshLayout.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.fetchActiveAds(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        List<MobileAd> ads = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            MobileAd ad = new MobileAd();
                            ad.setAdId(obj.getString("ad_id"));
                            ad.setTitle(obj.getString("title"));
                            ad.setDescription(obj.getString("description"));
                            ad.setImageUrl(obj.optString("image_url", ""));
                            ad.setClicks(obj.optInt("clicks", 0));
                            ad.setImpressions(obj.optInt("impressions", 0));
                            ads.add(ad);
                            
                            // Record impression
                            supabaseAuth.recordAdInteraction(ad.getAdId(), "impressions", ad.getImpressions(), new SupabaseAuthHelper.AuthCallback() {
                                @Override public void onSuccess(String data) {}
                                @Override public void onError(String error) {}
                            });
                        }
                        adAdapter.updateAds(ads);
                        tvFeaturedLabel.setVisibility(ads.isEmpty() ? View.GONE : View.VISIBLE);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                // Fail silently for ads
                runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_PRODUCT_IMAGE && data != null && data.getData() != null) {
            try {
                android.net.Uri uri = data.getData();
                android.graphics.Bitmap bitmap = android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                
                // Show preview in dialog if visible
                if (ivProductPreviewInDialog != null) {
                    ivProductPreviewInDialog.setImageBitmap(bitmap);
                    ivProductPreviewInDialog.setVisibility(View.VISIBLE);
                }

                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] bytes = baos.toByteArray();
                selectedProductImageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
                
                Toast.makeText(this, R.string.image_added, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void filterProducts(int tabIndex) {
        List<Product> filteredList = new ArrayList<>();
        String currentUserId = sessionManager.getUserId();

        if (tabIndex == 0) { // Shop
            rvMarket.setVisibility(View.VISIBLE);
            rvSales.setVisibility(View.GONE);
            filteredList.addAll(allProducts);
        } else if (tabIndex == 1) { // My Listings
            rvMarket.setVisibility(View.VISIBLE);
            rvSales.setVisibility(View.GONE);
            for (Product p : allProducts) {
                if (p.getSellerId().equals(currentUserId)) {
                    filteredList.add(p);
                }
            }
        } else { // Sales
            rvMarket.setVisibility(View.GONE);
            rvSales.setVisibility(View.VISIBLE);
            // Sales data is handled by salesAdapter separately
        }
        marketAdapter.updateProducts(filteredList);
    }

    private void showSubscriptionDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription, null);
        RecyclerView rvPackages = dialogView.findViewById(R.id.rvPackages);
        android.widget.ProgressBar pbPackages = dialogView.findViewById(R.id.pbPackages);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        rvPackages.setLayoutManager(new LinearLayoutManager(this));
        
        pbPackages.setVisibility(View.VISIBLE);
        supabaseAuth.fetchSubscriptionPackages(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbPackages.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(data);
                        List<SubscriptionPackage> pkgs = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            SubscriptionPackage p = new SubscriptionPackage();
                            p.setPackageId(obj.getString("package_id"));
                            p.setPackageName(obj.getString("package_name"));
                            p.setDescription(obj.getString("description"));
                            p.setPrice(obj.getDouble("price"));
                            p.setAdType(obj.getString("ad_type"));
                            pkgs.add(p);
                        }
                        
                        PackageAdapter adapter = new PackageAdapter(pkgs, pkg -> {
                            dialog.dismiss();
                            showPaymentMethodDialog(pkg);
                        });
                        rvPackages.setAdapter(adapter);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbPackages.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, R.string.failed_to_load_packages, Toast.LENGTH_SHORT).show();
                });
            }
        });

        dialogView.findViewById(R.id.tvFreePlan).setOnClickListener(v -> {
            dialog.dismiss();
            showAddProductDialog(null, "None"); // Null for Free
        });

        dialogView.findViewById(R.id.btnCancelSub).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showPaymentMethodDialog(SubscriptionPackage pkg) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment_method, null);
        TextView tvAmount = dialogView.findViewById(R.id.tvPaymentAmount);
        
        tvAmount.setText(getString(R.string.total_amount, pkg.getPrice()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnStripe).setOnClickListener(v -> {
            dialog.dismiss();
            pendingPackage = pkg;
            startStripePaymentForSubscription(pkg);
        });

        dialogView.findViewById(R.id.btnCancelPayment).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void processPayment(String method, SubscriptionPackage pkg) {
        // Simulate payment processing
        Toast.makeText(this, getString(R.string.processing_payment, method), Toast.LENGTH_SHORT).show();
        
        // In a real app, you would integrate Google Pay API or Stripe SDK here.
        // On success, proceed to list the product.
        new android.os.Handler().postDelayed(() -> {
            Toast.makeText(this, getString(R.string.payment_successful, method), Toast.LENGTH_SHORT).show();
            showAddProductDialog(pkg, method);
        }, 2000);
    }

    private android.widget.ImageView ivProductPreviewInDialog;

    private void showAddProductDialog(SubscriptionPackage selectedPackage, String paymentMethod) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.etProductName);
        com.google.android.material.textfield.TextInputEditText etPrice = dialogView.findViewById(R.id.etProductPrice);
        android.widget.AutoCompleteTextView etCategory = dialogView.findViewById(R.id.etProductCategory);
        com.google.android.material.textfield.TextInputEditText etDesc = dialogView.findViewById(R.id.etProductDescription);
        com.google.android.material.button.MaterialButton btnSelectImage = dialogView.findViewById(R.id.btnSelectProductImage);
        ivProductPreviewInDialog = dialogView.findViewById(R.id.ivProductPreview);
        com.google.android.material.button.MaterialButton btnSubmit = dialogView.findViewById(R.id.btnPostProduct);

        selectedProductImageBase64 = ""; // Reset

        // Set up category dropdown
        android.widget.ArrayAdapter<String> catAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, categories);
        etCategory.setAdapter(catAdapter);

        btnSelectImage.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_PRODUCT_IMAGE);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSubmit.setOnClickListener(v -> {
            if (etName.getText() == null || etPrice.getText() == null || etCategory.getText() == null || etDesc.getText() == null) return;
            String name = etName.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            String category = etCategory.getText().toString().trim();
            String description = etDesc.getText().toString().trim();

            if (name.isEmpty() || priceStr.isEmpty() || category.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Since mobile_advertisements IS the product table:
                JSONObject adJson = new JSONObject();
                String adId = UUID.randomUUID().toString();
                adJson.put("ad_id", adId);
                adJson.put("user_id", sessionManager.getUserId());
                adJson.put("package_id", selectedPackage != null ? selectedPackage.getPackageId() : null);
                adJson.put("title", name);
                adJson.put("description", description.isEmpty() ? category : description);
                adJson.put("ad_type", selectedPackage != null ? selectedPackage.getAdType() : "Free");
                adJson.put("status", "active");
                
                // Store the VENDOR'S SELLING PRICE in amount_paid
                adJson.put("amount_paid", Double.parseDouble(priceStr));
                
                adJson.put("payment_status", selectedPackage != null ? "paid" : "free");
                adJson.put("user_email", sessionManager.getUserEmail());
                adJson.put("user_name", sessionManager.getUserName());
                adJson.put("image_url", selectedProductImageBase64);

                btnSubmit.setEnabled(false);
                supabaseAuth.addProduct(adJson, new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String data) {
                        // If it's a paid transaction, record the PACKAGE PRICE in transactions
                        if (selectedPackage != null) {
                            recordPaymentTransaction(adId, selectedPackage, paymentMethod);
                        }
                        
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            fetchData();
                            Toast.makeText(MarketActivity.this, R.string.product_listed_success, Toast.LENGTH_SHORT).show();
                            
                            // Show notification
                            String title = getString(R.string.notification_market_title);
                            String message = "Your product '" + name + "' has been listed successfully!";
                            com.example.smartfarmer.utils.NotificationHelper.showNotification(MarketActivity.this, title, message);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(MarketActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        dialog.show();
    }

    private void recordPaymentTransaction(String adId, SubscriptionPackage pkg, String method) {
        try {
            JSONObject transJson = new JSONObject();
            transJson.put("user_id", sessionManager.getUserId());
            transJson.put("ad_id", adId);
            transJson.put("package_id", pkg.getPackageId());
            transJson.put("amount", pkg.getPrice());
            transJson.put("payment_method", method);
            transJson.put("transaction_reference", "SMFT-" + System.currentTimeMillis());
            transJson.put("status", "completed");
            
            supabaseAuth.recordTransaction(transJson, new SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {
                }
                @Override public void onError(String error) {}
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showEditProductDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_product, null);
        TextView tvTitle = dialogView.findViewById(android.R.id.title); // Note: I should add an ID to the title in XML
        // Actually, looking at the layout, it's just a TextView with "List Your Product"
        // For now, I'll just find by index or just leave it.

        com.google.android.material.textfield.TextInputEditText etName = dialogView.findViewById(R.id.etProductName);
        com.google.android.material.textfield.TextInputEditText etPrice = dialogView.findViewById(R.id.etProductPrice);
        android.widget.AutoCompleteTextView etCategory = dialogView.findViewById(R.id.etProductCategory);
        com.google.android.material.textfield.TextInputEditText etDesc = dialogView.findViewById(R.id.etProductDescription);
        com.google.android.material.button.MaterialButton btnSelectImage = dialogView.findViewById(R.id.btnSelectProductImage);
        ivProductPreviewInDialog = dialogView.findViewById(R.id.ivProductPreview);
        com.google.android.material.button.MaterialButton btnSubmit = dialogView.findViewById(R.id.btnPostProduct);

        // Pre-fill data
        etName.setText(product.getName());
        etPrice.setText(String.valueOf(product.getPrice()));
        etCategory.setText(product.getCategory(), false); // false prevents filtering the dropdown
        etDesc.setText(product.getDescription());
        btnSubmit.setText("Update Listing");

        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            selectedProductImageBase64 = product.getImageUrl();
            try {
                byte[] decodedString = android.util.Base64.decode(product.getImageUrl(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivProductPreviewInDialog.setImageBitmap(bitmap);
                ivProductPreviewInDialog.setVisibility(View.VISIBLE);
            } catch (Exception e) {}
        }

        // Set up category dropdown
        android.widget.ArrayAdapter<String> catAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, categories);
        etCategory.setAdapter(catAdapter);

        btnSelectImage.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, PICK_PRODUCT_IMAGE);
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnSubmit.setOnClickListener(v -> {
            if (etName.getText() == null || etPrice.getText() == null || etCategory.getText() == null || etDesc.getText() == null) return;
            String name = etName.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            String category = etCategory.getText().toString().trim();
            String description = etDesc.getText().toString().trim();

            if (name.isEmpty() || priceStr.isEmpty() || category.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject updates = new JSONObject();
                updates.put("title", name);
                updates.put("description", description.isEmpty() ? category : description);
                updates.put("amount_paid", Double.parseDouble(priceStr));
                updates.put("image_url", selectedProductImageBase64);
                updates.put("updated_at", new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(new java.util.Date()));

                btnSubmit.setEnabled(false);
                supabaseAuth.updateMobileAd(product.getId(), updates, new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String data) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            fetchData();
                            Toast.makeText(MarketActivity.this, R.string.listing_updated_success, Toast.LENGTH_SHORT).show();

                            // Show notification
                            String title = getString(R.string.notification_market_title);
                            String message = "Your listing '" + name + "' has been updated successfully!";
                            com.example.smartfarmer.utils.NotificationHelper.showNotification(MarketActivity.this, title, message);

                            // Also create record in DB
                            supabaseAuth.createNotification(sessionManager.getUserId(), title, message, "market", product.getId(), new SupabaseAuthHelper.AuthCallback() {
                                @Override public void onSuccess(String data) {}
                                @Override public void onError(String error) {}
                            });
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            btnSubmit.setEnabled(true);
                            Toast.makeText(MarketActivity.this, getString(R.string.update_failed, error), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        dialog.show();
    }

    private void initStripe() {
        PaymentConfiguration.init(getApplicationContext(), stripePublishableKey);
        paymentSheet = new PaymentSheet(this, this::onPaymentSheetResult);
    }

    private void onPaymentSheetResult(final PaymentSheetResult paymentSheetResult) {
        if (paymentSheetResult instanceof PaymentSheetResult.Completed) {
            if (pendingProduct != null) {
                processProductPayment("Stripe", pendingProduct);
                pendingProduct = null;
            } else if (pendingPackage != null) {
                processPayment("Stripe", pendingPackage);
                pendingPackage = null;
            }
            Toast.makeText(this, "Stripe Payment Success!", Toast.LENGTH_SHORT).show();
        } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            Toast.makeText(this, "Payment Failed: " + ((PaymentSheetResult.Failed) paymentSheetResult).getError(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startStripePayment(Product product) {
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.initiateStripePayment(product.getPrice(), "LKR", "Purchase: " + product.getName(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(data);
                        String clientSecret = json.getString("client_secret");
                        
                        PaymentSheet.Configuration configuration = new PaymentSheet.Configuration.Builder("Smart Farmer")
                                .build();
                                
                        paymentSheet.presentWithPaymentIntent(clientSecret, configuration);
                    } catch (Exception e) {
                        Toast.makeText(MarketActivity.this, "Payment error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, "Payment failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startStripePaymentForSubscription(SubscriptionPackage pkg) {
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.initiateStripePayment(pkg.getPrice(), "LKR", "Subscription: " + pkg.getPackageName(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(data);
                        String clientSecret = json.getString("client_secret");
                        
                        PaymentSheet.Configuration configuration = new PaymentSheet.Configuration.Builder("Smart Farmer")
                                .build();
                                
                        paymentSheet.presentWithPaymentIntent(clientSecret, configuration);
                    } catch (Exception e) {
                        Toast.makeText(MarketActivity.this, "Payment error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, "Payment failed: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
