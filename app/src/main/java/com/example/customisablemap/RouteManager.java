package com.example.customisablemap;

import android.content.Context;
import android.graphics.Color;
import android.os.Looper;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Dot;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Manager class for finding and displaying transportation routes on Google Maps.
 */
public class RouteManager {
    private static final String TAG = "RouteManager";
    private final Context context;
    private final GoogleMap map;
    private final OkHttpClient client;
    private final int[] routeColors = {
            android.graphics.Color.parseColor("#4285F4"), // Blue
            android.graphics.Color.parseColor("#EA4335"), // Red
            android.graphics.Color.parseColor("#FBBC05"), // Yellow
            android.graphics.Color.parseColor("#34A853"), // Green
            android.graphics.Color.parseColor("#9C27B0")  // Purple
    };

    /**
     * Creates a new RouteManager.
     *
     * @param context Application context for API key and resources
     * @param map Google Map instance for route visualization
     */
    public RouteManager(Context context, GoogleMap map) {
        this.context = context;
        this.map = map;

        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Finds public transportation routes between two locations.
     *
     * @param origin Starting location coordinates
     * @param destination Destination location coordinates
     * @param preferredModes Set of preferred transportation modes
     * @param callback Callback to receive routing results
     */
    public void findRoutes(LatLng origin, LatLng destination,
                           Set<TransportMode> preferredModes,
                           RouteCallback callback) {
        try {
            // url with API key
            String url = buildRoutesApiUrl(origin, destination, preferredModes);

            PublicTransportRequest transportRequest = new PublicTransportRequest(
                    origin, destination, preferredModes,
                    preferredModes.contains(TransportMode.WALKING) ? 1000 : 500);

            JSONObject requestJson = transportRequest.buildRequestJson();

            // create MediaType for JSON
            MediaType JSON = MediaType.parse("application/json; charset=utf-8");

            // creating RequestBody
            RequestBody requestBody = RequestBody.create(JSON, requestJson.toString());

            String fieldMask = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
                    "routes.legs.steps.distanceMeters,routes.legs.steps.duration," +
                    "routes.legs.steps.polyline.encodedPolyline," +
                    "routes.legs.steps.travelMode," +
                    "routes.legs.steps.transitDetails," +
                    "routes.routeTravelMode";

            // simplified mask
            String fieldMaskAlt = "routes";

            // POST request
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Goog-FieldMask", fieldMaskAlt)  // Header
                    .build();

            Log.d(TAG, "Request URL: " + url);
            Log.d(TAG, "Request Body: " + requestJson.toString());
            Log.d(TAG, "Request Headers: X-Goog-FieldMask=" + fieldMaskAlt);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.e(TAG, "Route request failed", e);
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> callback.onRoutingFailure(e.getMessage()));
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    String responseBody = "";
                    try {
                        if (!response.isSuccessful()) {
                            String errorBody = response.body() != null ? response.body().string() : "No error details";
                            Log.e(TAG, "API Error: " + response.code() + " - " + errorBody);
                            Handler mainHandler = new Handler(Looper.getMainLooper());
                            mainHandler.post(() -> callback.onRoutingFailure("API Error: " + response.code()));
                            response.close(); // Ensure response is closed
                            return;
                        }

                        // parse routes from response
                        responseBody = response.body().string();
                        Log.d(TAG, "Response: " + responseBody);
                        List<Route> routes = parseRoutesResponse(responseBody);

                        // return on main thread
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> callback.onRoutingSuccess(routes));
                    } catch (Exception e) {
                        Log.e(TAG, "Error parsing response: " + e.getMessage(), e);
                        Log.e(TAG, "Response body: " + responseBody);
                        Handler mainHandler = new Handler(Looper.getMainLooper());
                        mainHandler.post(() -> callback.onRoutingFailure("Error parsing response: " + e.getMessage()));
                    } finally {
                        // response has to be closed anyway
                        response.close();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in findRoutes", e);
            callback.onRoutingFailure("Error preparing request: " + e.getMessage());
        }
    }

