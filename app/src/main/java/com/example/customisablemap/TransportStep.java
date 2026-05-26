package com.example.customisablemap;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Represents a single step in a transportation route.
 */
public class TransportStep {
    private final TransportMode mode;
    private final String instruction;
    private final int durationSeconds;
    private final int distanceMeters;
    private final List<LatLng> points;
    private final String lineName;

    /**
     * Creates a new TransportStep with the specified details.
     *
     * @param mode The mode of transportation for this step
     * @param instruction Textual instructions for this step
     * @param durationSeconds The duration of this step in seconds
     * @param distanceMeters The distance of this step in meters
     * @param points The sequence of geographic points forming this step's path
     * @param lineName The name or number of the transit line
     */
    public TransportStep(TransportMode mode, String instruction,
                         int durationSeconds, int distanceMeters,
                         List<LatLng> points, String lineName) {
        this.mode = mode;
        this.instruction = instruction;
        this.durationSeconds = durationSeconds;
        this.distanceMeters = distanceMeters;
        this.points = points;
        this.lineName = lineName;
    }

    /**
     * Gets the mode of transportation for this step.
     *
     * @return The TransportMode enum value (BUS, BIKE, TRAIN, or WALKING)
     */
    public TransportMode getMode() {
        return mode;
    }

    /**
     * Gets the sequence of geographic points that form this step's path.
     *
     * @return List of LatLng coordinates forming the step's path
     */
    public List<LatLng> getPoints() {
        return points;
    }

    /**
     * Gets the name or number of the transit line for this step.
     *
     * @return The transit line name or number
     */
    public String getLineName() {
        return lineName;
    }
}