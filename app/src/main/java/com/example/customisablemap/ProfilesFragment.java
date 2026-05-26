package com.example.customisablemap;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for managing user transportation preference profiles.
 */
public class ProfilesFragment extends Fragment {
    private static final String TAG = "ProfilesFragment";
    private RecyclerView profilesRecyclerView;
    private ProfileAdapter profileAdapter;
    private List<UserProfile> profiles = new ArrayList<>();
    private ProfileSQLiteManager profileManager;
    private FirebaseDataManager firebaseManager;
    private Button addProfileButton;
    private Button logoutButton;
    private ProgressBar syncProgressBar;
    private ValueEventListener profilesListener;

    /**
     * Called to have the fragment instantiate its user interface view.
     *
     * @param inflater The LayoutInflater object that can be used to inflate views
     * @param container If non-null, this is the parent view that the fragment's UI should be attached to
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state
     * @return The View for the fragment's UI
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profiles, container, false);

        // initialize UI
        profilesRecyclerView = view.findViewById(R.id.profiles_recycler_view);
        addProfileButton = view.findViewById(R.id.add_profile_button);
        logoutButton = view.findViewById(R.id.logout_button);

        // progress bar (for sync operations)
        syncProgressBar = view.findViewById(R.id.sync_progress_bar);
        if (syncProgressBar == null) {
            // If not in layout, create it programmatically
            syncProgressBar = new ProgressBar(requireContext());
            syncProgressBar.setId(View.generateViewId());
            syncProgressBar.setVisibility(View.GONE);
            ((ViewGroup) view).addView(syncProgressBar);
        }

        // recyclerView
        profilesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        profileAdapter = new ProfileAdapter(
                profiles,
                this::onProfileSelected,
                this::onProfileSetDefault,
                this::onProfileDelete
        );
        profilesRecyclerView.setAdapter(profileAdapter);

        // add profile button listener
        addProfileButton.setOnClickListener(v -> showAddProfileDialog());

        // logout button listener
        logoutButton.setOnClickListener(v -> showLogoutConfirmationDialog());

        return view;
    }

    /**
     * Called when the fragment is attached to an activity.
     *
     * @param context The context the fragment is attached to
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            profileManager = new ProfileSQLiteManager(context, user.getUid());
            // Firebase manager
            firebaseManager = new FirebaseDataManager(context, user.getUid());
        }
    }

    /**
     * Called when the fragment is visible to the user and actively running.
     */
    @Override
    public void onResume() {
        super.onResume();
        loadProfiles();
    }

    /**
     * Called when the fragment is being destroyed.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        // cleanup up any Firebase listeners
        if (firebaseManager != null && profilesListener != null) {
            FirebaseDatabase.getInstance().getReference("profiles")
                    .child(FirebaseAuth.getInstance().getUid())
                    .removeEventListener(profilesListener);
        }
    }

    /**
     * Loads profiles from the local database.
     */
    private void loadProfiles() {
        if (profileManager != null) {
            profiles.clear();
            profiles.addAll(profileManager.getAllProfiles());

            // create a default one if no profiles exist
            if (profiles.isEmpty()) {
                long profileId = profileManager.ensureDefaultProfile();
                profiles.addAll(profileManager.getAllProfiles());
            }

            profileAdapter.notifyDataSetChanged();

            // notify MapActivity about the default profile
            notifyProfileSelected(getDefaultProfile());
        }
    }

    /**
     * Gets the default profile from the loaded profiles.
     *
     * @return The default profile, or the first profile if none is marked as default,
     */
    private UserProfile getDefaultProfile() {
        for (UserProfile profile : profiles) {
            if (profile.isDefault()) {
                return profile;
            }
        }
        return profiles.isEmpty() ? null : profiles.get(0);
    }

    /**
     * Shows a dialog for adding a new profile.
     */
    private void showAddProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Create New Profile");

        // input setup
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Profile Name");
        builder.setView(input);

