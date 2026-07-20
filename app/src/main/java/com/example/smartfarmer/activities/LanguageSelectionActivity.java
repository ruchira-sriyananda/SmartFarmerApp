package com.example.smartfarmer.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.smartfarmer.R;
import com.example.smartfarmer.utils.LocaleHelper;
import com.example.smartfarmer.utils.SessionManager;

public class LanguageSelectionActivity extends BaseActivity {
    private SessionManager sessionManager;
    private Button btnSinhala, btnTamil, btnEnglish;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_selection);

        sessionManager = new SessionManager(this);

        btnSinhala = findViewById(R.id.btnSinhala);
        btnTamil = findViewById(R.id.btnTamil);
        btnEnglish = findViewById(R.id.btnEnglish);

        btnSinhala.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLanguage("si");
            }
        });

        btnTamil.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLanguage("ta");
            }
        });

        btnEnglish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setLanguage("en");
            }
        });
    }

    private void setLanguage(String languageCode) {
        try {
            // Save language preference
            sessionManager.setLanguage(languageCode);

            // Set locale
            LocaleHelper.setLocale(this, languageCode);

            // Show confirmation
            Toast.makeText(this, getString(R.string.language_changed), Toast.LENGTH_SHORT).show();

            // Navigate to login
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_changing_language, Toast.LENGTH_SHORT).show();
        }
    }
}