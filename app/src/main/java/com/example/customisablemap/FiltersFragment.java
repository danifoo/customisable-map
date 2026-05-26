package com.example.customisablemap;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * A fragment that manages transportation filters for route planning.
 * This fragment allows users to toggle preferences for different transport modes
 * (bus, bike, train) and set their maximum walking distance.
 * Changes to filter settings are saved to both local SQLite storage and Firebase.
 */
public class FiltersFragment extends Fragment {
    private static final String TAG = "FiltersFragment";
    private SwitchCompat busSwitch;
    private SwitchCompat subwaySwitch;
    private SwitchCompat trainSwitch;
    private SeekBar maxWalkingSeekBar;
    private TextView walkingDistanceText;
    private boolean isUiInitialized = false;
    private ProfileSQLiteManager profileManager;
    private FirebaseDataManager firebaseManager;
    private UserProfile currentProfile;

    /**
     * Called to create a user interface for the fragment.
     *
     * @param inflater The LayoutInflater object that can be used to inflate views
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to
     * @param savedInstanceState Helps the fragment to get re-constructed from a previous saved state
     * @return The View for the fragment's UI
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_filters, container, false);

        try {
            // initializing the UI elements
            busSwitch = view.findViewById(R.id.bus_switch);
            subwaySwitch = view.findViewById(R.id.subway_switch);
            trainSwitch = view.findViewById(R.id.train_switch);
            maxWalkingSeekBar = view.findViewById(R.id.max_walking_seekbar);
            walkingDistanceText = view.findViewById(R.id.walking_distance_text);

            // the seekbar for the walk distance
            maxWalkingSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int distance = progress * 100;
                    walkingDistanceText.setText(distance + " meters");
                    if (currentProfile != null && fromUser) {
                        updateCurrentProfileFilter();
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            // switch listeners
            busSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (currentProfile != null) {
                    updateCurrentProfileFilter();
                }
            });

            subwaySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (currentProfile != null) {
                    updateCurrentProfileFilter();
                }
            });

            trainSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (currentProfile != null) {
                    updateCurrentProfileFilter();
                }
            });

            isUiInitialized = true;
        } catch (Exception e) {
            Log.e("FiltersFragment", "Error initializing UI", e);
            isUiInitialized = false;
        }

        return view;
    }

    /**
     * Updates the current profile's filter settings based on the UI state.
     * Saves changes to both the local database and Firebase.
     */
    private void updateCurrentProfileFilter() {
        if (currentProfile != null && profileManager != null && isUiInitialized) {
            try {
                // update current profile with new values from UI
                currentProfile.setPreferBus(busSwitch.isChecked());
                currentProfile.setPreferSubway(subwaySwitch.isChecked());
                currentProfile.setPreferTrain(trainSwitch.isChecked());
                currentProfile.setMaxWalkingDistance(maxWalkingSeekBar.getProgress() * 100);

                // update local database
                boolean success = profileManager.updateProfileFilter(
                        currentProfile.getId(),
                        currentProfile.isPreferBus(),
                        currentProfile.isPreferSubway(),
                        currentProfile.isPreferTrain(),
                        currentProfile.getMaxWalkingDistance()
                );

                // sync to Firebase
                if (success && firebaseManager != null) {
                    Log.d(TAG, "Syncing profile filter changes to Firebase");
                    firebaseManager.saveProfileToFirebase(currentProfile, new FirebaseDataManager.FirebaseOperationCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Filter changes successfully synced to Firebase");
                        }

                        @Override
                        public void onError(String errorMessage) {
                            Log.e(TAG, "Error syncing filter changes to Firebase: " + errorMessage);
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Changes saved locally but failed to sync with cloud",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error updating profile filter", e);
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error updating filters: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Log.w(TAG, "Cannot update profile filter: profile=" + (currentProfile != null) +
                    ", manager=" + (profileManager != null) +
                    ", UI initialized=" + isUiInitialized);
        }
    }

    /**
     * Initializes the profile and Firebase managers.
     *
     * @param context The context the fragment is attached to
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String userId = user.getUid();
            profileManager = new ProfileSQLiteManager(context, userId);

            firebaseManager = new FirebaseDataManager(context, userId);
        }
    }

    /**
     * Sets the current profile to display and edit.
     *
     * @param profile The user profile settings to display
     */
    public void setCurrentProfile(UserProfile profile) {
        this.currentProfile = profile;
        updateUI();
    }

    /**
     * Updates the UI controls to reflect the current profile's settings.
     */
    private void updateUI() {
        if (isUiInitialized && currentProfile != null) {
            busSwitch.setChecked(currentProfile.isPreferBus());
            subwaySwitch.setChecked(currentProfile.isPreferSubway());
            trainSwitch.setChecked(currentProfile.isPreferTrain());

            int progress = currentProfile.getMaxWalkingDistance() / 100;
            maxWalkingSeekBar.setProgress(progress);
            walkingDistanceText.setText(currentProfile.getMaxWalkingDistance() + " meters");
        }
    }

    /**
     * Returns bus preference.
     *
     * @return true if bus transportation is preferred and false otherwise
     */
    public boolean isBusPreferred() {
        return isUiInitialized && busSwitch.isChecked();
    }

    /**
     * Returns bike preference.
     * In the beginning of the project, this filter was configured to be subway and later on a decision was made to change it to bike.
     * To keep the refactoring of the codebase to a minimal, all the subway related methods were repurposed for bike preference.
     *
     * @return true if subway transportation is preferred and false otherwise
     */
    public boolean isSubwayPreferred() {
        return isUiInitialized && subwaySwitch.isChecked();
    }

    /**
     * Returns train (and tram) preference.
     *
     * @return true if train transportation is preferred and false otherwise
     */
    public boolean isTrainPreferred() {
        return isUiInitialized && trainSwitch.isChecked();
    }

    /**
     * Returns the maximum walking distance currently set.
     *
     * @return The maximum walking distance in meters, or 1000 if UI is not initialized
     */
    public int getMaxWalkingDistance() {
        return isUiInitialized ? maxWalkingSeekBar.getProgress() * 100 : 1000;
    }

    /**
     * Returns whether the UI has been fully initialized.
     *
     * @return true if the UI is initialized, false otherwise
     */
    public boolean isUiInitialized() {
        return isUiInitialized;
    }
}