        // buttons setup
        builder.setPositiveButton("Create", (dialog, which) -> {
            String profileName = input.getText().toString().trim();
            if (!profileName.isEmpty()) {
                createNewProfile(profileName);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        // create dialog first
        AlertDialog dialog = builder.create();

        // showing the dialog
        dialog.show();

        // dialog buttons customisation
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.colorDeepGrey));
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(requireContext(), R.color.colorDeepGrey));
    }

    /**
     * Creates a new profile with the given name.
     *
     * @param profileName The name for the new profile
     */
    private void createNewProfile(String profileName) {
        Log.d(TAG, "in create new profile");
        if (!verifyAuthentication()) {
            Toast.makeText(requireContext(), "Please sign in to create profiles", Toast.LENGTH_SHORT).show();
            syncProgressBar.setVisibility(View.GONE);
            return;
        }
        if (profileManager != null) {
            try {
                // loading indicator
                syncProgressBar.setVisibility(View.VISIBLE);

                // creating profile in local database
                long profileId = profileManager.createProfile(profileName, false);
                if (profileId <= 0) {
                    syncProgressBar.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Failed to create profile locally", Toast.LENGTH_SHORT).show();
                    return;
                }

                // create default filters for this profile
                boolean filterSuccess = profileManager.createFilterForProfile(profileId, true, true, true, 1000);
                if (!filterSuccess) {
                    Log.w(TAG, "Failed to create default filters for profile");
                }

                // Firebase sync
                UserProfile newProfile = profileManager.getProfile(profileId);
                if (newProfile != null && firebaseManager != null) {
                    Log.d(TAG, "in the if");
                    firebaseManager.saveProfileToFirebase(newProfile, new FirebaseDataManager.FirebaseOperationCallback() {
                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "profile created successfully");
                            syncProgressBar.setVisibility(View.GONE);
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "Profile created successfully",
                                        Toast.LENGTH_SHORT).show();
                                // Refresh the list
                                loadProfiles();
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            syncProgressBar.setVisibility(View.GONE);
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Profile created locally but failed to sync with cloud",
                                        Toast.LENGTH_SHORT).show();
                                // refresh the local list regardless
                                loadProfiles();
                            }
                        }
                    });
                } else {
                    Log.d(TAG, "in the else");
                    syncProgressBar.setVisibility(View.GONE);
                    loadProfiles();
                }
            } catch (Exception e) {
                syncProgressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error creating profile", e);
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error creating profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Callback when a profile is clicked.
     *
     * @param profile The profile that was clicked
     */
    private void onProfileSelected(UserProfile profile) {
        // notify MapActivity about the selected profile
        notifyProfileSelected(profile);
    }

    /**
     * Callback when a profile is set as the default.
     *
     * @param profile The profile to set as default
     */
    private void onProfileSetDefault(UserProfile profile) {
        if (profileManager != null) {
            try {
                syncProgressBar.setVisibility(View.VISIBLE);

                profileManager.setDefaultProfile(profile.getId());

                // sync with Firebase
                if (firebaseManager != null) {
                    UserProfile updatedProfile = profileManager.getProfile(profile.getId());
                    if (updatedProfile != null) {
                        firebaseManager.saveProfileToFirebase(updatedProfile, new FirebaseDataManager.FirebaseOperationCallback() {
                            @Override
                            public void onSuccess() {
                                // update other profiles in Firebase that were previously default
                                updateOtherProfilesDefaultStatus(profile.getId());
                            }

                            @Override
                            public void onError(String errorMessage) {
                                syncProgressBar.setVisibility(View.GONE);
                                if (isAdded()) {
                                    Toast.makeText(requireContext(),
                                            "Default profile set locally but failed to sync with cloud",
                                            Toast.LENGTH_SHORT).show();
                                }
                                loadProfiles();
                            }
                        });
                    } else {
                        syncProgressBar.setVisibility(View.GONE);
                        loadProfiles();
                    }
                } else {
                    syncProgressBar.setVisibility(View.GONE);
                    loadProfiles();
                }
            } catch (Exception e) {
                syncProgressBar.setVisibility(View.GONE);
                Log.e(TAG, "Error setting default profile", e);
                if (isAdded()) {
                    Toast.makeText(requireContext(), "Error setting default profile: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    /**
     * Updates the default status of other profiles in Firebase.
     *
     * @param newDefaultId The ID of the new default profile
     */
    private void updateOtherProfilesDefaultStatus(long newDefaultId) {
        try {
            // updating previous default profiles in Firebase
            int syncCounter = 0;
            final int[] syncCompleted = {0};
            final boolean[] hasError = {false};

            for (UserProfile otherProfile : profiles) {
                if (otherProfile.getId() != newDefaultId && otherProfile.isDefault()) {
                    otherProfile.setDefault(false);
                    syncCounter++;

                    final int totalSyncs = syncCounter;

                    firebaseManager.saveProfileToFirebase(otherProfile, new FirebaseDataManager.FirebaseOperationCallback() {
                        @Override
                        public void onSuccess() {
                            syncCompleted[0]++;
                            if (syncCompleted[0] >= totalSyncs && !hasError[0]) {
                                syncProgressBar.setVisibility(View.GONE);
                                loadProfiles();
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            hasError[0] = true;
                            syncCompleted[0]++;
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "Some profiles failed to sync with cloud",
                                        Toast.LENGTH_SHORT).show();
                            }

                            if (syncCompleted[0] >= totalSyncs) {
                                syncProgressBar.setVisibility(View.GONE);
                                loadProfiles();
                            }
                        }
                    });
                }
            }

            // if there is no previous defaults to update then just refresh the UI
            if (syncCounter == 0) {
                syncProgressBar.setVisibility(View.GONE);
                loadProfiles();
            }
        } catch (Exception e) {
            syncProgressBar.setVisibility(View.GONE);
            Log.e(TAG, "Error updating other profiles default status", e);
            loadProfiles();
        }
    }

    /**
     * Callback when a profile is selected for deletion.
     *
     * @param profile The profile to delete
     */
    private void onProfileDelete(UserProfile profile) {
        // confirmation dialog for deleting
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Profile")
                .setMessage("Are you sure you want to delete the profile '" + profile.getProfileName() + "'?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (profileManager != null) {
                        try {
                            syncProgressBar.setVisibility(View.VISIBLE);

                            // delete from local database
                            boolean success = profileManager.deleteProfile(profile.getId());

                            if (success) {
                                // Firebase deletion with callback
                                if (firebaseManager != null) {
                                    firebaseManager.deleteProfileFromFirebase(profile.getId(),
                                            new FirebaseDataManager.FirebaseOperationCallback() {
                                                @Override
                                                public void onSuccess() {
                                                    syncProgressBar.setVisibility(View.GONE);
                                                    if (isAdded()) {
                                                        Toast.makeText(requireContext(),
                                                                "Profile deleted successfully",
                                                                Toast.LENGTH_SHORT).show();
                                                        loadProfiles();
                                                    }
                                                }

                                                @Override
                                                public void onError(String errorMessage) {
                                                    syncProgressBar.setVisibility(View.GONE);
                                                    if (isAdded()) {
                                                        Toast.makeText(requireContext(),
                                                                "Profile deleted locally but failed to sync with cloud",
                                                                Toast.LENGTH_SHORT).show();
                                                        loadProfiles();
                                                    }
                                                }
                                            });
                                } else {
                                    syncProgressBar.setVisibility(View.GONE);
                                    Toast.makeText(requireContext(), "Profile deleted", Toast.LENGTH_SHORT).show();
                                    loadProfiles();
                                }
                            } else {
                                syncProgressBar.setVisibility(View.GONE);
                                Toast.makeText(requireContext(),
                                        "Cannot delete default profile. Set another profile as default first.",
                                        Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            syncProgressBar.setVisibility(View.GONE);
                            Log.e(TAG, "Error deleting profile", e);
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "Error deleting profile: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Notifies the parent activity about a selected profile.
     *
     * @param profile The selected profile
     */
    private void notifyProfileSelected(UserProfile profile) {
        if (getActivity() instanceof MapActivity && profile != null) {
            ((MapActivity) getActivity()).onProfileSelected(profile);
        }
    }

    /**
     * Shows a confirmation dialog for logging out.
     */
    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Performs the logout operation.
     */
    private void performLogout() {
        try {
            // sign out from Firebase
            FirebaseAuth.getInstance().signOut();

            // back to Login screen
            Intent intent = new Intent(requireActivity(), LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        } catch (Exception e) {
            Log.e("ProfilesFragment", "Error during logout", e);
            Toast.makeText(requireContext(), "Logout failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Refreshes the profiles list.
     */
    public void refreshProfiles() {
        if (isAdded()) {
            loadProfiles();
        }
    }

    /**
     * Verifies that the user is authenticated before performing Firebase operations.
     *
     * @return true if user is authenticated and false otherwise
     */
    private boolean verifyAuthentication() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "Firebase operation attempted without authentication");
            return false;
        }
        return true;
    }
}