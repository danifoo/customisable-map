package com.example.customisablemap;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Toast;
import android.Manifest;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Main map activity that provides navigation functionality.
 * <p>
 * This activity displays a Google Map with origin and destination inputs,
 * allowing users to find and visualize public transportation routes. It provides
 * options to filter transportation modes and manage user profiles with transportation
 * preferences.
 * </p>
 */
public class MapActivity extends AppCompatActivity implements
        OnMapReadyCallback, RouteManager.RouteCallback, RouteAdapter.OnRouteClickListener {
    private static final String TAG = "MapActivity";
    private GoogleMap mMap;
    private RouteManager routeManager;
    private AutoCompleteTextView originInput;
    private AutoCompleteTextView destinationInput;
    private Button findRouteButton;
    private RouteAdapter routeAdapter;
    private List<Route> availableRoutes = new ArrayList<>();
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private RoutesFragment routesFragment;
    private FiltersFragment filtersFragment;
    private LatLng originLatLng;
    private LatLng destinationLatLng;
    private FusedLocationProviderClient fusedLocationClient;
    private Marker currentLocationMarker;
    private Marker destinationMarker;
    private FloatingActionButton currentLocationButton;
    private Geocoder geocoder;
    private ProfileSQLiteManager profileManager;
    private UserProfile currentProfile;
    private FirebaseAuth mAuth;
    private String userId;
    private ProfilesFragment profilesFragment;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private ImageButton toggleSearchButton;
    private ImageButton toggleBottomPanelButton;
    private CardView searchPanel;
    private ConstraintLayout bottomPanel;
    private boolean isSearchPanelExpanded = true;
    private boolean isBottomPanelExpanded = true;
    private int bottomPanelHeight = 350;

    /**
     * Initializes the activity, sets up the map, and configures UI components.
     *
     * @param savedInstanceState For when the activity is being re-initialized after previously
     *                           being shut down.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        setContentView(R.layout.activity_map);

        // initialize location provider client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // initialize Geocoder
        geocoder = new Geocoder(this, Locale.getDefault());

        // setting up a  map fragment
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // getting the current user's id
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            userId = user.getUid();
            profileManager = new ProfileSQLiteManager(this, userId);
        } else {
            // no user logged in, back to login menu
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        initializeUI();

        // listener for find route button
        findRouteButton.setOnClickListener(v -> {
            // hide keyboard
            hideKeyboard();

            // find routes
            findRoutes();

            // hide the search panel
            if (isSearchPanelExpanded) {
                toggleSearchPanel();
            }

            // make sure the bottom panel is expanded
            if (!isBottomPanelExpanded) {
                toggleBottomPanel();
            }
        });

        // listener for current location button
        currentLocationButton.setOnClickListener(v -> getCurrentLocation());

        // setting up search bar autocomplete
        setupPlacesAutocomplete();

        loadDefaultProfile();
    }

    /**
     * Hides the system keyboard.
     */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Initializes all UI components and sets up listeners.
     */
    private void initializeUI() {
        originInput = findViewById(R.id.origin_input);
        destinationInput = findViewById(R.id.destination_input);
        findRouteButton = findViewById(R.id.find_route_button);
        currentLocationButton = findViewById(R.id.current_location_button);

        // ViewPager and TabLayout
        viewPager = findViewById(R.id.view_pager);
        tabLayout = findViewById(R.id.tabs);

        // tab fragments
        routesFragment = new RoutesFragment(this);
        filtersFragment = new FiltersFragment();
        profilesFragment = new ProfilesFragment();

        profilesFragment.refreshProfiles();

        // collapsible panels
        searchPanel = findViewById(R.id.search_panel);
        bottomPanel = findViewById(R.id.bottom_panel);
        toggleSearchButton = findViewById(R.id.toggle_search_button);
        toggleBottomPanelButton = findViewById(R.id.toggle_bottom_panel_button);

        // toggle buttons listeners
        toggleSearchButton.setOnClickListener(v -> toggleSearchPanel());
        toggleBottomPanelButton.setOnClickListener(v -> toggleBottomPanel());

        // get original height of bottom panel after layout is complete
        bottomPanel.post(() -> {
            bottomPanelHeight = bottomPanel.getHeight();
        });

        // tabs adapter with all three fragments
        MapTabsAdapter tabsAdapter = new MapTabsAdapter(this,
                routesFragment, filtersFragment, profilesFragment);
        viewPager.setAdapter(tabsAdapter);

        // connect TabLayout with ViewPager
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Routes");
                    break;
                case 1:
                    tab.setText("Filters");
                    break;
                case 2:
                    tab.setText("Profiles");
                    break;
            }
        }).attach();

        // route adapter initialized
        availableRoutes = new ArrayList<>();
        routeAdapter = new RouteAdapter(availableRoutes, this);
    }

    /**
     * Callback method called when the map is ready to be used.
     * Configures map settings and initializes route manager.
     *
     * @param googleMap The GoogleMap instance
     */
    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // map style
        try {
            boolean success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style));
            if (!success) {
                Toast.makeText(this, "Style parsing failed", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Can't find style. Error: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

        // map UI settings
        mMap.getUiSettings().setZoomControlsEnabled(false);
        // using a custom my location button
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        // check for location permission and enable my location if granted
        checkLocationPermission();

        // map click listener
        mMap.setOnMapClickListener(latLng -> {
            setDestinationMarker(latLng);
        });

        // default position (if location permissions are not granted)
        LatLng defaultLocation = new LatLng(51.5074, -0.1278);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f));

        // Initialize RouteManager after map is ready
        routeManager = new RouteManager(this, mMap);

        checkLocationPermissionAndGetLocation();
    }

    /**
     * Checks for location permission and gets current location if permitted.
     */
    private void checkLocationPermissionAndGetLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            getCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Checks if location permission is granted and requests it if needed.
     */
    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * Enables the "My Location" on the map if permission is granted.
     */
    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (mMap != null) {
            mMap.setMyLocationEnabled(true);
        }
    }

    /**
     * Enables location features if permission is granted.
     *
     * @param requestCode The request code passed to requestPermissions
     * @param permissions The requested permissions
     * @param grantResults The grant results for the permissions
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
                // getting current location immediately after permission is granted
                getCurrentLocation();
            } else {
                Toast.makeText(this, "If location permission is denied. Some features may not work properly.",
                        Toast.LENGTH_LONG).show();

                // default location (London) if permission denied
                LatLng defaultLocation = new LatLng(51.5074, -0.1278);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f));
            }
        }
    }

    /**
     * Gets the device's current location and updates the map.
     */
    @SuppressLint("MissingPermission")
    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermission();
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        // current location
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());

                        // update the origin location
                        originLatLng = currentLatLng;

                        // current location marker
                        if (currentLocationMarker != null) {
                            currentLocationMarker.remove();
                        }

                        // adding a marker with a different icon for current location
                        currentLocationMarker = mMap.addMarker(new MarkerOptions()
                                .position(currentLatLng)
                                .title("My Location")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

                        // moving camera to the location
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f));

                        // getting the address for the location and update the origin input
                        getAddressFromLocation(currentLatLng, address -> {
                            originInput.setText(address);
                        });
                    } else {
                        Toast.makeText(MapActivity.this,
                                "Could not get current location. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting current location", e);
                    Toast.makeText(MapActivity.this,
                            "Error getting location: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Sets a marker at the specified location as the destination.
     *
     * @param latLng Finds out where the destination marker should be placed
     */
    private void setDestinationMarker(LatLng latLng) {
        // destination location
        destinationLatLng = latLng;

        // remove previous marker if exists
        if (destinationMarker != null) {
            destinationMarker.remove();
        }

        // adding new marker
        destinationMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Destination"));

        // get the address for the location and update the destination input
        getAddressFromLocation(latLng, address -> {
            destinationInput.setText(address);
        });
    }

    /**
     * Interface for address lookup callbacks.
     */
    interface AddressCallback {
        /**
         * Called when an address is found for the given coordinates.
         *
         * @param address The address string
         */
        void onAddressFound(String address);
    }

    /**
     * Gets a formatted address from coordinates using the Geocoder.
     *
     * @param latLng Coordinates to look up
     * @param callback Callback to return the address to
     */
    private void getAddressFromLocation(LatLng latLng, AddressCallback callback) {
        try {
            List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                // formatted address
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                    sb.append(address.getAddressLine(i));
                    if (i < address.getMaxAddressLineIndex()) {
                        sb.append(", ");
                    }
                }
                callback.onAddressFound(sb.toString());
            } else {
                // if can't find address, use coordinates
                callback.onAddressFound(String.format(Locale.getDefault(),
                        "%.6f, %.6f", latLng.latitude, latLng.longitude));
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address", e);
            // if error, use coordinates
            callback.onAddressFound(String.format(Locale.getDefault(),
                    "%.6f, %.6f", latLng.latitude, latLng.longitude));
        }
    }

    /**
     * Converts an address string to coordinates using the Geocoder.
     *
     * @param addressStr The address to geocode
     * @return LatLng coordinates
     */
    private LatLng geocodeAddress(String addressStr) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressStr, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                return new LatLng(address.getLatitude(), address.getLongitude());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Asynchronously geocodes an address string and returns the result using callback.
     *
     * @param addressStr The address to geocode
     * @param callback Callback to return the coordinates to
     */
    private void geocodeAddress(String addressStr, GeocodeCallback callback) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocationName(addressStr, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                callback.onLatLngFound(latLng);
            } else {
                callback.onLatLngFound(null);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error geocoding address", e);
            callback.onLatLngFound(null);
        }
    }

    /**
     * Interface for geocoding callbacks.
     */
    interface GeocodeCallback {
        /**
         * Called when coordinates are found for the given address.
         *
         * @param latLng The coordinates or null if geocoding failed
         */
        void onLatLngFound(LatLng latLng);
    }

    /**
     * Starts route finding between origin and destination.
     */
    private void findRoutes() {
        try {
            // check origin and destination coordinates
            if (originLatLng == null || destinationLatLng == null) {
                // geocoding as fallback
                String originStr = originInput.getText().toString();
                String destStr = destinationInput.getText().toString();

                // get coordinates from addresses using geocoding
                originLatLng = geocodeAddress(originStr);
                destinationLatLng = geocodeAddress(destStr);
            }

            if (originLatLng == null || destinationLatLng == null) {
                Toast.makeText(this, "Please select both origin and destination", Toast.LENGTH_SHORT).show();
                return;
            }

            // a set for transport modes
            Set<TransportMode> preferredModes = new HashSet<>();
            preferredModes.add(TransportMode.BUS);
            preferredModes.add(TransportMode.BIKE);
            preferredModes.add(TransportMode.TRAIN);
            preferredModes.add(TransportMode.WALKING);

            // try to use preferences if fragment is really ready and UI is initialized
            boolean preferencesReady = filtersFragment != null &&
                    filtersFragment.getView() != null &&
                    filtersFragment.isAdded() &&
                    filtersFragment.isUiInitialized();

            if (preferencesReady) {
                try {
                    // updating modes based on preferences
                    preferredModes.clear();
                    if (filtersFragment.isBusPreferred()) preferredModes.add(TransportMode.BUS);
                    if (filtersFragment.isSubwayPreferred()) preferredModes.add(TransportMode.BIKE);
                    if (filtersFragment.isTrainPreferred()) preferredModes.add(TransportMode.TRAIN);
                    // walking always included
                    preferredModes.add(TransportMode.WALKING);

                    // max walking distance extracted
                    int maxWalking = filtersFragment.getMaxWalkingDistance();
                    Log.d("MapActivity", "Max walking distance: " + maxWalking + " meters");
                } catch (Exception e) {
                    Log.e("MapActivity", "Error getting preferences from fragment", e);
                }
            } else {
                Log.d("MapActivity", "Using default preferences as fragment UI is not initialized");
            }

            Log.d("MapActivity", "Finding routes with modes: " + preferredModes);

            // request routes
            routeManager.findRoutes(originLatLng, destinationLatLng, preferredModes, this);

            // switch to Routes tab
            viewPager.setCurrentItem(0);

        } catch (Exception e) {
            // catching exceptions to prevent app crashes
            Log.e("MapActivity", "Error finding routes", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Callback when routes are successfully retrieved. Filters routes based on user preferences and updates the UI.
     *
     * @param routes List of available routes
     */
    @Override
    public void onRoutingSuccess(List<Route> routes) {
        // filter routes based on preferences
        List<Route> filteredRoutes = new ArrayList<>();
        Set<TransportMode> preferredModes = new HashSet<>();

        // retrieve preferred modes from current profile
        boolean preferencesReady = filtersFragment != null &&
                filtersFragment.getView() != null &&
                filtersFragment.isAdded() &&
                filtersFragment.isUiInitialized();

        if (preferencesReady) {
            if (filtersFragment.isBusPreferred()) preferredModes.add(TransportMode.BUS);
            if (filtersFragment.isSubwayPreferred()) preferredModes.add(TransportMode.BIKE);
            if (filtersFragment.isTrainPreferred()) preferredModes.add(TransportMode.TRAIN);
            // walking always included
            preferredModes.add(TransportMode.WALKING);
        } else {
            // default preferences in case the preferences couldn't be retrieved
            preferredModes.add(TransportMode.BUS);
            preferredModes.add(TransportMode.BIKE);
            preferredModes.add(TransportMode.TRAIN);
            preferredModes.add(TransportMode.WALKING);
        }

        // filtering the routes based on preferred modes
        for (Route route : routes) {
            boolean routeMatchesPreferences = true;
            if (route.getSteps() != null && !route.getSteps().isEmpty()) {
                for (TransportStep step : route.getSteps()) {
                    if (!preferredModes.contains(step.getMode())) {
                        routeMatchesPreferences = false;
                        break;
                    }
                }

                if (routeMatchesPreferences) {
                    filteredRoutes.add(route);
                }
            } else {
                filteredRoutes.add(route);
            }
        }

        // if no routes match preferences then show all routes
        if (filteredRoutes.isEmpty() && !routes.isEmpty()) {
            Log.d(TAG, "No routes match preferences, showing all routes");
            filteredRoutes = routes;
        }
        if (filteredRoutes.size() > 5) {
            Log.d(TAG, "Limiting routes to 5 from " + filteredRoutes.size());
            filteredRoutes = filteredRoutes.subList(0, 5);
        }

        // update UI with filtered routes
        availableRoutes.clear();
        availableRoutes.addAll(filteredRoutes);

        // notify the routes fragment adapter
        if (routesFragment != null) {
            routesFragment.updateRoutes(filteredRoutes);
        }

        // the first route gets drawn
        if (!filteredRoutes.isEmpty()) {
            routeManager.drawRoute(filteredRoutes.get(0), 0);
        }
    }

    /**
     * Callback when route finding fails.
     *
     * @param errorMessage The error message
     */
    @Override
    public void onRoutingFailure(String errorMessage) {
        Toast.makeText(this, "Routing error: " + errorMessage, Toast.LENGTH_LONG).show();
    }

    /**
     * Draws the selected route on the map.
     *
     * @param route The selected route
     * @param position The position of the route in the list
     */
    @Override
    public void onRouteClick(Route route, int position) {
        routeManager.drawRoute(route, position);
    }

    /**
     * Sets up Google Places autocomplete for origin and destination inputs.
     */
    private void setupPlacesAutocomplete() {
        // initialize Places
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), getString(R.string.google_maps_key));
        }

        // sets up origin autocomplete
        AutoCompleteTextView originInput = findViewById(R.id.origin_input);
        PlacesAutocompleteAdapter originAdapter = new PlacesAutocompleteAdapter(this);
        originInput.setAdapter(originAdapter);

        originInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) {
                    originAdapter.getPredictions(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        originInput.setOnItemClickListener((parent, view, position, id) -> {
            AutocompletePrediction prediction = originAdapter.getItem(position);
            getPlaceFromPrediction(prediction, place -> {
                originLatLng = place.getLatLng();
                Log.d("PlacesAPI", "Origin selected: " + place.getName());
                originInput.setText(place.getName());
                updateOriginMarker(originLatLng);
            });
        });

        // listener for when the user types an address and presses enter
        originInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                String address = originInput.getText().toString();
                if (!TextUtils.isEmpty(address)) {
                    // hide system keyboard
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(originInput.getWindowToken(), 0);

                    // geocoding the address
                    geocodeAddress(address, latLng -> {
                        if (latLng != null) {
                            originLatLng = latLng;
                            updateOriginMarker(originLatLng);
                        } else {
                            Toast.makeText(MapActivity.this, "Could not find location: " + address,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                }
            }
            return false;
        });

        // setting up destination autocomplete
        AutoCompleteTextView destinationInput = findViewById(R.id.destination_input);
        PlacesAutocompleteAdapter destinationAdapter = new PlacesAutocompleteAdapter(this);
        destinationInput.setAdapter(destinationAdapter);

        destinationInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 2) {
                    destinationAdapter.getPredictions(s.toString());
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        destinationInput.setOnItemClickListener((parent, view, position, id) -> {
            AutocompletePrediction prediction = destinationAdapter.getItem(position);
            getPlaceFromPrediction(prediction, place -> {
                destinationLatLng = place.getLatLng();
                Log.d("PlacesAPI", "Destination selected: " + place.getName());
                destinationInput.setText(place.getName());
                updateDestinationMarker(destinationLatLng);
            });
        });

        // listener for when the user manually types a destination
        destinationInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {

                String address = destinationInput.getText().toString();
                if (!TextUtils.isEmpty(address)) {
                    // hide keyboard
                    hideKeyboard();

                    // geocoding the address
                    geocodeAddress(address, latLng -> {
                        if (latLng != null) {
                            destinationLatLng = latLng;
                            updateDestinationMarker(destinationLatLng);

                            // trigger route finding if both locations are set
                            if (originLatLng != null) {
                                findRoutes();
                            }
                        } else {
                            Toast.makeText(MapActivity.this, "Could not find location: " + address,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * Updates the origin marker on the map.
     *
     * @param latLng Coordinates for the origin marker
     */
    private void updateOriginMarker(LatLng latLng) {
        if (latLng == null) return;

        // remove existing marker (if exists)
        if (currentLocationMarker != null) {
            currentLocationMarker.remove();
        }

        // adding new marker
        currentLocationMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Origin")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

        // relocate camera to the location
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    /**
     * Updates the destination marker on the map.
     *
     * @param latLng Coordinates for the destination marker
     */
    private void updateDestinationMarker(LatLng latLng) {
        if (latLng == null) return;

        // remove existing markers (if exists)
        if (destinationMarker != null) {
            destinationMarker.remove();
        }

        // adding new marker
        destinationMarker = mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Destination"));

        // moving camera
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
    }

    /**
     * Retrieves place details from a Places API prediction.
     *
     * @param prediction The prediction from Places Autocomplete
     * @param callback Callback to return the Place object
     */
    private void getPlaceFromPrediction(AutocompletePrediction prediction, PlaceCallback callback) {
        PlacesClient placesClient = Places.createClient(this);
        String placeId = prediction.getPlaceId();

        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS);

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields).build();

        placesClient.fetchPlace(request).addOnSuccessListener((response) -> {
            Place place = response.getPlace();
            callback.onPlaceRetrieved(place);
            // shutdown the client to prevent memory leaks
            if (placesClient instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) placesClient).close();
                } catch (Exception e) {
                    Log.e("PlacesClient", "Error closing client", e);
                }
            }
        }).addOnFailureListener((exception) -> {
            if (exception instanceof ApiException) {
                ApiException apiException = (ApiException) exception;
                Log.e("PlacesAPI", "Place not found: " + apiException.getStatusCode());
            }
            // cleanup either when successful or failed
            if (placesClient instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) placesClient).close();
                } catch (Exception e) {
                    Log.e("PlacesClient", "Error closing client", e);
                }
            }
        });
    }

    /**
     * Gets the current location marker.
     *
     * @return The current location marker
     */
    public Marker getCurrentLocationMarker() {
        return currentLocationMarker;
    }

    /**
     * Gets the destination marker.
     *
     * @return The destination marker
     */
    public Marker getDestinationMarker() {
        return destinationMarker;
    }

    /**
     * Recreates the current location marker on the map. Used after map clearing operations to maintain markers.
     */
    public void recreateCurrentLocationMarker() {
        if (originLatLng != null) {
            currentLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(originLatLng)
                    .title("My Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)) // Green for starting point
                    .alpha(1.0f) // Fully opaque
                    .zIndex(2.0f)); // Same z-index as destination
        }
    }

    /**
     * Recreates the destination marker on the map. Used after map clearing operations to maintain markers.
     */
    public void recreateDestinationMarker() {
        if (destinationLatLng != null) {
            destinationMarker = mMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title("Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Standard red
                    .alpha(1.0f)
                    .zIndex(2.0f));
        }
    }

    /**
     * Loads the default user profile from the database.
     */
    private void loadDefaultProfile() {
        if (profileManager != null) {
            // ensure that at least one profile exists
            profileManager.ensureDefaultProfile();
            currentProfile = profileManager.getDefaultProfile();

            // updating filters UI with new settings
            if (currentProfile != null && filtersFragment != null) {
                filtersFragment.setCurrentProfile(currentProfile);
            }
        }
    }

    /**
     * Callback when a user profile is selected.
     * Updates the filters to match the selected profile.
     *
     * @param profile The selected user profile
     */
    public void onProfileSelected(UserProfile profile) {
        if (profile != null) {
            currentProfile = profile;

            // updating filter UI to match selected profile
            if (filtersFragment != null) {
                filtersFragment.setCurrentProfile(profile);
            }
        }
    }

    /**
     * Changes the visibility of the search panel.
     */
    private void toggleSearchPanel() {
        if (isSearchPanelExpanded) {
            // Collapse
            searchPanel.animate()
                    .translationY(-searchPanel.getHeight())
                    .setDuration(300)
                    .withEndAction(() -> {
                        searchPanel.setVisibility(View.GONE);
                        toggleSearchButton.setImageResource(R.drawable.ic_arrow_down);
                    })
                    .start();

            // button animation
            toggleSearchButton.animate()
                    .translationY(80)
                    .setDuration(300)
                    .start();
        } else {
            // Expand
            searchPanel.setVisibility(View.VISIBLE);
            searchPanel.animate()
                    .translationY(0)
                    .setDuration(300)
                    .withEndAction(() -> {
                        toggleSearchButton.setImageResource(R.drawable.ic_arrow_up);
                    })
                    .start();

            // button animation
            toggleSearchButton.animate()
                    .translationY(80)
                    .setDuration(300)
                    .start();
        }
        isSearchPanelExpanded = !isSearchPanelExpanded;
    }

    /**
     * Changes the visibility and height of the bottom panel.
     */
    private void toggleBottomPanel() {
        if (isBottomPanelExpanded) {
            // save current expanded height for animation
            final int fullHeight = bottomPanel.getHeight();
            final int collapsedHeight = tabLayout.getHeight() + toggleBottomPanelButton.getHeight();

            // calculate the distance to animate
            final int animDistance = fullHeight - collapsedHeight;

            // height animation
            ValueAnimator animator = ValueAnimator.ofInt(fullHeight, collapsedHeight);
            animator.setDuration(300);
            animator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams params = bottomPanel.getLayoutParams();
                params.height = (int) animation.getAnimatedValue();
                bottomPanel.setLayoutParams(params);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    toggleBottomPanelButton.setImageResource(R.drawable.ic_arrow_up);
                }
            });
            animator.start();
        } else {
            // Save current collapsed height for animation
            final int collapsedHeight = bottomPanel.getHeight();

            // height change
            ValueAnimator animator = ValueAnimator.ofInt(collapsedHeight, bottomPanelHeight);
            animator.setDuration(300);
            animator.addUpdateListener(animation -> {
                ViewGroup.LayoutParams params = bottomPanel.getLayoutParams();
                params.height = (int) animation.getAnimatedValue();
                bottomPanel.setLayoutParams(params);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    toggleBottomPanelButton.setImageResource(R.drawable.ic_arrow_down);
                }
            });
            animator.start();
        }

        isBottomPanelExpanded = !isBottomPanelExpanded;
    }

    /**
     * Interface for Place retrieval callbacks.
     */
    interface PlaceCallback {
        /**
         * Called when a Place is retrieved from the Places API.
         *
         * @param place The retrieved Place object
         */
        void onPlaceRetrieved(Place place);
    }
}