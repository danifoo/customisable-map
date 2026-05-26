package com.example.customisablemap;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * Adapter for managing the tab fragments in the map interface. It works with ViewPager2 to handle switching between three main tabs.
 * The adapter maintains references to pre-created fragment instances to preserve. their state across tab switches, rather than recreating them each time.
 */
public class MapTabsAdapter extends FragmentStateAdapter {
    private final RoutesFragment routesFragment;
    private final FiltersFragment filtersFragment;
    private final ProfilesFragment profilesFragment;

    /**
     * Creates a new MapTabsAdapter with pre-created fragment instances.
     *
     * @param fragmentActivity The host FragmentActivity
     * @param routesFragment Pre-created RoutesFragment
     * @param filtersFragment Pre-created FiltersFragment
     * @param profilesFragment Pre-created ProfilesFragment
     */
    public MapTabsAdapter(@NonNull FragmentActivity fragmentActivity,
                          RoutesFragment routesFragment,
                          FiltersFragment filtersFragment,
                          ProfilesFragment profilesFragment) {
        super(fragmentActivity);
        this.routesFragment = routesFragment;
        this.filtersFragment = filtersFragment;
        this.profilesFragment = profilesFragment;
    }

    /**
     * Creates or returns the Fragment for a specific position.
     *
     * @param position The tab position (0 for Routes, 1 for Filters, 2 for Profiles)
     * @return The Fragment to display for the given position
     */
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return routesFragment;
            case 1:
                return filtersFragment;
            case 2:
            default:
                return profilesFragment;
        }
    }

    /**
     * Returns the total number of tabs in the ViewPager. This method was created to allow flexibility.
     *
     * @return Always 3 (Routes, Filters, and Profiles tabs)
     */
    @Override
    public int getItemCount() {
        return 3;
    }
}