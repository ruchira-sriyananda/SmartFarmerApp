package com.smartfarmers.activities;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import com.smartfarmers.R;
import com.smartfarmers.fragments.BarterRequestFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MyBartersActivity extends BaseActivity {
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_barters);

        initViews();
    }

    private void initViews() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbarMyBarters);
        toolbar.setNavigationOnClickListener(v -> finish());
        tabLayout = findViewById(R.id.tabLayoutBarters);
        viewPager = findViewById(R.id.viewPagerBarters);

        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                return switch (position) {
                    case 0 -> new com.smartfarmers.fragments.MyBarterListingsFragment();
                    case 1 -> BarterRequestFragment.newInstance(true);
                    default -> BarterRequestFragment.newInstance(false);
                };
            }

            @Override
            public int getItemCount() {
                return 3;
            }
        });

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0 -> tab.setText("My Listings");
                case 1 -> tab.setText("Received");
                case 2 -> tab.setText("Sent");
            }
        }).attach();
    }
}
