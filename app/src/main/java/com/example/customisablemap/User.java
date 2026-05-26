package com.example.customisablemap;

/**
 * Represents a user of the application.
 */
public class User {
    private String name;
    private String email;

    /**
     * Default constructor required for Firebase.
     */
    public User() {
    }

    /**
     * Creates a new User with the specified name and email.
     *
     * @param name The display name of the user
     * @param email The email address of the user
     */
    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    /**
     * Gets the user's display name.
     *
     * @return The name of the user
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's display name.
     *
     * @param name The new name for the user
     */
    public void setName(String name) {
        this.name = name;
    }
}