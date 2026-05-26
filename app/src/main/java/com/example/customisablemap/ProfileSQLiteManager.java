package com.example.customisablemap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Manager class for SQLite database operations related to user profiles.
 */
public class ProfileSQLiteManager {
    private static final String TAG = "ProfileSQLiteManager";
    private final DatabaseHelper dbHelper;
    private final String userId;
    private final Context context;

    /**
     * Creates a new ProfileSQLiteManager.
     *
     * @param context Application context needed for database and Firebase operations
     * @param userId The ID of the current user
     */
    public ProfileSQLiteManager(Context context, String userId) {
        this.dbHelper = new DatabaseHelper(context);
        this.userId = userId;
        this.context = context;
    }

    /**
     * Creates a new profile in the local database.
     *
     * @param profileName The name for the profile
     * @param isDefault Whether this profile should be set as the default
     * @return The ID of the newly created profile, or -1 if creation failed
     */
    public long createProfile(String profileName, boolean isDefault) {
        long profileId = dbHelper.addProfile(userId, profileName, isDefault);

        // sync with Firebase if possible
        if (profileId > 0) {
            UserProfile profile = getProfile(profileId);
            if (profile != null) {
                FirebaseDataManager firebaseManager = new FirebaseDataManager(context, userId);
                firebaseManager.saveProfileToFirebase(profile);
            }
        }

        return profileId;
    }

    /**
     * Creates filter settings for an existing profile.
     *
     * @param profileId The ID of the profile
     * @param preferBus Bus preference
     * @param preferSubway Bike preference
     * @param preferTrain Train preference
     * @param maxWalkingDistance Maximum walking distance
     * @return true if filter creation was successful, false otherwise
     */
    public boolean createFilterForProfile(long profileId, boolean preferBus, boolean preferSubway,
                                          boolean preferTrain, int maxWalkingDistance) {
        return dbHelper.addFilterToProfile(profileId, preferBus, preferSubway, preferTrain, maxWalkingDistance) > 0;
    }

    /**
     * Retrieves all profiles for the current user.
     *
     * @return List of UserProfile objects
     */
    public List<UserProfile> getAllProfiles() {
        List<UserProfile> profiles = new ArrayList<>();
        Cursor profileCursor = dbHelper.getUserProfiles(userId);

        if (profileCursor != null && profileCursor.moveToFirst()) {
            do {
                long profileId = profileCursor.getLong(profileCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID));
                String profileName = profileCursor.getString(profileCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PROFILE_NAME));
                boolean isDefault = profileCursor.getInt(profileCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_DEFAULT)) == 1;

                // filter for the profile
                Cursor filterCursor = dbHelper.getProfileFilter(profileId);
                boolean preferBus = true;
                boolean preferSubway = true;
                boolean preferTrain = true;
                int maxWalkingDistance = 1000;

