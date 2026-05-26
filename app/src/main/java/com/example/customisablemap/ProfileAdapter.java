package com.example.customisablemap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying and managing user profiles in a RecyclerView.
 */
public class ProfileAdapter extends RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder> {
    private final List<UserProfile> profiles;
    private final ProfileClickListener profileClickListener;
    private final ProfileDefaultListener profileDefaultListener;
    private final ProfileDeleteListener profileDeleteListener;

    /**
     * Creates a new ProfileAdapter.
     *
     * @param profiles List of profiles to show
     * @param profileClickListener Callback for when a profile is clicked
     * @param profileDefaultListener Callback for when a profile is set as selected
     * @param profileDeleteListener Callback for when a profile is deleted
     */
    public ProfileAdapter(List<UserProfile> profiles,
                          ProfileClickListener profileClickListener,
                          ProfileDefaultListener profileDefaultListener,
                          ProfileDeleteListener profileDeleteListener) {
        this.profiles = profiles;
        this.profileClickListener = profileClickListener;
        this.profileDefaultListener = profileDefaultListener;
        this.profileDeleteListener = profileDeleteListener;
    }

    /**
     * Creates a new ViewHolder by inflating the profile item layout.
     *
     * @param parent The parent ViewGroup
     * @param viewType The type of view to create
     * @return A new ProfileViewHolder
     */
    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_profile, parent, false);
        return new ProfileViewHolder(view);
    }

    /**
     * Binds profile data to the ViewHolder at the specified position.
     *
     * @param holder The ViewHolder to bind data to
     * @param position The position of the item in the dataset
     */
    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        UserProfile profile = profiles.get(position);
        holder.bind(profile, profileClickListener, profileDefaultListener, profileDeleteListener);
    }

    /**
     * Returns the total number of profiles in the dataset.
     *
     * @return The number of profiles
     */
    @Override
    public int getItemCount() {
        return profiles.size();
    }

    /**
     * Interface for handling profile click events.
     */
    public interface ProfileClickListener {
        /**
         * Called when a profile is clicked.
         *
         * @param profile The profile that was clicked
         */
        void onProfileClick(UserProfile profile);
    }

    /**
     * Interface for handling setting a profile as the default.
     */
    public interface ProfileDefaultListener {
        /**
         * Called when a profile is set as the default.
         *
         * @param profile The profile to set as default
         */
        void onSetDefault(UserProfile profile);
    }

    /**
     * Interface for handling profile deletion events.
     */
    public interface ProfileDeleteListener {
        /**
         * Called when a profile is to be deleted.
         *
         * @param profile The profile to delete
         */
        void onDeleteProfile(UserProfile profile);
    }

    /**
     * ViewHolder class for profile items.
     */
    static class ProfileViewHolder extends RecyclerView.ViewHolder {
        private final TextView profileNameTextView;
        private final CheckBox defaultCheckBox;
        private final TextView transportModesTextView;
        private final Button deleteButton;

        /**
         * Creates a new ProfileViewHolder.
         *
         * @param itemView The profile item view
         */
        public ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            profileNameTextView = itemView.findViewById(R.id.profile_name);
            defaultCheckBox = itemView.findViewById(R.id.default_checkbox);
            transportModesTextView = itemView.findViewById(R.id.transport_modes);
            deleteButton = itemView.findViewById(R.id.delete_profile_button);
        }

        /**
         * Binds profile data to the ViewHolder and sets up interaction listeners.
         *
         * @param profile The profile to display
         * @param profileClickListener Callback for profile click
         * @param profileDefaultListener Callback for setting default profile
         * @param profileDeleteListener Callback for profile deletion
         */
        public void bind(UserProfile profile,
                         ProfileClickListener profileClickListener,
                         ProfileDefaultListener profileDefaultListener,
                         ProfileDeleteListener profileDeleteListener) {
            profileNameTextView.setText(profile.getProfileName());
            defaultCheckBox.setChecked(profile.isDefault());

            // show active transport modes
            StringBuilder modesText = new StringBuilder("Modes: ");
            if (profile.isPreferBus()) modesText.append("Bus ");
            if (profile.isPreferSubway()) modesText.append("Subway ");
            if (profile.isPreferTrain()) modesText.append("Train ");
            modesText.append("(").append(profile.getMaxWalkingDistance()).append("m max walk)");
            transportModesTextView.setText(modesText.toString());

            // click listener on all of the item
            itemView.setOnClickListener(v -> profileClickListener.onProfileClick(profile));

            // checkbox listener
            defaultCheckBox.setOnClickListener(v -> {
                if (defaultCheckBox.isChecked()) {
                    profileDefaultListener.onSetDefault(profile);
                }
            });

            if (profile.isDefault()) {
                // delete button click (disabled for default profile)
                deleteButton.setEnabled(false);
                deleteButton.setAlpha(0.5f);
            } else {
                deleteButton.setEnabled(true);
                deleteButton.setAlpha(1.0f);
                deleteButton.setOnClickListener(v -> profileDeleteListener.onDeleteProfile(profile));
            }
        }
    }
}