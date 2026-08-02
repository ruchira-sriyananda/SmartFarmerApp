package com.smartfarmers.activities;

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
import com.smartfarmers.R;
import com.smartfarmers.adapters.AdAdapter;
import com.smartfarmers.adapters.MarketAdapter;
import com.smartfarmers.adapters.PackageAdapter;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.MobileAd;
import com.smartfarmers.models.Order;
import com.smartfarmers.models.Product;
import com.smartfarmers.models.SubscriptionPackage;
import com.smartfarmers.utils.SessionManager;
import com.google.android.material.tabs.TabLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import android.widget.ImageButton;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.paymentsheet.PaymentSheet;
import com.stripe.android.paymentsheet.PaymentSheetResult;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MarketActivity extends BaseActivity {
    private RecyclerView rvMarket, rvAds, rvPackages;
    private MarketAdapter marketAdapter;
    private AdAdapter adAdapter;
    private PackageAdapter packageAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TabLayout tabLayout;
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;
    private List<Product> allProducts = new ArrayList<>();
    private List<SubscriptionPackage> allPackages = new ArrayList<>();
    private com.github.ybq.android.spinkit.SpinKitView pbLoading;
    private TextView tvFeaturedLabel, tvAllProductsLabel;
    private android.widget.ImageView ivProductPreviewInDialog;
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

        initStripe();
        initViews();
        setupRecyclerViews();
        setupTabs();
        fetchData();

        findViewById(R.id.toolbarMarket).setOnClickListener(v -> finish());
        findViewById(R.id.fabAddProduct).setOnClickListener(v -> {
            showSubscriptionRequiredDialog();
        });
    }

    private void checkSubscriptionAndShowDialog() {
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.checkUserSubscription(sessionManager.getUserId(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(data);
                        if (arr.length() > 0) {
                            showAddProductDialog();
                        } else {
                            showSubscriptionRequiredDialog();
                        }
                    } catch (Exception e) {
                        showSubscriptionRequiredDialog();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    Toast.makeText(MarketActivity.this, "Error checking subscription: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showSubscriptionRequiredDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_subscription_packages, null);
        RecyclerView rv = dialogView.findViewById(R.id.rvDialogPackages);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        PackageAdapter adapter = new PackageAdapter(allPackages, pkg -> {
            showPackageCheckoutDialog(pkg);
        });
        rv.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btnCloseSubscriptionDialog).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void initViews() {
        rvMarket = findViewById(R.id.rvMarket);
        rvAds = findViewById(R.id.rvAds);
        rvPackages = findViewById(R.id.rvPackages);
        tvFeaturedLabel = findViewById(R.id.tvFeaturedLabel);
        tvAllProductsLabel = findViewById(R.id.tvAllProductsLabel);
        pbLoading = findViewById(R.id.pbMarket);
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
            public void onMessageSellerClick(Product product) {
                if (product.getSellerId().isEmpty()) return;
                
                Intent intent = new Intent(MarketActivity.this, ChatActivity.class);
                intent.putExtra("other_user_id", product.getSellerId());
                intent.putExtra("room_name", "Seller");
                intent.putExtra("tagged_product_id", product.getId());
                intent.putExtra("tagged_product_name", product.getName());
                intent.putExtra("tagged_product_image", product.getImageUrl());
                intent.putExtra("tagged_product_price", product.getPrice());
                startActivity(intent);
            }

            @Override
            public void onEditClick(Product product) {
                showEditProductDialog(product);
            }

            @Override
            public void onProductClick(Product product) {
                showProductDetailDialog(product);
            }
        });
        rvMarket.setLayoutManager(new LinearLayoutManager(this));
        rvMarket.setAdapter(marketAdapter);

        // Featured Ads
        adAdapter = new AdAdapter(new ArrayList<>(), ad -> {
            showAdDetailDialog(ad);
            supabaseAuth.recordAdInteraction(ad.getAdId(), "clicks", ad.getClicks(), new SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {}
                @Override public void onError(String error) {}
            });
        });
        rvAds.setAdapter(adAdapter);

        // Packages List
        packageAdapter = new PackageAdapter(new ArrayList<>(), pkg -> {
            showPackageCheckoutDialog(pkg);
        });
        rvPackages.setLayoutManager(new LinearLayoutManager(this));
        rvPackages.setAdapter(packageAdapter);
    }

    private void showPackageCheckoutDialog(SubscriptionPackage pkg) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkout, null);
        TextView tvName = dialogView.findViewById(R.id.tvCheckoutProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvCheckoutPrice);
        TextView tvTotal = dialogView.findViewById(R.id.tvCheckoutTotal);
        com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmCheckout);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelCheckout);

        tvName.setText(pkg.getPackageName());
        String details = pkg.getDescription() + "\nType: " + pkg.getAdType() + " | " + pkg.getDurationDays() + " Days";
        tvName.append("\n" + details);

        String formattedPrice = String.format("Rs. %.2f", pkg.getPrice());
        tvPrice.setText(formattedPrice);
        tvTotal.setText(formattedPrice);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            showPaymentMethodDialogForPackage(pkg);
        });

        dialog.show();
    }

    private void showPaymentMethodDialogForPackage(SubscriptionPackage pkg) {
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
            startStripePaymentForPackage(pkg);
        });

        dialogView.findViewById(R.id.btnCancelPayment).setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void startStripePaymentForPackage(SubscriptionPackage pkg) {
        if (pkg.getPrice() <= 0) {
            Toast.makeText(this, "Invalid package price", Toast.LENGTH_SHORT).show();
            return;
        }
        this.pendingPackage = pkg;
        pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.initiateStripePayment(pkg.getPrice(), "LKR", "Package: " + pkg.getPackageName(), new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONObject json = new JSONObject(data);
                        String clientSecret = json.getString("client_secret");
                        PaymentSheet.Configuration configuration = new PaymentSheet.Configuration.Builder("Smart Farmer").build();
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

    private void showAdDetailDialog(MobileAd ad) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ad_detail, null);
        android.widget.ImageView ivDetail = dialogView.findViewById(R.id.ivAdDetailImage);
        TextView tvTitle = dialogView.findViewById(R.id.tvAdDetailTitle);
        TextView tvDesc = dialogView.findViewById(R.id.tvAdDetailDescription);
        TextView tvBadge = dialogView.findViewById(R.id.tvAdDetailBadge);
        com.google.android.material.button.MaterialButton btnClose = dialogView.findViewById(R.id.btnAdAction);
        android.widget.ImageButton btnIconClose = dialogView.findViewById(R.id.btnCloseAdDetail);

        tvTitle.setText(ad.getTitle());
        tvDesc.setText(ad.getDescription());
        tvBadge.setVisibility(View.VISIBLE);

        if (ad.getImageUrl() != null && !ad.getImageUrl().isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(ad.getImageUrl(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivDetail.setImageBitmap(bitmap);
            } catch (Exception e) {
                ivDetail.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnIconClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showProductDetailDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ad_detail, null);
        android.widget.ImageView ivDetail = dialogView.findViewById(R.id.ivAdDetailImage);
        TextView tvTitle = dialogView.findViewById(R.id.tvAdDetailTitle);
        TextView tvDesc = dialogView.findViewById(R.id.tvAdDetailDescription);
        TextView tvBadge = dialogView.findViewById(R.id.tvAdDetailBadge);
        com.google.android.material.button.MaterialButton btnClose = dialogView.findViewById(R.id.btnAdAction);
        android.widget.ImageButton btnIconClose = dialogView.findViewById(R.id.btnCloseAdDetail);

        tvTitle.setText(product.getName());
        String description = product.getDescription();
        if (description == null || description.isEmpty()) description = product.getCategory();
        tvDesc.setText(description);

        tvBadge.setVisibility(product.isAd() ? View.VISIBLE : View.GONE);

        if (product.getPrice() > 0) {
            tvTitle.append("\nRs. " + String.format("%.2f", product.getPrice()));
        }

        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            try {
                byte[] decodedString = android.util.Base64.decode(product.getImageUrl(), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                ivDetail.setImageBitmap(bitmap);
            } catch (Exception e) {
                ivDetail.setImageResource(R.drawable.thumb_show_fotor_bg_remover_20260709171323);
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnIconClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showCheckoutDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_checkout, null);
        TextView tvName = dialogView.findViewById(R.id.tvCheckoutProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvCheckoutPrice);
        TextView tvTotal = dialogView.findViewById(R.id.tvCheckoutTotal);
        com.google.android.material.button.MaterialButton btnConfirm = dialogView.findViewById(R.id.btnConfirmCheckout);
        com.google.android.material.button.MaterialButton btnCancel = dialogView.findViewById(R.id.btnCancelCheckout);

        tvName.setText(product.getName());
        String formattedPrice = String.format("Rs. %.2f", product.getPrice());
        tvPrice.setText(formattedPrice);
        tvTotal.setText(formattedPrice);

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
            com.smartfarmers.utils.NotificationHelper.showNotification(this, title, message);

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
            transJson.put("status", "pending");
            
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
                    
                    // Refresh sales if we are the seller (common in testing)
                    if (sessionManager.getUserId().equals(sellerId)) {
                        fetchData();
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
                int position = tab.getPosition();
                filterProducts(position);
                
                boolean isShop = position == 0;
                boolean isMyListings = position == 1;

                rvAds.setVisibility(isShop ? View.VISIBLE : View.GONE);
                tvFeaturedLabel.setVisibility(isShop && adAdapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
                
                rvMarket.setVisibility(View.VISIBLE);
                tvAllProductsLabel.setVisibility(View.VISIBLE);
                rvPackages.setVisibility(View.GONE);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void fetchData() {
        swipeRefreshLayout.setRefreshing(true);
        fetchProducts();
        fetchAds();
        fetchPackages();
    }

    private void fetchPackages() {
        if (!swipeRefreshLayout.isRefreshing()) pbLoading.setVisibility(View.VISIBLE);
        supabaseAuth.fetchSubscriptionPackages(new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                runOnUiThread(() -> {
                    pbLoading.setVisibility(View.GONE);
                    try {
                        JSONArray arr = new JSONArray(jsonResponse);
                        allPackages.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            SubscriptionPackage pkg = new SubscriptionPackage();
                            pkg.setPackageId(obj.getString("package_id"));
                            pkg.setPackageName(obj.getString("package_name"));
                            pkg.setDescription(obj.optString("description", ""));
                            pkg.setPrice(obj.optDouble("price", 0.0));
                            pkg.setDurationDays(obj.optInt("duration_days", 30));
                            pkg.setAdType(obj.optString("ad_type", "Premium"));
                            pkg.setActive(obj.optBoolean("is_active", true));
                            pkg.setDisplayOrder(obj.optInt("display_order", 0));
                            
                            // Parse features array
                            JSONArray featuresArr = obj.optJSONArray("features");
                            if (featuresArr != null) {
                                List<String> features = new ArrayList<>();
                                for (int j = 0; j < featuresArr.length(); j++) {
                                    features.add(featuresArr.getString(j));
                                }
                                pkg.setFeatures(features);
                            }

                            allPackages.add(pkg);
                        }
                        packageAdapter.updatePackages(allPackages);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> pbLoading.setVisibility(View.GONE));
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

        if (tabIndex == 0) { // Shop - Don't show my own listings
            rvMarket.setVisibility(View.VISIBLE);
            for (Product p : allProducts) {
                if (!p.getSellerId().equals(currentUserId)) {
                    filteredList.add(p);
                }
            }
        } else if (tabIndex == 1) { // My Listings
            rvMarket.setVisibility(View.VISIBLE);
            for (Product p : allProducts) {
                if (p.getSellerId().equals(currentUserId)) {
                    filteredList.add(p);
                }
            }
        }
        marketAdapter.updateProducts(filteredList);
    }

    private void showAddProductDialog() {
        showAddProductDialogWithPackage(null);
    }

    private void showAddProductDialogWithPackage(String preSelectedPackage) {
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
            String packageName = preSelectedPackage;
            
            if (packageName == null && allPackages != null && !allPackages.isEmpty()) {
                packageName = allPackages.get(0).getPackageName();
            }


            if (name.isEmpty() || priceStr.isEmpty() || category.isEmpty() || description.isEmpty() || selectedProductImageBase64.isEmpty()) {
                Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Find selected package ID
                String selectedPackageId = null;
                if (allPackages != null && packageName != null) {
                    for (SubscriptionPackage pkg : allPackages) {
                        if (pkg.getPackageName().equals(packageName)) {
                            selectedPackageId = pkg.getPackageId();
                            break;
                        }
                    }
                }

                // Since mobile_advertisements IS the product table:
                JSONObject adJson = new JSONObject();
                String adId = UUID.randomUUID().toString();
                adJson.put("ad_id", adId);
                adJson.put("user_id", sessionManager.getUserId());
                adJson.put("package_id", selectedPackageId != null ? selectedPackageId : JSONObject.NULL);
                adJson.put("title", name);
                adJson.put("description", description.isEmpty() ? category : description);
                adJson.put("ad_type", "Premium");
                adJson.put("status", "active");
                
                // Store the VENDOR'S SELLING PRICE in amount_paid
                adJson.put("amount_paid", Double.parseDouble(priceStr));
                
                adJson.put("payment_status", "paid");
                adJson.put("user_email", sessionManager.getUserEmail());
                adJson.put("user_name", sessionManager.getUserName());
                adJson.put("image_url", selectedProductImageBase64);

                btnSubmit.setEnabled(false);
                supabaseAuth.addProduct(adJson, new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String data) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            fetchData();
                            Toast.makeText(MarketActivity.this, R.string.product_listed_success, Toast.LENGTH_SHORT).show();
                            
                            // Show notification
                            String title = getString(R.string.notification_market_title);
                            String message = "Your product '" + name + "' has been listed successfully!";
                            com.smartfarmers.utils.NotificationHelper.showNotification(MarketActivity.this, title, message);
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
                            com.smartfarmers.utils.NotificationHelper.showNotification(MarketActivity.this, title, message);

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
                processPackagePayment("Stripe", pendingPackage);
                pendingPackage = null;
            }
            Toast.makeText(this, "Stripe Payment Success!", Toast.LENGTH_SHORT).show();
        } else if (paymentSheetResult instanceof PaymentSheetResult.Failed) {
            Toast.makeText(this, "Payment Failed: " + ((PaymentSheetResult.Failed) paymentSheetResult).getError(), Toast.LENGTH_SHORT).show();
        }
    }

    private void processPackagePayment(String method, SubscriptionPackage pkg) {
        Toast.makeText(this, "Processing package: " + pkg.getPackageName(), Toast.LENGTH_SHORT).show();
        // Record transaction logic...
        JSONObject transJson = new JSONObject();
        try {
            transJson.put("user_id", sessionManager.getUserId());
            transJson.put("package_id", pkg.getPackageId());
            transJson.put("amount", pkg.getPrice());
            transJson.put("payment_method", method);
            transJson.put("transaction_reference", "PKG-" + System.currentTimeMillis());
            transJson.put("status", "completed");
            
            supabaseAuth.recordTransaction(transJson, new SupabaseAuthHelper.AuthCallback() {
                @Override public void onSuccess(String data) {
                    runOnUiThread(() -> {
                        Toast.makeText(MarketActivity.this, "Package purchased successfully!", Toast.LENGTH_LONG).show();
                        
                        // Create Notification
                        String title = "Package Activated";
                        String message = "Your " + pkg.getPackageName() + " has been activated successfully.";
                        supabaseAuth.createNotification(sessionManager.getUserId(), title, message, "subscription", pkg.getPackageId(), new SupabaseAuthHelper.AuthCallback() {
                            @Override public void onSuccess(String data) {}
                            @Override public void onError(String error) {}
                        });
                        
                        // Show Local Notification
                        com.smartfarmers.utils.NotificationHelper.showNotification(MarketActivity.this, title, message);
                        
                        // After successful payment, show the Add Product dialog with pre-selected package
                        showAddProductDialogWithPackage(pkg.getPackageName());
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(MarketActivity.this, "Transaction error: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        } catch (Exception e) {}
    }

    private void startStripePayment(Product product) {
        if (product.getPrice() <= 0) {
            Toast.makeText(this, "Invalid product price", Toast.LENGTH_SHORT).show();
            return;
        }
        this.pendingProduct = product;
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

}
