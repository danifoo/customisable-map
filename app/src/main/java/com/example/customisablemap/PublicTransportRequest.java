package com.example.customisablemap;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

/**
 * Creates and configures requests for the Google Directions API to find public transportation routes.
 */
public class PublicTransportRequest {
    private final LatLng origin;
    private final LatLng destination;
    private final Set<TransportMode> preferredModes;
    private final int maxWalkingDistance;

    /**
     * Creates a new public transportation request with specified parameters.
     *
     * @param origin The starting location for the route
     * @param destination The destination location for the route
     * @param preferredModes Set of transportation modes to include (BUS, BIKE, TRAIN, WALKING)
     * @param maxWalkingDistance Maximum walking distance in meters
     */
    public PublicTransportRequest(LatLng origin, LatLng destination,
                                  Set<TransportMode> preferredModes,
                                  int maxWalkingDistance) {
        this.origin = origin;
        this.destination = destination;
        this.preferredModes = preferredModes;
        this.maxWalkingDistance = maxWalkingDistance;
    }

    /**
     * Builds a JSON object representing the route request for the Google Directions API.
     *
     * @return A JSONObject containing the properly formatted request parameters
     */
    public JSONObject buildRequestJson() {
        JSONObject request = new JSONObject();
        try {
            // origin coordinate
            JSONObject originLocationObject = new JSONObject();
            JSONObject originLatLngObject = new JSONObject();
            originLatLngObject.put("latitude", origin.latitude);
            originLatLngObject.put("longitude", origin.longitude);
            originLocationObject.put("latLng", originLatLngObject);
            request.put("origin", new JSONObject().put("location", originLocationObject));

            // destination coordinate
            JSONObject destLocationObject = new JSONObject();
            JSONObject destLatLngObject = new JSONObject();
            destLatLngObject.put("latitude", destination.latitude);
            destLatLngObject.put("longitude", destination.longitude);
            destLocationObject.put("latLng", destLatLngObject);
            request.put("destination", new JSONObject().put("location", destLocationObject));

            request.put("travelMode", "TRANSIT");

            // alternative routes
            request.put("computeAlternativeRoutes", true);

            // getting a timestamp for current time
            java.text.SimpleDateFormat iso8601Format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            iso8601Format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            String currentTimeIso = iso8601Format.format(new java.util.Date());

            // departureTime
            request.put("departureTime", currentTimeIso);

            Log.d("PublicTransportRequest", "Request JSON: " + request.toString());
        } catch (JSONException e) {
            Log.e("PublicTransportRequest", "Error building JSON", e);
        }

        return request;
    }

}