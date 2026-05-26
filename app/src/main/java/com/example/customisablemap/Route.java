package com.example.customisablemap;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Represents a complete transportation route with geographic points and metadata.
 */
public class Route {
    private final List<LatLng> points;
    private final int durationSeconds;
    private final int distanceMeters;
    private final List<TransportStep> steps;
    private String departureTime;
    private String arrivalTime;

    /**
     * Creates a new Route with the specified details.
     *
     * @param points The sequence of geographic points that form the route
     * @param durationSeconds The total duration of the route in seconds
     * @param distanceMeters The total distance of the route in meters
     * @param steps The detailed steps of the route
     * @param departureTime The scheduled departure time
     * @param arrivalTime The scheduled arrival time
     */
    public Route(List<LatLng> points, int durationSeconds, int distanceMeters,
                 List<TransportStep> steps, String departureTime, String arrivalTime) {
        this.points = points;
        this.durationSeconds = durationSeconds;
        this.distanceMeters = distanceMeters;
        this.steps = steps;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    /**
     * Gets the sequence of geographic points that form the route.
     *
     * @return List of LatLng coordinates forming the route path
     */
    public List<LatLng> getPoints() {
        return points;
    }

    /**
     * Gets the scheduled departure time.
     *
     * @return The departure time as a formatted string
     */
    public String getDepartureTime() {
        return departureTime;
    }

    /**
     * Gets the scheduled arrival time.
     *
     * @return The arrival time as a formatted string
     */
    public String getArrivalTime() {
        return arrivalTime;
    }

    /**
     * Gets the detailed steps that make up this route.
     *
     * @return List of TransportStep objects describing each segment of the journey
     */
    public List<TransportStep> getSteps() {
        return steps;
    }

    /**
     * Makes the route duration human readable.
     *
     * @return The formatted duration string
     */
    public String getFormattedDuration() {
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;

        if (hours > 0) {
            return hours + " hrs " + minutes + " mins";
        } else {
            return minutes + " mins";
        }
    }

    /**
     * Makes the route distance human readable.
     *
     * @return The formatted distance string
     */
    public String getFormattedDistance() {
        if (distanceMeters >= 1000) {
            return String.format("%.1f km", distanceMeters / 1000.0);
        } else {
            return distanceMeters + " m";
        }
    }

    /**
     * Creates a descriptive text of the transport modes used in this route.
     *
     * @return A formatted string describing the sequence of transport modes
     */
    public String getTransportModesText() {
        if (steps == null || steps.isEmpty()) {
            return "No transport info";
        }

        StringBuilder sb = new StringBuilder("Via: ");
        TransportMode currentMode = null;
        String currentLine = "";
        boolean first = true;

        for (TransportStep step : steps) {
            if (currentMode != step.getMode() ||
                    !currentLine.equals(step.getLineName())) {

                if (first) {
                    first = false;
                } else {
                    sb.append(" → ");
                }

                sb.append(formatTransportMode(step.getMode()));

                if (step.getLineName() != null && !step.getLineName().isEmpty()) {
                    sb.append(" (").append(step.getLineName()).append(")");
                }

                currentMode = step.getMode();
                currentLine = step.getLineName();
            }
        }

        return sb.toString();
    }

    /**
     * Formats a transport mode enum value as a user friendly string.
     *
     * @param mode The TransportMode to format
     * @return A user friendly string of the transport mode
     */
    private String formatTransportMode(TransportMode mode) {
        switch (mode) {
            case BUS:
                return "Bus";
            case BIKE:
                return "Bike";
            case TRAIN:
                return "Train";
            case WALKING:
                return "Walking";
            default:
                return mode.name();
        }
    }
}