    /**
     * Builds the URL for the Google Directions API request.
     *
     * @param origin Starting location coordinates
     * @param destination Destination location coordinates
     * @param modes Set of preferred transportation modes
     * @return The complete URL for the API request
     */
    private String buildRoutesApiUrl(LatLng origin, LatLng destination,
                                     Set<TransportMode> modes) {
        String baseUrl = "https://routes.googleapis.com/directions/v2:computeRoutes";

        // API key as a query parameter
        String apiKey = context.getString(R.string.google_maps_key);
        return baseUrl + "?key=" + apiKey;
    }

    /**
     * Parses the JSON response from the Google Directions API.
     *
     * @param json The JSON string response from the API
     * @return List of Route objects
     */
    private List<Route> parseRoutesResponse(String json) {
        List<Route> routes = new ArrayList<>();

        try {
            // JSON object created from the response string
            JSONObject jsonResponse = new JSONObject(json);

            // checking if routes array exists
            if (!jsonResponse.has("routes")) {
                Log.e(TAG, "No routes found in response");
                return routes;
            }

            // getting the routes array
            JSONArray routesArray = jsonResponse.getJSONArray("routes");

            // looping through each route
            for (int i = 0; i < routesArray.length(); i++) {
                JSONObject routeJson = routesArray.getJSONObject(i);

                // polyline extraction
                List<LatLng> points = new ArrayList<>();
                if (routeJson.has("polyline") && routeJson.getJSONObject("polyline").has("encodedPolyline")) {
                    String encodedPolyline = routeJson.getJSONObject("polyline").getString("encodedPolyline");
                    points = decodePolyline(encodedPolyline);
                }

                // extracting duration
                int durationSeconds = 0;
                if (routeJson.has("duration")) {
                    try {
                        durationSeconds = routeJson.getJSONObject("duration").getInt("seconds");
                    } catch (JSONException e) {
                        String durationStr = routeJson.getString("duration");
                        if (durationStr.endsWith("s")) {
                            try {
                                durationSeconds = Integer.parseInt(durationStr.substring(0, durationStr.length() - 1));
                            } catch (NumberFormatException nfe) {
                                Log.e(TAG, "Error parsing duration string: " + durationStr, nfe);
                            }
                        }
                    }
                }

                // extract distance
                int distanceMeters = 0;
                if (routeJson.has("distanceMeters")) {
                    distanceMeters = routeJson.getInt("distanceMeters");
                }

                // extract steps
                List<TransportStep> steps = extractSteps(routeJson);

                // extract departure and arrival times
                String departureTime = "";
                String arrivalTime = "";

                // first attempt through route level
                if (routeJson.has("localizedValues")) {
                    JSONObject localizedValues = routeJson.getJSONObject("localizedValues");

                    if (localizedValues.has("departureTime")) {
                        JSONObject depTime = localizedValues.getJSONObject("departureTime");
                        if (depTime.has("time") && depTime.getJSONObject("time").has("text")) {
                            departureTime = depTime.getJSONObject("time").getString("text");
                        }
                    }

                    if (localizedValues.has("arrivalTime")) {
                        JSONObject arrTime = localizedValues.getJSONObject("arrivalTime");
                        if (arrTime.has("time") && arrTime.getJSONObject("time").has("text")) {
                            arrivalTime = arrTime.getJSONObject("time").getString("text");
                        }
                    }
                }

                // second attempt through first and last legs/steps
                if (departureTime.isEmpty() && steps.size() > 0 && routeJson.has("legs")) {
                    JSONArray legs = routeJson.getJSONArray("legs");
                    if (legs.length() > 0) {
                        JSONObject firstLeg = legs.getJSONObject(0);

                        // first leg's steps
                        if (firstLeg.has("steps") && firstLeg.getJSONArray("steps").length() > 0) {
                            JSONObject firstStep = firstLeg.getJSONArray("steps").getJSONObject(0);
                            if (firstStep.has("transitDetails")) {
                                JSONObject transitDetails = firstStep.getJSONObject("transitDetails");

                                if (transitDetails.has("localizedValues") &&
                                        transitDetails.getJSONObject("localizedValues").has("departureTime")) {
                                    JSONObject depTime = transitDetails.getJSONObject("localizedValues")
                                            .getJSONObject("departureTime");
                                    if (depTime.has("time") && depTime.getJSONObject("time").has("text")) {
                                        departureTime = depTime.getJSONObject("time").getString("text");
                                    }
                                }
                            }
                        }

                        // last leg's last step
                        JSONObject lastLeg = legs.getJSONObject(legs.length() - 1);
                        if (lastLeg.has("steps") && lastLeg.getJSONArray("steps").length() > 0) {
                            JSONArray lastLegSteps = lastLeg.getJSONArray("steps");
                            JSONObject lastStep = lastLegSteps.getJSONObject(lastLegSteps.length() - 1);

                            if (lastStep.has("transitDetails")) {
                                JSONObject transitDetails = lastStep.getJSONObject("transitDetails");

                                if (transitDetails.has("localizedValues") &&
                                        transitDetails.getJSONObject("localizedValues").has("arrivalTime")) {
                                    JSONObject arrTime = transitDetails.getJSONObject("localizedValues")
                                            .getJSONObject("arrivalTime");
                                    if (arrTime.has("time") && arrTime.getJSONObject("time").has("text")) {
                                        arrivalTime = arrTime.getJSONObject("time").getString("text");
                                    }
                                }
                            }
                        }
                    }
                }

                // add the route with time information
                Route route = new Route(points, durationSeconds, distanceMeters, steps, departureTime, arrivalTime);
                routes.add(route);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON response", e);
        }

        return routes;
    }

    /**
     * Decodes an encoded polyline string into a list of LatLng points.
     *
     * @param encoded The encoded polyline string
     * @return List of LatLng coordinates
     */
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latD = lat / 1E5;
            double lngD = lng / 1E5;
            poly.add(new LatLng(latD, lngD));
        }

