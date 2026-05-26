package com.example.customisablemap;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles syncing of user profiles between local SQLite database and Firebase.
 */
public class FirebaseDataManager {
    private static final String TAG = "FirebaseDataManager";
    private final DatabaseReference mDatabase;
    private final String userId;
    private final ProfileSQLiteManager sqliteManager;
    private final Context context;

    /**
     * Constructor that initializes Firebase and SQLite managers.
     *
     * @param context Application context used for Toast notifications and database operations
     * @param userId The ID of the user
     */
    public FirebaseDataManager(Context context, String userId) {
        this.context = context;
        this.userId = userId;
        this.mDatabase = FirebaseDatabase.getInstance().getReference();
        this.sqliteManager = new ProfileSQLiteManager(context, userId);
    }

    /**
     * Interface for Firebase operation callbacks.
     * Used to notify callers about the success or failure of Firebase operations.
     */
    public interface FirebaseOperationCallback {
        /**
         * Called when the operation completes successfully.
         */
        void onSuccess();

        /**
         * Called when the operation fails with an error message.
         *
         * @param errorMessage A description of what went wrong
         */
        void onError(String errorMessage);
    }

    /**
     * Verifies that the user is authenticated before performing Firebase operations.
     *
     * @return true if user is authenticated and the user ID matches, false otherwise
     */
    private boolean verifyAuthentication() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "User not authenticated, cannot perform Firebase operation");
            Toast.makeText(context, "Please sign in to sync your profiles", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!currentUser.getUid().equals(userId)) {
            Log.e(TAG, "User ID mismatch. Expected: " + userId + ", Actual: " + currentUser.getUid());
            return false;
        }

        return true;
    }

    /**
     * Creates a Firebase reference to a user's data.
     *
     * @param profileId The ID of the profile to reference
     * @return DatabaseReference pointing to the specific profile in Firebase
     */
    private DatabaseReference getProfilesReference(String profileId) {
        // path to exactly match the Firebase internal rules: profiles/$uid/$profileId
        return mDatabase.child("profiles").child(userId).child(profileId);
    }

    /**
     * This method performs a sync from Firebase to local SQLite.
     *
     * @param callback A callback to notify on completion or error
     */
    public void syncFirebaseToLocal(final SyncCallback callback) {
        mDatabase.child("profiles").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    for (DataSnapshot profileSnapshot : dataSnapshot.getChildren()) {
                        UserProfile firebaseProfile = profileSnapshot.getValue(UserProfile.class);
                        if (firebaseProfile != null) {
                            long profileId;
                            try {
                                profileId = Long.parseLong(profileSnapshot.getKey());
                            } catch (NumberFormatException e) {
                                // for non-numeric keys like "default", use a hash code
                                profileId = Math.abs(profileSnapshot.getKey().hashCode());
                                Log.d(TAG, "Using hash code for non-numeric key: " +
                                        profileSnapshot.getKey() + " -> " + profileId);
                            }

                            firebaseProfile.setId(profileId);

                            if (firebaseProfile != null) {
                                // profile ID from the snapshot key
                                firebaseProfile.setId(profileId);

                                // checking if profile exists locally
                                UserProfile localProfile = sqliteManager.getProfile(profileId);

                                if (localProfile == null) {
                                    // creating new profile with the same ID from Firebase
                                    sqliteManager.createProfileWithId(profileId,
                                            firebaseProfile.getProfileName(),
                                            firebaseProfile.isDefault());
                                    sqliteManager.updateProfileFilter(profileId,
                                            firebaseProfile.isPreferBus(),
                                            firebaseProfile.isPreferSubway(),
                                            firebaseProfile.isPreferTrain(),
                                            firebaseProfile.getMaxWalkingDistance());
                                } else {
                                    // updating existing profile
                                    sqliteManager.updateProfile(profileId,
                                            firebaseProfile.getProfileName(),
                                            firebaseProfile.isDefault());
                                    sqliteManager.updateProfileFilter(profileId,
                                            firebaseProfile.isPreferBus(),
                                            firebaseProfile.isPreferSubway(),
                                            firebaseProfile.isPreferTrain(),
                                            firebaseProfile.getMaxWalkingDistance());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during profile sync", e);
                    if (callback != null) {
                        callback.onSyncError("Error: " + e.getMessage());
                    }
                    return;
                }

                // call callback when successful
                if (callback != null) {
                    callback.onSyncComplete();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error syncing from Firebase: " + databaseError.getMessage());
                if (callback != null) {
                    callback.onSyncError(databaseError.getMessage());
                }
            }
        });
    }

    /**
     * Saves a profile to Firebase.
     *
     * @param profile The profile to save
     * @param callback Callback to notify of success or failure
     */
    public void saveProfileToFirebase(UserProfile profile, final FirebaseOperationCallback callback) {
        // user authentication
        if (!verifyAuthentication()) {
            if (callback != null) callback.onError("Not authenticated");
            return;
        }

        // making sure profileId is valid
        if (profile == null || profile.getId() <= 0) {
            Log.e(TAG, "Invalid profile object or ID");
            if (callback != null) callback.onError("Invalid profile");
            return;
        }

        Map<String, Object> profileValues = new HashMap<>();
        profileValues.put("profileName", profile.getProfileName());
        profileValues.put("preferBus", profile.isPreferBus());
        profileValues.put("preferSubway", profile.isPreferSubway());
        profileValues.put("preferTrain", profile.isPreferTrain());
        profileValues.put("maxWalkingDistance", profile.getMaxWalkingDistance());
        profileValues.put("isDefault", profile.isDefault());

        // getting the reference
        String profileIdStr = String.valueOf(profile.getId());
        DatabaseReference profileRef = getProfilesReference(profileIdStr);

        Log.d(TAG, "Writing to Firebase path: " + profileRef.toString());

        profileRef.setValue(profileValues)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile saved to Firebase successfully at path: " + profileRef.toString());
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving profile to Firebase at path: " + profileRef.toString(), e);
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /**
     * Deletes a profile from Firebase.
     *
     * @param profileId ID of the profile
     * @param callback Callback to notify of success or failure
     */
    public void deleteProfileFromFirebase(long profileId, final FirebaseOperationCallback callback) {
        if (!verifyAuthentication()) {
            if (callback != null) callback.onError("Not authenticated");
            return;
        }

        String profileIdStr = String.valueOf(profileId);
        DatabaseReference profileRef = getProfilesReference(profileIdStr);

        Log.d(TAG, "Deleting from Firebase path: " + profileRef.toString());

        profileRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile deleted from Firebase successfully at path: " + profileRef.toString());
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting profile from Firebase at path: " + profileRef.toString(), e);
                    if (callback != null) callback.onError(e.getMessage());
                });
    }

    /**
     * Saves a profile to Firebase without callback.
     * Convenience method that calls saveProfileToFirebase with null callback.
     *
     * @param profile The profile to save
     */
    public void saveProfileToFirebase(UserProfile profile) {
        saveProfileToFirebase(profile, null);
    }

    /**
     * Deletes a profile from Firebase without callback.
     * Convenience method that calls deleteProfileFromFirebase with null callback.
     *
     * @param profileId ID of the profile to delete
     */
    public void deleteProfileFromFirebase(long profileId) {
        deleteProfileFromFirebase(profileId, null);
    }

    /**
     * Interface for sync callbacks.
     */
    public interface SyncCallback {
        /**
         * Called when sync completes successfully.
         */
        void onSyncComplete();

        /**
         * Called when sync comes back with an error.
         *
         * @param errorMessage The error message
         */
        void onSyncError(String errorMessage);
    }

    /**
     * Syncs profiles from Firebase to local database in the background.
     *
     * @param callback Callback to notify when sync completes or fails
     */
    public void syncFirebaseToLocalBackground(final SyncCallback callback) {
        if (!verifyAuthentication()) {
            if (callback != null) callback.onSyncError("Not authenticated");
            return;
        }

        // reference to profiles/userId
        DatabaseReference userProfilesRef = mDatabase.child("profiles").child(userId);
        Log.d(TAG, "Syncing from Firebase path: " + userProfilesRef.toString());

        userProfilesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                try {
                    Log.d(TAG, "Received data snapshot with " + dataSnapshot.getChildrenCount() + " profiles");

                    // going through each profile in the snapshot
                    for (DataSnapshot profileSnapshot : dataSnapshot.getChildren()) {
                        UserProfile firebaseProfile = profileSnapshot.getValue(UserProfile.class);
                        if (firebaseProfile != null) {
                            long profileId;
                            try {
                                profileId = Long.parseLong(profileSnapshot.getKey());
                            } catch (NumberFormatException e) {
                                // for non-numeric keys like "default", use a hash code
                                profileId = Math.abs(profileSnapshot.getKey().hashCode());
                                Log.d(TAG, "Using hash code for non-numeric key: " +
                                        profileSnapshot.getKey() + " -> " + profileId);
                            }

                            // set the ID in the profile object
                            firebaseProfile.setId(profileId);

                            // check if profile exists locally by ID
                            UserProfile localProfile = sqliteManager.getProfile(profileId);

                            if (localProfile == null) {
                                // new profile with the same ID from Firebase
                                sqliteManager.createProfileWithId(
                                        profileId,
                                        firebaseProfile.getProfileName(),
                                        firebaseProfile.isDefault()
                                );
                                // adding filter settings
                                sqliteManager.updateProfileFilter(
                                        profileId,
                                        firebaseProfile.isPreferBus(),
                                        firebaseProfile.isPreferSubway(),
                                        firebaseProfile.isPreferTrain(),
                                        firebaseProfile.getMaxWalkingDistance()
                                );
                                Log.d(TAG, "Created new local profile: " + firebaseProfile.getProfileName());
                            } else {
                                // update existing profile
                                sqliteManager.updateProfile(
                                        profileId,
                                        firebaseProfile.getProfileName(),
                                        firebaseProfile.isDefault()
                                );
                                sqliteManager.updateProfileFilter(
                                        profileId,
                                        firebaseProfile.isPreferBus(),
                                        firebaseProfile.isPreferSubway(),
                                        firebaseProfile.isPreferTrain(),
                                        firebaseProfile.getMaxWalkingDistance()
                                );
                                Log.d(TAG, "Updated existing local profile: " + firebaseProfile.getProfileName());
                            }
                        }
                    }

                    // profiles synced successfully
                    Log.d(TAG, "Background sync completed successfully");
                    if (callback != null) {
                        callback.onSyncComplete();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during profile sync", e);
                    if (callback != null) {
                        callback.onSyncError("Error: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error syncing from Firebase: " + databaseError.getMessage());
                if (callback != null) {
                    callback.onSyncError(databaseError.getMessage());
                }
            }
        });
    }
}