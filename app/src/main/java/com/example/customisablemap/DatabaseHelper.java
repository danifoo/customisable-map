package com.example.customisablemap;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * DatabaseHelper manages the SQLite database operations for the application.
 * It handles creating and upgrading the database schema, also
 * providing methods to interact with user profiles and transportation filters.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "customisablemap.db";
    private static final int DATABASE_VERSION = 1;
    public static final String TABLE_PROFILES = "profiles";
    public static final String TABLE_FILTERS = "filters";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_USER_ID = "user_id";
    public static final String COLUMN_PROFILE_NAME = "profile_name";
    public static final String COLUMN_IS_DEFAULT = "is_default";
    public static final String COLUMN_PROFILE_ID = "profile_id";
    public static final String COLUMN_PREFER_BUS = "prefer_bus";
    public static final String COLUMN_PREFER_SUBWAY = "prefer_subway";
    public static final String COLUMN_PREFER_TRAIN = "prefer_train";
    public static final String COLUMN_MAX_WALKING = "max_walking_distance";

    // SQL statement to create the profiles table
    private static final String CREATE_TABLE_PROFILES = "CREATE TABLE " + TABLE_PROFILES + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_USER_ID + " TEXT NOT NULL,"
            + COLUMN_PROFILE_NAME + " TEXT NOT NULL,"
            + COLUMN_IS_DEFAULT + " INTEGER DEFAULT 0"
            + ")";

    // SQL statement to create the filters table with foreign key constraint
    private static final String CREATE_TABLE_FILTERS = "CREATE TABLE " + TABLE_FILTERS + "("
            + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_PROFILE_ID + " INTEGER NOT NULL,"
            + COLUMN_PREFER_BUS + " INTEGER DEFAULT 1,"
            + COLUMN_PREFER_SUBWAY + " INTEGER DEFAULT 1,"
            + COLUMN_PREFER_TRAIN + " INTEGER DEFAULT 1,"
            + COLUMN_MAX_WALKING + " INTEGER DEFAULT 1000,"
            + "FOREIGN KEY(" + COLUMN_PROFILE_ID + ") REFERENCES " + TABLE_PROFILES + "(" + COLUMN_ID + ")"
            + ")";

    /**
     * Constructor for the DatabaseHelper.
     *
     * @param context The application context which will be used to open and create the database
     */
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Creates the basic tables.
     *
     * @param db The database that is being created
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_PROFILES);
        db.execSQL(CREATE_TABLE_FILTERS);
    }

    /**
     * Called when the database needs to be upgraded to a newer version.
     * Drops existing tables and recreates them.
     *
     * @param db The database instance being upgraded
     * @param oldVersion The older database version
     * @param newVersion The newer database version
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // drop older tables if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FILTERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROFILES);

        // create tables again
        onCreate(db);
    }

    /**
     * Adds a new profile.
     * If the profile is set as default, all other profiles for the user will be set as non-default.
     *
     * @param userId The user ID that owns this profile
     * @param profileName The given name to the profile
     * @param isDefault Whether this profile should be set as the default one
     * @return The row ID of the newly inserted profile, or -1 if an error occurred
     */
    public long addProfile(String userId, String profileName, boolean isDefault) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_USER_ID, userId);
        values.put(COLUMN_PROFILE_NAME, profileName);
        values.put(COLUMN_IS_DEFAULT, isDefault ? 1 : 0);

        // if setting as default, first clear all other defaults
        if (isDefault) {
            ContentValues resetValues = new ContentValues();
            resetValues.put(COLUMN_IS_DEFAULT, 0);
            db.update(TABLE_PROFILES, resetValues, COLUMN_USER_ID + "=?", new String[]{userId});
        }

        return db.insert(TABLE_PROFILES, null, values);
    }

    /**
     * Adds transportation filter settings to an existing profile.
     *
     * @param profileId The ID of the profile to add filters to
     * @param preferBus Bus preference
     * @param preferSubway Bike preference
     * @param preferTrain Train preference
     * @param maxWalkingDistance Maximum walking distance
     * @return The row ID of the newly inserted filter, or -1 if an error occurred
     */
    public long addFilterToProfile(long profileId, boolean preferBus, boolean preferSubway,
                                   boolean preferTrain, int maxWalkingDistance) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROFILE_ID, profileId);
        values.put(COLUMN_PREFER_BUS, preferBus ? 1 : 0);
        values.put(COLUMN_PREFER_SUBWAY, preferSubway ? 1 : 0);
        values.put(COLUMN_PREFER_TRAIN, preferTrain ? 1 : 0);
        values.put(COLUMN_MAX_WALKING, maxWalkingDistance);

        return db.insert(TABLE_FILTERS, null, values);
    }

    /**
     * Finds all profiles for a specific user.
     *
     * @param userId The user ID for finding its profiles
     * @return A Cursor containing profile records
     */
    public Cursor getUserProfiles(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PROFILES,
                new String[]{COLUMN_ID, COLUMN_PROFILE_NAME, COLUMN_IS_DEFAULT},
                COLUMN_USER_ID + "=?",
                new String[]{userId},
                null, null, null);
    }

    /**
     * Finds a specific profile by its ID.
     *
     * @param profileId The profile ID to find
     * @return A Cursor containing the profile record
     */
    public Cursor getProfileById(long profileId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_PROFILES,
                null,
                COLUMN_ID + "=?",
                new String[]{String.valueOf(profileId)},
                null, null, null);
    }

    /**
     * Finds the filter settings for a profile.
     *
     * @param profileId The ID of the profile to find its settings
     * @return A Cursor containing the filter record
     */
    public Cursor getProfileFilter(long profileId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_FILTERS,
                null,
                COLUMN_PROFILE_ID + "=?",
                new String[]{String.valueOf(profileId)},
                null, null, null, "1");
    }

    /**
     * Gets the ID of the default profile for a user.
     *
     * @param userId The user ID to find the default profile for
     * @return The ID of the default profile (-1 if none exists)
     */
    public long getDefaultProfileId(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_PROFILES,
                new String[]{COLUMN_ID},
                COLUMN_USER_ID + "=? AND " + COLUMN_IS_DEFAULT + "=1",
                new String[]{userId},
                null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            long id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID));
            cursor.close();
            return id;
        }

        if (cursor != null) {
            cursor.close();
        }

        // no default profile found, return -1
        return -1;
    }

    /**
     * Updates the filter settings of a profile, or creates new ones if there is none.
     *
     * @param profileId The ID of the profile for filter updating
     * @param preferBus Bus preference
     * @param preferSubway Bike preference
     * @param preferTrain Train preference
     * @param maxWalkingDistance Maximum walking distance (in meters)
     * @return true if the operation succeeded and false otherwise
     */
    public boolean updateProfileFilter(long profileId, boolean preferBus, boolean preferSubway,
                                       boolean preferTrain, int maxWalkingDistance) {
        SQLiteDatabase db = this.getWritableDatabase();

        // check if filter exists
        Cursor cursor = getProfileFilter(profileId);
        boolean filterExists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) cursor.close();

        ContentValues values = new ContentValues();
        values.put(COLUMN_PREFER_BUS, preferBus ? 1 : 0);
        values.put(COLUMN_PREFER_SUBWAY, preferSubway ? 1 : 0);
        values.put(COLUMN_PREFER_TRAIN, preferTrain ? 1 : 0);
        values.put(COLUMN_MAX_WALKING, maxWalkingDistance);

        if (filterExists) {
            // update existing filter
            return db.update(TABLE_FILTERS, values, COLUMN_PROFILE_ID + "=?",
                    new String[]{String.valueOf(profileId)}) > 0;
        } else {
            // create new filter
            values.put(COLUMN_PROFILE_ID, profileId);
            return db.insert(TABLE_FILTERS, null, values) > 0;
        }
    }

    /**
     * Sets a profile as the default for a user and updates all other profiles to be non-default.
     *
     * @param userId The user ID of the profiles
     * @param profileId The ID of the profile to set as default
     * @return true if the operation succeeded and false otherwise
     */
    public boolean setDefaultProfile(String userId, long profileId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // reset all profiles to non-default
        ContentValues resetValues = new ContentValues();
        resetValues.put(COLUMN_IS_DEFAULT, 0);
        db.update(TABLE_PROFILES, resetValues, COLUMN_USER_ID + "=?", new String[]{userId});

        // set the selected profile as default
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_DEFAULT, 1);
        return db.update(TABLE_PROFILES, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(profileId)}) > 0;
    }

    /**
     * Updates a profile's name and default status.
     *
     * @param userId The user ID that owns the profile
     * @param profileId The ID of the profile to update
     * @param profileName The new name of the profile
     * @param isDefault Whether this profile should be set as the default
     * @return true if the operation succeeded and false otherwise
     */
    public boolean updateProfile(String userId, long profileId, String profileName, boolean isDefault) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PROFILE_NAME, profileName);
        values.put(COLUMN_IS_DEFAULT, isDefault ? 1 : 0);

        // if set as default selected, first clear all other defaults
        if (isDefault) {
            ContentValues resetValues = new ContentValues();
            resetValues.put(COLUMN_IS_DEFAULT, 0);
            db.update(TABLE_PROFILES, resetValues, COLUMN_USER_ID + "=?", new String[]{userId});
        }

        return db.update(TABLE_PROFILES, values, COLUMN_ID + "=?",
                new String[]{String.valueOf(profileId)}) > 0;
    }

    /**
     * Deletes a profile and its associated filter settings.
     *
     * @param profileId The ID of the profile to delete
     * @return true if the profile was deleted, false otherwise
     */
    public boolean deleteProfile(long profileId) {
        SQLiteDatabase db = this.getWritableDatabase();

        // deleting the profile's filters first
        db.delete(TABLE_FILTERS, COLUMN_PROFILE_ID + "=?", new String[]{String.valueOf(profileId)});

        return db.delete(TABLE_PROFILES, COLUMN_ID + "=?", new String[]{String.valueOf(profileId)}) > 0;
    }
}