        return poly;
    }

    /**
     * Draws a route on the map with a specific color based on the route index.
     *
     * @param route The route to draw
     * @param routeIndex The index of the route (used to determine color)
     */
    public void drawRoute(Route route, int routeIndex) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            // clear all previous routes and markers
            map.clear();

            // adding markers again (if they exist)
            if (((MapActivity)context).getCurrentLocationMarker() != null) {
                ((MapActivity)context).recreateCurrentLocationMarker();
            }

            if (((MapActivity)context).getDestinationMarker() != null) {
                ((MapActivity)context).recreateDestinationMarker();
            }

            // draw each step with appropriate style
            if (route.getSteps() != null && !route.getSteps().isEmpty()) {
                drawRouteSteps(route, routeIndex);
            } else {
                // in case of any error, draw the whole route with one style
                drawSinglePolyline(route, routeIndex);
            }
        });
    }

    /**
     * Draws a route's individual steps with appropriate styling for each transportation mode.
     *
     * @param route The route containing steps to draw
     * @param routeIndex The index of the route
     */
    private void drawRouteSteps(Route route, int routeIndex) {
        List<TransportStep> steps = route.getSteps();

        for (int i = 0; i < steps.size(); i++) {
            TransportStep step = steps.get(i);
            List<LatLng> points = step.getPoints();

            if (points == null || points.isEmpty()) {
                continue;
            }

            // defining startPoint
            LatLng startPoint = points.get(0);

            PolylineOptions polylineOptions = new PolylineOptions();

            // color and style based on transport mode
            switch (step.getMode()) {
                case WALKING:
                    polylineOptions.color(Color.GRAY);
                    polylineOptions.width(6);
                    // Create a dotted line for walking
                    polylineOptions.pattern(Arrays.asList(
                            new Dot(), new Gap(10f)));
                    break;
                case BUS:
                    polylineOptions.color(routeColors[0]);
                    polylineOptions.width(10);
                    break;
                case BIKE:
                    polylineOptions.color(routeColors[1]);
                    polylineOptions.width(10);
                    break;
                case TRAIN:
                    polylineOptions.color(routeColors[2]);
                    polylineOptions.width(10);
                    break;
                default:
                    polylineOptions.color(routeColors[routeIndex % routeColors.length]);
                    polylineOptions.width(8);
                    break;
            }

            // station marker for non walking steps
            if (step.getMode() != TransportMode.WALKING) {
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(startPoint)
                        .title(getStationTitle(step))
                        .alpha(0.9f)
                        .zIndex(1.0f);

                // different colors based on mode
                float hue;
                switch (step.getMode()) {
                    case BUS:
                        hue = BitmapDescriptorFactory.HUE_AZURE;
                        break;
                    case BIKE:
                        hue = BitmapDescriptorFactory.HUE_VIOLET;
                        break;
                    case TRAIN:
                        hue = BitmapDescriptorFactory.HUE_ORANGE;
                        break;
                    default:
                        hue = BitmapDescriptorFactory.HUE_CYAN;
                        break;
                }

                markerOptions.icon(BitmapDescriptorFactory.defaultMarker(hue));
                map.addMarker(markerOptions);
            }

            // adding points to polyline
            for (LatLng point : points) {
                polylineOptions.add(point);
            }

            // drawing the polyline
            map.addPolyline(polylineOptions);

            // check if this is a public transport step
            if (step.getMode() != TransportMode.WALKING) {
                // add end station marker for last point in transport segment
                if (i == steps.size() - 1 || steps.get(i + 1).getMode() != step.getMode()) {
                    LatLng endPoint = points.get(points.size() - 1);

                    MarkerOptions endMarkerOptions = new MarkerOptions()
                            .position(endPoint)
                            .title("Get off " + getModeString(step.getMode()) +
                                    (step.getLineName() != null && !step.getLineName().isEmpty() ?
                                            " " + step.getLineName() : ""));

                    BitmapDescriptor icon = getBitmapDescriptorForMode(step.getMode());
                    if (icon != null) {
                        endMarkerOptions.icon(icon);
                    }

                    map.addMarker(endMarkerOptions);
                }
            }

        }
    }

    /**
     * Creates a station title based on a transport step.
     *
     * @param step The transport step to create a title for
     * @return A descriptive title for the station marker
     */
    private String getStationTitle(TransportStep step) {
        StringBuilder title = new StringBuilder();

        title.append("Take ");
        title.append(getModeString(step.getMode()));

        if (step.getLineName() != null && !step.getLineName().isEmpty()) {
            title.append(" ").append(step.getLineName());
        }

        return title.toString();
    }

    /**
     * Converts a TransportMode enum to a user friendly string.
     *
     * @param mode The transport mode to convert
     * @return A human readable string for the transport mode
     */
    private String getModeString(TransportMode mode) {
        switch (mode) {
            case BUS:
                return "Bus";
            case BIKE:
                return "Bike";
            case TRAIN:
                return "Train";
            case WALKING:
                return "Walk";
            default:
                return mode.name();
        }
    }

    /**
     * Gets the appropriate bitmap descriptor for a transport mode marker.
     *
     * @param mode The transport mode to get a marker icon for
     * @return A BitmapDescriptor for the specified transport mode
     */
    private BitmapDescriptor getBitmapDescriptorForMode(TransportMode mode) {
        float hue;
        float alpha = 0.9f;

        switch (mode) {
            case BUS:
                hue = BitmapDescriptorFactory.HUE_AZURE;
                break;
            case BIKE:
                hue = BitmapDescriptorFactory.HUE_VIOLET;
                break;
            case TRAIN:
                hue = BitmapDescriptorFactory.HUE_ORANGE;
                break;
            default:
                hue = BitmapDescriptorFactory.HUE_CYAN;
                break;
        }

        return BitmapDescriptorFactory.defaultMarker(hue);
    }

    /**
     * Draws a route as a single polyline.
     *
     * @param route The route to draw
     * @param routeIndex The index of the route (used to determine color)
     */
    private void drawSinglePolyline(Route route, int routeIndex) {
        PolylineOptions polylineOptions = new PolylineOptions();
        polylineOptions.color(routeColors[routeIndex % routeColors.length]);
        polylineOptions.width(10);

        for (LatLng point : route.getPoints()) {
            polylineOptions.add(point);
        }

        map.addPolyline(polylineOptions);
    }

    /**
     * Interface for routing callbacks.
     */
    public interface RouteCallback {
        /**
         * Called when routes are successfully retrieved.
         *
         * @param routes List of Route objects
         */
        void onRoutingSuccess(List<Route> routes);

        /**
         * Called when route finding fails.
         *
         * @param errorMessage Description of what went wrong
         */
        void onRoutingFailure(String errorMessage);
    }

    /**
     * Extracts individual transportation steps from a route JSON object.
     *
     * @param routeJson The JSON object containing route information
     * @return List of TransportStep objects
     * @throws JSONException If the JSON structure is invalid
     */
    private List<TransportStep> extractSteps(JSONObject routeJson) throws JSONException {
        List<TransportStep> steps = new ArrayList<>();

        if (!routeJson.has("legs")) {
            return steps;
        }

        JSONArray legsArray = routeJson.getJSONArray("legs");
        for (int j = 0; j < legsArray.length(); j++) {
            JSONObject legJson = legsArray.getJSONObject(j);
            if (!legJson.has("steps")) {
                continue;
            }

            JSONArray stepsArray = legJson.getJSONArray("steps");
            for (int k = 0; k < stepsArray.length(); k++) {
                JSONObject stepJson = stepsArray.getJSONObject(k);

                // travel mode
                String travelModeStr = stepJson.optString("travelMode", "WALKING");
                TransportMode mode = parseTransportMode(travelModeStr, stepJson);

                // step details
                String instruction = stepJson.optString("htmlInstructions", "");

                int stepDurationSeconds = 0;
                if (stepJson.has("duration")) {
                    if (stepJson.get("duration") instanceof JSONObject) {
                        stepDurationSeconds = stepJson.getJSONObject("duration").optInt("seconds", 0);
                    } else {
                        String durationStr = stepJson.optString("duration", "0s");
                        if (durationStr.endsWith("s")) {
                            durationStr = durationStr.substring(0, durationStr.length() - 1);
                            try {
                                stepDurationSeconds = Integer.parseInt(durationStr);
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Error parsing step duration: " + durationStr, e);
                            }
                        }
                    }
                }

                int stepDistanceMeters = stepJson.optInt("distanceMeters", 0);

                // polyline
                List<LatLng> stepPoints = new ArrayList<>();
                if (stepJson.has("polyline") && stepJson.getJSONObject("polyline").has("encodedPolyline")) {
                    String encodedPolyline = stepJson.getJSONObject("polyline").getString("encodedPolyline");
                    stepPoints = decodePolyline(encodedPolyline);
                }

                // transit details
                String lineName = "";
                if (stepJson.has("transitDetails")) {
                    JSONObject transitDetails = stepJson.getJSONObject("transitDetails");

                    Log.d(TAG, "Transit details found: " + transitDetails.toString());

                    // checking for transitLine
                    if (transitDetails.has("transitLine")) {
                        JSONObject transitLine = transitDetails.getJSONObject("transitLine");

                        // attempting to get the line name or number
                        if (transitLine.has("nameShort")) {
                            lineName = transitLine.getString("nameShort");
                            Log.d(TAG, "nameShort field found: " + lineName);
                        } else if (transitLine.has("name")) {
                            lineName = transitLine.getString("name");
                            Log.d(TAG, "name field found: " + lineName);
                        }

                        // vehicle type
                        if (transitLine.has("vehicle") && transitLine.getJSONObject("vehicle").has("type")) {
                            String vehicleType = transitLine.getJSONObject("vehicle").getString("type");

                            if (vehicleType.equals("BUS")) {
                                Log.d(TAG, "Vehicle BUS");
                                mode = TransportMode.BUS;
                            } else if (vehicleType.equals("BIKE")) {
                                Log.d(TAG, "Vehicle BIKE");
                                mode = TransportMode.BIKE;
                            } else if (vehicleType.equals("TRAIN")) {
                                Log.d(TAG, "Vehicle TRAIN");
                                mode = TransportMode.TRAIN;
                            } else if (vehicleType.equals("TRAM")) {
                                Log.d(TAG, "Vehicle TRAM");
                                mode = TransportMode.TRAIN; // Using TRAIN for TRAM
                            }
                        }

                    }
                    // if transitLine isn't found
                    else if (transitDetails.has("line")) {
                        JSONObject line = transitDetails.getJSONObject("line");

                        // possible fields for the line name
                        if (line.has("shortName")) {
                            lineName = line.getString("shortName");
                            Log.d(TAG, "field found: " + lineName);
                        } else if (line.has("name")) {
                            lineName = line.getString("name");
                            Log.d(TAG, "field found: " + lineName);
                        }

                        Log.d(TAG, "Found line: " + lineName);

                        //  vehicle information
                        if (line.has("vehicle") && line.getJSONObject("vehicle").has("type")) {
                            String vehicleType = line.getJSONObject("vehicle").getString("type");

                            if (vehicleType.equals("BUS")) {
                                Log.d(TAG, "Vehicle BUS");
                                mode = TransportMode.BUS;
                            } else if (vehicleType.equals("BIKE")) {
                                Log.d(TAG, "Vehicle BIKE");
                                mode = TransportMode.BIKE;
                            } else if (vehicleType.equals("TRAIN")) {
                                Log.d(TAG, "Vehicle TRAIN");
                                mode = TransportMode.TRAIN;
                            } else if (vehicleType.equals("TRAM")) {
                                Log.d(TAG, "Vehicle TRAM");
                                mode = TransportMode.TRAIN;
                            }
                        }
                    }

                    // headsign information (direction) if available
                    if (transitDetails.has("headsign") && !lineName.isEmpty()) {
                        String headsign = transitDetails.getString("headsign");
                        Log.d(TAG, "Headsign: " + headsign);
                    }
                }

                TransportStep step = new TransportStep(mode, instruction, stepDurationSeconds,
                        stepDistanceMeters, stepPoints, lineName);
                steps.add(step);
            }
        }

        return steps;
    }

    /**
     * Parses a travel mode string to the corresponding TransportMode enum.
     *
     * @param travelModeStr The travel mode string from the API
     * @param stepJson The JSON object containing step details
     * @return The corresponding TransportMode enum value
     */
    private TransportMode parseTransportMode(String travelModeStr, JSONObject stepJson) {
        switch (travelModeStr) {
            case "WALKING":
                return TransportMode.WALKING;
            case "BICYCLING":
                return TransportMode.BIKE;
            case "TRANSIT":
                if (stepJson.has("transitDetails")) {
                    try {
                        JSONObject transitDetails = stepJson.getJSONObject("transitDetails");

                        // check for the new transitLine
                        if (transitDetails.has("transitLine") &&
                                transitDetails.getJSONObject("transitLine").has("vehicle") &&
                                transitDetails.getJSONObject("transitLine").getJSONObject("vehicle").has("type")) {

                            String vehicleType = transitDetails.getJSONObject("transitLine")
                                    .getJSONObject("vehicle").getString("type");

                            Log.d(TAG, "Found vehicle type (transitLine): " + vehicleType);

                            switch (vehicleType) {
                                case "BUS":
                                    return TransportMode.BUS;
                                case "SUBWAY":
                                    return TransportMode.TRAIN; // using TRAIN for SUBWAY
                                case "TRAIN":
                                    return TransportMode.TRAIN;
                                case "TRAM":
                                    return TransportMode.TRAIN; // Using TRAIN for TRAM
                                case "BICYCLE":
                                    return TransportMode.BIKE;
                            }
                        }
                        // check for the new line
                        else if (transitDetails.has("line") &&
                                transitDetails.getJSONObject("line").has("vehicle") &&
                                transitDetails.getJSONObject("line").getJSONObject("vehicle").has("type")) {

                            String vehicleType = transitDetails.getJSONObject("line")
                                    .getJSONObject("vehicle").getString("type");

                            Log.d(TAG, "Found vehicle type (line): " + vehicleType);

                            switch (vehicleType) {
                                case "BUS":
                                    return TransportMode.BUS;
                                case "SUBWAY":
                                    return TransportMode.TRAIN; // using TRAIN for SUBWAY
                                case "TRAIN":
                                    return TransportMode.TRAIN;
                                case "TRAM":
                                    return TransportMode.TRAIN; // Using TRAIN for TRAM
                                case "BICYCLE":
                                    return TransportMode.BIKE;
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing transit details", e);
                    }
                }
                // default to BUS if can't determine specific type
                return TransportMode.BUS;
            default:
                return TransportMode.WALKING;
        }
    }
}