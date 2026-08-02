package com.smartfarmers.activities;

import android.os.Bundle;
import com.smartfarmers.R;
import com.google.android.material.appbar.MaterialToolbar;

public class AboutUsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);

        MaterialToolbar toolbar = findViewById(R.id.toolbarAboutUs);
        toolbar.setNavigationOnClickListener(v -> finish());
    }
}
