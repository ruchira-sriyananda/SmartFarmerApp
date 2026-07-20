package com.example.smartfarmer.activities;

import android.os.Bundle;
import com.example.smartfarmer.R;
import com.google.android.material.appbar.MaterialToolbar;

public class PrivacyPolicyActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        MaterialToolbar toolbar = findViewById(R.id.toolbarPrivacy);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
