package com.example.customisablemap;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that displays a list of available transportation routes.
 */
public class RoutesFragment extends Fragment {
    private RecyclerView routesList;
    private RouteAdapter routeAdapter;
    private List<Route> routes = new ArrayList<>();
    private RouteAdapter.OnRouteClickListener listener;

    /**
     * Creates a new RoutesFragment with the specified route click listener.
     *
     * @param listener Callback for when a route is clicked
     */
    public RoutesFragment(RouteAdapter.OnRouteClickListener listener) {
        this.listener = listener;
    }

    /**
     * Called to have the fragment instantiate its user interface view.
     *
     * @param inflater The LayoutInflater object that can be used to inflate views
     * @param container This is the parent view that the fragment's UI should be attached to
     * @param savedInstanceState This fragment is being reconstructed from a previous saved state
     * @return The View for the fragment's UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_routes, container, false);

        // setup RecyclerView
        routesList = view.findViewById(R.id.routes_list);
        routesList.setLayoutManager(new LinearLayoutManager(requireContext()));

        routeAdapter = new RouteAdapter(routes, listener);
        routesList.setAdapter(routeAdapter);

        return view;
    }

    /**
     * Updates the list of routes displayed in the fragment.
     *
     * @param newRoutes The new list of routes to display
     */
    public void updateRoutes(List<Route> newRoutes) {
        routes.clear();
        routes.addAll(newRoutes);
        routeAdapter.notifyDataSetChanged();
    }
}