package com.shahzaib.mycontacts;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.shahzaib.mycontacts.Adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private ViewPagerAdapter adapter;
    private int lastSelectedTabIndex = 1;


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
        // Set the current item to the Contacts tab whenever MainActivity resumes

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
//            viewPager.setCurrentItem(CONTACTS_TAB_INDEX, false);
            viewPager.setCurrentItem(lastSelectedTabIndex, false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewPager != null) {
            lastSelectedTabIndex = viewPager.getCurrentItem();
        }
    }

}