                if (filterCursor != null && filterCursor.moveToFirst()) {
                    preferBus = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_BUS)) == 1;
                    preferSubway = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_SUBWAY)) == 1;
                    preferTrain = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_TRAIN)) == 1;
                    maxWalkingDistance = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MAX_WALKING));
                    filterCursor.close();
                }

                UserProfile profile = new UserProfile(profileName, preferBus, preferSubway, preferTrain, maxWalkingDistance);
                profile.setId(profileId);
                profile.setDefault(isDefault);
                profiles.add(profile);

            } while (profileCursor.moveToNext());

            profileCursor.close();
        }

        return profiles;
    }

    /**
     * Retrieves a specific profile by its ID.
     *
     * @param profileId The ID of the profile to retrieve
     * @return The UserProfile object, or null if not found
     */
    public UserProfile getProfile(long profileId) {
        Cursor profileCursor = dbHelper.getProfileById(profileId);

        if (profileCursor != null && profileCursor.moveToFirst()) {
            String profileName = profileCursor.getString(profileCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PROFILE_NAME));
            boolean isDefault = profileCursor.getInt(profileCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_IS_DEFAULT)) == 1;

            // filters for the profile
            Cursor filterCursor = dbHelper.getProfileFilter(profileId);
            boolean preferBus = true;
            boolean preferSubway = true;
            boolean preferTrain = true;
            int maxWalkingDistance = 1000;

            if (filterCursor != null && filterCursor.moveToFirst()) {
                preferBus = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_BUS)) == 1;
                preferSubway = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_SUBWAY)) == 1;
                preferTrain = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_PREFER_TRAIN)) == 1;
                maxWalkingDistance = filterCursor.getInt(filterCursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_MAX_WALKING));
                filterCursor.close();
            }

            UserProfile profile = new UserProfile(profileName, preferBus, preferSubway, preferTrain, maxWalkingDistance);
            profile.setId(profileId);
            profile.setDefault(isDefault);
            profileCursor.close();
            return profile;
        }

        if (profileCursor != null) {
            profileCursor.close();
        }

        return null;
    }

    /**
     * Gets the default profile for the current user.
     *
     * @return The default UserProfile, or null if no default profile exists
     */
    public UserProfile getDefaultProfile() {
        long defaultProfileId = dbHelper.getDefaultProfileId(userId);
        if (defaultProfileId != -1) {
            return getProfile(defaultProfileId);
        }

        return null;
    }

    /**
     * Updates the filter settings for a profile.
     *
     * @param profileId The ID of the profile
     * @param preferBus Bus preference
     * @param preferSubway Bike preference
     * @param preferTrain Train preference
     * @param maxWalkingDistance Maximum walking distance
     * @return true if the update was successful, false otherwise
     */
    public boolean updateProfileFilter(long profileId, boolean preferBus, boolean preferSubway,
                                       boolean preferTrain, int maxWalkingDistance) {
        return dbHelper.updateProfileFilter(profileId, preferBus, preferSubway, preferTrain, maxWalkingDistance);
    }

    /**
     * Sets a profile as the default.
     *
     * @param profileId The ID of the profile
     * @return true if the operation was successful and false otherwise
     */
    public boolean setDefaultProfile(long profileId) {
        return dbHelper.setDefaultProfile(userId, profileId);
    }

    /**
     * Ensures that at least one default profile exists for the user.
     *
     * @return The ID of the default profile
     */
    public long ensureDefaultProfile() {
        UserProfile defaultProfile = getDefaultProfile();
        if (defaultProfile == null) {
            List<UserProfile> profiles = getAllProfiles();
            if (profiles.isEmpty()) {
                // create a new profile
                long profileId = createProfile("Default Profile", true);
                createFilterForProfile(profileId, true, true, true, 1000);
                return profileId;
            } else {
                // make the first profile default
                setDefaultProfile(profiles.get(0).getId());
                return profiles.get(0).getId();
            }
        }
        return defaultProfile.getId();
    }

    /**
     * Gets the application context.
     *
     * @return The application context
     */
    public Context getContext() {
        return context;
    }

    /**
     * Updates a profile's name and default status.
     *
     * @param profileId The ID of the profile
     * @param profileName The new name for the profile
     * @param isDefault Whether this profile should be set as the default
     * @return true if the update was successful, false otherwise
     */
    public boolean updateProfile(long profileId, String profileName, boolean isDefault) {
        return dbHelper.updateProfile(userId, profileId, profileName, isDefault);
    }

    /**
     * Deletes a profile and its related filter.
     *
     * @param profileId The ID of the profile to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteProfile(long profileId) {
        // default (selected) profile cannot be deleted
        UserProfile profile = getProfile(profileId);
        if (profile != null && profile.isDefault()) {
            Log.w("ProfileSQLiteManager", "Cannot delete default profile");
            return false;
        }

        // filters should be deleted first
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseHelper.TABLE_FILTERS,
                DatabaseHelper.COLUMN_PROFILE_ID + "=?",
                new String[]{String.valueOf(profileId)});

        // delete the profile
        int result = db.delete(DatabaseHelper.TABLE_PROFILES,
                DatabaseHelper.COLUMN_ID + "=?",
                new String[]{String.valueOf(profileId)});

        // sync with Firebase
        try {
            if (result > 0) {
                FirebaseDataManager firebaseManager = new FirebaseDataManager(context, userId);
                firebaseManager.deleteProfileFromFirebase(profileId);
            }
        } catch (Exception e) {
            Log.e("ProfileSQLiteManager", "Error syncing profile deletion with Firebase", e);
        }

        return result > 0;
    }

    /**
     * Creates a profile with a specific ID.
     *
     * @param profileId The specific ID to use for the profile
     * @param profileName The name for the profile
     * @param isDefault Whether this profile should be set as the default
     * @return true if creation was successful, false otherwise
     */
    public boolean createProfileWithId(long profileId, String profileName, boolean isDefault) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COLUMN_ID, profileId);
        values.put(DatabaseHelper.COLUMN_USER_ID, userId);
        values.put(DatabaseHelper.COLUMN_PROFILE_NAME, profileName);
        values.put(DatabaseHelper.COLUMN_IS_DEFAULT, isDefault ? 1 : 0);

        if (isDefault) {
            ContentValues resetValues = new ContentValues();
            resetValues.put(DatabaseHelper.COLUMN_IS_DEFAULT, 0);
            db.update(DatabaseHelper.TABLE_PROFILES, resetValues,
                    DatabaseHelper.COLUMN_USER_ID + "=?", new String[]{userId});
        }

        return db.insert(DatabaseHelper.TABLE_PROFILES, null, values) > 0;
    }

}