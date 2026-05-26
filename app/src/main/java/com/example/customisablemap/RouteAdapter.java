package com.example.customisablemap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying transportation routes in a RecyclerView.
 */
public class RouteAdapter extends RecyclerView.Adapter<RouteAdapter.RouteViewHolder> {
    private final List<Route> routes;
    private final OnRouteClickListener listener;

    /**
     * Creates a new RouteAdapter.
     *
     * @param routes List of Route objects to display
     * @param listener Callback for when a route is clicked
     */
    public RouteAdapter(List<Route> routes, OnRouteClickListener listener) {
        this.routes = routes;
        this.listener = listener;
    }

    /**
     * Creates a new ViewHolder by inflating the route item layout.
     *
     * @param parent The parent ViewGroup
     * @param viewType The type of view to create
     * @return A new RouteViewHolder instance
     */
    @NonNull
    @Override
    public RouteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_route, parent, false);
        return new RouteViewHolder(view);
    }

    /**
     * Binds route data to the ViewHolder at the specified position.
     *
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the dataset
     */
    @Override
    public void onBindViewHolder(@NonNull RouteViewHolder holder, int position) {
        Route route = routes.get(position);
        holder.bind(route, listener, position);
    }

    /**
     * Returns the total number of routes in the dataset.
     *
     * @return The number of routes
     */
    @Override
    public int getItemCount() {
        return routes.size();
    }

    /**
     * Interface for route click event callbacks.
     */
    public interface OnRouteClickListener {
        /**
         * Called when a route item is clicked.
         *
         * @param route The selected Route object
         * @param position The position of the route in the list
         */
        void onRouteClick(Route route, int position);
    }

    /**
     * ViewHolder class for route items.
     */
    static class RouteViewHolder extends RecyclerView.ViewHolder {
        private final TextView routeTitle;
        private final TextView routeDuration;
        private final TextView routeDistance;
        private final TextView routeTransportModes;
        private final TextView routeTimes;

        /**
         * Creates a new RouteViewHolder.
         *
         * @param itemView The route item view
         */
        public RouteViewHolder(@NonNull View itemView) {
            super(itemView);
            routeTitle = itemView.findViewById(R.id.route_title);
            routeDuration = itemView.findViewById(R.id.route_duration);
            routeDistance = itemView.findViewById(R.id.route_distance);
            routeTransportModes = itemView.findViewById(R.id.route_transport_modes);
            routeTimes = itemView.findViewById(R.id.route_times);
        }

        /**
         * Binds route data to the ViewHolder and sets up click listener.
         *
         * @param route The route to display
         * @param listener Callback for route click
         * @param position The position of the route in the list
         */
        public void bind(Route route, OnRouteClickListener listener, int position) {
            // setting route title without colored circle
            String title = "Route " + (position + 1);
            routeTitle.setText(title);

            // rest of the data binding
            routeDuration.setText(route.getFormattedDuration());
            routeDistance.setText(route.getFormattedDistance());

            // transport modes
            if (route.getSteps() != null && !route.getSteps().isEmpty()) {
                routeTransportModes.setText(route.getTransportModesText());
                routeTransportModes.setVisibility(View.VISIBLE);
            } else {
                routeTransportModes.setText("Via: Unknown transport modes");
                routeTransportModes.setVisibility(View.VISIBLE);
            }

            // departure and arrival times (this will not be displayed in the app as the implementation has not been fully finished)
            if (route.getDepartureTime() != null && !route.getDepartureTime().isEmpty() &&
                    route.getArrivalTime() != null && !route.getArrivalTime().isEmpty()) {
                routeTimes.setText(route.getDepartureTime() + " → " + route.getArrivalTime());
                routeTimes.setVisibility(View.VISIBLE);
            } else {
                routeTimes.setVisibility(View.GONE);
            }

            // listener
            itemView.setOnClickListener(v -> listener.onRouteClick(route, position));
        }
    }
}