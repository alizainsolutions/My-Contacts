package com.alizainsolutions.mycontacts;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.TelecomManager; // This import might not be necessary if not used

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.alizainsolutions.mycontacts.Adapter.ViewPagerAdapter;
import com.alizainsolutions.mycontacts.Fragments.HomeFragment;
import com.alizainsolutions.mycontacts.Fragments.FavouritesFragment;
import com.alizainsolutions.mycontacts.Fragments.RecentsFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ViewPagerAdapter adapter;

    // Define the index for the Contacts tab
    private static final int CONTACTS_TAB_INDEX = 1; // "Contacts" is at index 2 in your tabTitles array

    private final String[] tabTitles = new String[]{"Favourites", "Recents", "Contacts"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);

        adapter = new ViewPagerAdapter(this);
        viewPager.setUserInputEnabled(false);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(tabTitles[position]);
        }).attach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Set the current item to the Contacts tab whenever MainActivity resumes
        if (viewPager != null) {
            // Use false for smoothScroll to switch immediately without animation
            viewPager.setCurrentItem(CONTACTS_TAB_INDEX, false);
        }
    }
}