package com.example.smartfarmer.activities;

import android.os.Bundle;
import android.util.Base64;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.example.smartfarmer.R;

public class FullscreenImageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        ImageView ivFullscreen = findViewById(R.id.ivFullscreen);
        ImageButton btnClose = findViewById(R.id.btnClose);

        String imageStr = getIntent().getStringExtra("image_data");

        if (imageStr != null && !imageStr.isEmpty()) {
            if (imageStr.startsWith("http")) {
                Glide.with(this)
                        .load(imageStr)
                        .into(ivFullscreen);
            } else {
                try {
                    byte[] imageBytes = Base64.decode(imageStr, Base64.DEFAULT);
                    Glide.with(this)
                            .load(imageBytes)
                            .into(ivFullscreen);
                } catch (Exception e) {
                    ivFullscreen.setImageResource(android.R.drawable.ic_menu_report_image);
                }
            }
        }

        btnClose.setOnClickListener(v -> finish());
    }
}
