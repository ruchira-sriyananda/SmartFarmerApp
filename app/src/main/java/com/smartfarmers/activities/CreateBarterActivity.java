package com.smartfarmers.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import com.smartfarmers.R;
import com.smartfarmers.auth.SupabaseAuthHelper;
import com.smartfarmers.models.BarterListing;
import com.smartfarmers.utils.SessionManager;
import com.github.ybq.android.spinkit.SpinKitView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class CreateBarterActivity extends BaseActivity {
    private static final int PICK_IMAGE = 100;
    private ImageView ivBarter;
    private android.widget.TextView tvSelectHint;
    private TextInputEditText etTitle, etQuantity, etUnit, etDescription;
    private com.google.android.material.button.MaterialButtonToggleGroup toggleType;
    private MaterialButton btnSubmit;
    private SpinKitView progressBar;
    private String base64Image = "";
    private SupabaseAuthHelper supabaseAuth;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_barter);

        supabaseAuth = new SupabaseAuthHelper();
        sessionManager = new SessionManager(this);

        initViews();
    }

    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarCreateBarter);
        toolbar.setNavigationOnClickListener(v -> finish());
        ivBarter = findViewById(R.id.ivNewBarterImage);
        tvSelectHint = findViewById(R.id.tvSelectHint);
        etTitle = findViewById(R.id.etBarterTitle);
        etQuantity = findViewById(R.id.etBarterQuantity);
        etUnit = findViewById(R.id.etBarterUnit);
        etDescription = findViewById(R.id.etBarterDescription);
        toggleType = findViewById(R.id.toggleBarterType);
        btnSubmit = findViewById(R.id.btnSubmitBarter);
        progressBar = findViewById(R.id.pbCreateBarter);

        findViewById(R.id.cardSelectBarterImage).setOnClickListener(v -> pickImage());
        btnSubmit.setOnClickListener(v -> submitListing());
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                ivBarter.setImageBitmap(bitmap);
                ivBarter.setPadding(0, 0, 0, 0);
                if (tvSelectHint != null) tvSelectHint.setVisibility(View.GONE);
                
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] bytes = baos.toByteArray();
                base64Image = Base64.encodeToString(bytes, Base64.DEFAULT);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void submitListing() {
        String title = etTitle.getText().toString().trim();
        String qtyStr = etQuantity.getText().toString().trim();
        String unit = etUnit.getText().toString().trim();
        String desc = etDescription.getText().toString().trim();

        if (title.isEmpty() || qtyStr.isEmpty() || unit.isEmpty() || base64Image.isEmpty()) {
            Toast.makeText(this, R.string.fill_fields_and_image, Toast.LENGTH_SHORT).show();
            return;
        }

        double quantity = 0;
        try {
            quantity = Double.parseDouble(qtyStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.enter_valid_quantity, Toast.LENGTH_SHORT).show();
            return;
        }

        if (quantity <= 0) {
            Toast.makeText(this, R.string.quantity_greater_than_zero, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String type = "Goods";
        if (toggleType.getCheckedButtonId() == R.id.btnTypeServices) {
            type = "Services";
        }
        
        progressBar.setVisibility(View.VISIBLE);
        btnSubmit.setEnabled(false);

        BarterListing listing = new BarterListing();
        listing.setUserId(sessionManager.getUserId());
        listing.setTitle(title);
        listing.setQuantity(quantity);
        listing.setUnit(unit);
        listing.setDescription(desc);
        listing.setImageUrl(base64Image);
        listing.setType(type);

        supabaseAuth.createBarterListing(listing, new SupabaseAuthHelper.AuthCallback() {
            @Override
            public void onSuccess(String data) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(CreateBarterActivity.this, R.string.listing_posted, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(CreateBarterActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
