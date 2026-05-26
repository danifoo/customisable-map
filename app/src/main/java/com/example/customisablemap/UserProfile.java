package com.example.customisablemap;

/**
 * Represents a user's transportation preference profile.
 */
public class UserProfile {
    private long id;
    private String profileName;
    private boolean preferBus;
    private boolean preferSubway;
    private boolean preferTrain;
    private int maxWalkingDistance;
    private boolean isDefault;

    /**
     * Default constructor required for Firebase.
     */
    public UserProfile() {
    }

    /**
     * Creates a new UserProfile with the specified preferences.
     *
     * @param profileName Name of the profile
     * @param preferBus Whether bus transportation is preferred
     * @param preferSubway Whether subway transportation is preferred
     * @param preferTrain Whether train transportation is preferred
     * @param maxWalkingDistance Maximum walking distance in meters
     */
    public UserProfile(String profileName, boolean preferBus,
                       boolean preferSubway, boolean preferTrain,
                       int maxWalkingDistance) {
        this.profileName = profileName;
        this.preferBus = preferBus;
        this.preferSubway = preferSubway;
        this.preferTrain = preferTrain;
        this.maxWalkingDistance = maxWalkingDistance;
    }

    /**
     * Gets the unique identifier of the profile.
     *
     * @return The profile ID
     */
    public long getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the profile.
     *
     * @param id The profile ID to set
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * Gets the name of the profile.
     *
     * @return The profile name
     */
    public String getProfileName() {
        return profileName;
    }

    /**
     * Checks if bus transportation is preferred.
     *
     * @return true if bus transportation is preferred, false otherwise
     */
    public boolean isPreferBus() {
        return preferBus;
    }

    /**
     * Sets the preference for bus transportation.
     *
     * @param preferBus true to prefer bus transportation, false otherwise
     */
    public void setPreferBus(boolean preferBus) {
        this.preferBus = preferBus;
    }

    /**
     * Checks if subway transportation is preferred.
     *
     * @return true if subway transportation is preferred, false otherwise
     */
    public boolean isPreferSubway() {
        return preferSubway;
    }

    /**
     * Sets the preference for subway transportation.
     *
     * @param preferSubway true to prefer subway transportation, false otherwise
     */
    public void setPreferSubway(boolean preferSubway) {
        this.preferSubway = preferSubway;
    }

    /**
     * Checks if train transportation is preferred.
     *
     * @return true if train transportation is preferred, false otherwise
     */
    public boolean isPreferTrain() {
        return preferTrain;
    }

    /**
     * Sets the preference for train transportation.
     *
     * @param preferTrain true to prefer train transportation, false otherwise
     */
    public void setPreferTrain(boolean preferTrain) {
        this.preferTrain = preferTrain;
    }

    /**
     * Gets the maximum walking distance.
     *
     * @return The maximum walking distance in meters
     */
    public int getMaxWalkingDistance() {
        return maxWalkingDistance;
    }

    /**
     * Sets the maximum walking distance.
     *
     * @param maxWalkingDistance The maximum walking distance in meters
     */
    public void setMaxWalkingDistance(int maxWalkingDistance) {
        this.maxWalkingDistance = maxWalkingDistance;
    }

    /**
     * Checks if this is the default profile.
     *
     * @return true if this is the default profile, false otherwise
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Sets whether this is the default profile.
     *
     * @param isDefault true to set as default profile, false otherwise
     */
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}