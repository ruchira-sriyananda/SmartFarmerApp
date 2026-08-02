package com.smartfarmers.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.smartfarmers.R;
import com.google.android.material.appbar.MaterialToolbar;

public class ContactUsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_us);

        MaterialToolbar toolbar = findViewById(R.id.toolbarContact);
        toolbar.setNavigationOnClickListener(v -> finish());

        findViewById(R.id.cardCall).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_DIAL);
            intent.setData(Uri.parse("tel:+94112345678"));
            startActivity(intent);
        });

        findViewById(R.id.cardEmail).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:support@smartfarmer.lk"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "Support Request - SmartFarmer");
            startActivity(Intent.createChooser(intent, "Send Email"));
        });

        findViewById(R.id.cardWebsite).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("https://www.smartfarmer.lk"));
            startActivity(intent);
        });
    }
}
