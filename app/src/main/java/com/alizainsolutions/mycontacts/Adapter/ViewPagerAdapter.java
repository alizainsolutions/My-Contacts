package com.alizainsolutions.mycontacts.Adapter;


import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.alizainsolutions.mycontacts.Fragments.FavouritesFragment;
import com.alizainsolutions.mycontacts.Fragments.HomeFragment;
import com.alizainsolutions.mycontacts.Fragments.RecentsFragment;

public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new FavouritesFragment();
            case 1: return new RecentsFragment();
            case 2: return new HomeFragment();
            default: return new HomeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 3; // Total tabs
    }
}
