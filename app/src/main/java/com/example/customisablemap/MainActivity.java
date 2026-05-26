package com.example.customisablemap;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.core.view.ViewCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * The first page that the application displays to the user.
 * MainActivity is a splash screen that handles initial authentication,
 * Firebase initialization, and user profile syncing before moving to MapActivity.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long SPLASH_DELAY = 1500; // 1.5 seconds
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private ImageView appLogo;
    private TextView appName;

    /**
     * Initializes the activity, sets up Firebase, animations, and handles navigation after the splash delay
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // enable Firebase persistence
        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            FirebaseDatabase.getInstance().getReference().keepSynced(true);
        } catch (Exception e) {
            Log.e(TAG, "Firebase persistence setup error", e);
        }

        setContentView(R.layout.activity_main);

        // views for transitions
        appLogo = findViewById(R.id.app_logo);
        appName = findViewById(R.id.app_name);

        // logo animation
        startLogoAnimation();

        // making sure Google Play Services is operational
        if (!checkGooglePlayServices()) {
            Toast.makeText(this, "Google Play Services are required for this app", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Log.d(TAG, "onCreate: Starting MainActivity initialization");

            // Firebase components
            mAuth = FirebaseAuth.getInstance();
            mDatabase = FirebaseDatabase.getInstance().getReference();

            // check current user
            FirebaseUser currentUser = mAuth.getCurrentUser();
            Log.d(TAG, "onCreate: Current user: " + (currentUser != null ? currentUser.getUid() : "null"));

            // back press callback
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finishAffinity();
                }
            });

            // delay to give time for the splash screen to be visible
            new Handler().postDelayed(() -> {
                if (currentUser != null) {
                    String userId = currentUser.getUid();
                    synchronizeProfilesAndNavigate(userId);
                } else {
                    navigateToLogin();
                }
            }, SPLASH_DELAY);

        } catch (Exception e) {
            Log.e(TAG, "Fatal error in MainActivity", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Creates and starts the logo animation sequence.
     */
    private void startLogoAnimation() {
        // fade in animation
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(800);
        fadeIn.setFillAfter(true);

        // animation listener
        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                // a pulse animation after the fade in
                AlphaAnimation pulse = new AlphaAnimation(1.0f, 0.8f);
                pulse.setDuration(600);
                pulse.setRepeatCount(Animation.INFINITE);
                pulse.setRepeatMode(Animation.REVERSE);
                appLogo.startAnimation(pulse);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        appLogo.startAnimation(fadeIn);
        appName.startAnimation(fadeIn);
    }

    /**
     * Syncs user profiles between Firebase and local database before navigation.
     *
     * @param userId The ID of the user
     */
    private void synchronizeProfilesAndNavigate(String userId) {
        try {
            // authenticate user
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser == null || !currentUser.getUid().equals(userId)) {
                Log.e(TAG, "Authentication mismatch when synchronizing profiles");
                navigateToLogin();
                return;
            }

            FirebaseDataManager firebaseManager = new FirebaseDataManager(MainActivity.this, userId);
            ProfileSQLiteManager profileManager = new ProfileSQLiteManager(MainActivity.this, userId);

            // making sure there is a default profile locally
            long defaultId = profileManager.ensureDefaultProfile();
            Log.d(TAG, "Default profile created/found with ID: " + defaultId);

            // attempt sync and pass a callback
            firebaseManager.syncFirebaseToLocalBackground(new FirebaseDataManager.SyncCallback() {
                @Override
                public void onSyncComplete() {
                    Log.d(TAG, "Background sync completed successfully");
                }

                @Override
                public void onSyncError(String errorMessage) {
                    Log.w(TAG, "Background sync had an error: " + errorMessage + " - continuing with local data");
                }
            });

            // navigate to MapActivity
            navigateToMapActivity(userId, String.valueOf(defaultId));
        } catch (Exception e) {
            Log.e(TAG, "Error in synchronizeProfilesAndNavigate", e);
            // navigating anyways
            navigateToMapActivity(userId, "default");
        }
    }

    /**
     * Navigates to the login activity with a shared element transition animation.
     */
    private void navigateToLogin() {
        try {
            Log.d(TAG, "navigateToLogin: Starting LoginActivity");

            // transition animation using shared elements (app logo and name)
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    Pair.create(appLogo, ViewCompat.getTransitionName(appLogo)),
                    Pair.create(appName, ViewCompat.getTransitionName(appName))
            );

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent, options.toBundle());

            // not calling finish() immediately to allow the transition to complete
            new Handler().postDelayed(this::finish, 300);

        } catch (Exception e) {
            Log.e(TAG, "navigateToLogin: Error", e);
            Toast.makeText(this, "Login navigation error: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // regular transition if the other one fails
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
        }
    }

    /**
     * Navigates to the map activity with fade transition animation.
     *
     * @param userId The ID of the user
     * @param defaultProfileId The ID of the default profile to use initially
     */
    private void navigateToMapActivity(String userId, String defaultProfileId) {
        try {
            Log.d(TAG, "navigateToMapActivity: Preparing to navigate to MapActivity");

            Intent intent = new Intent(MainActivity.this, MapActivity.class);

            // necessary extras
            intent.putExtra("USER_ID", userId);
            if (defaultProfileId != null) {
                intent.putExtra("DEFAULT_PROFILE", defaultProfileId);
            }

            // slide-up transition
            ActivityOptionsCompat options = ActivityOptionsCompat.makeCustomAnimation(
                    MainActivity.this,
                    android.R.anim.fade_in,
                    android.R.anim.fade_out
            );

            Log.d(TAG, "navigateToMapActivity: Starting activity");
            startActivity(intent, options.toBundle());

            // not calling finish() immediately to allow the transition to complete
            new Handler().postDelayed(this::finish, 300);

        } catch (Exception e) {
            Log.e(TAG, "navigateToMapActivity: Error", e);
            Toast.makeText(this, "Error navigating to Map: " + e.getMessage(), Toast.LENGTH_LONG).show();

            // regular navigation
            Intent intent = new Intent(MainActivity.this, MapActivity.class);
            intent.putExtra("USER_ID", userId);
            if (defaultProfileId != null) {
                intent.putExtra("DEFAULT_PROFILE", defaultProfileId);
            }
            startActivity(intent);
            finish();
        }
    }

    /**
     * Checks whether Google Play Services are available on the device.
     *
     * @return true if Google Play Services are available, false otherwise
     */
    private boolean checkGooglePlayServices() {
        GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
        int result = googleAPI.isGooglePlayServicesAvailable(this);
        if (result != ConnectionResult.SUCCESS) {
            if (googleAPI.isUserResolvableError(result)) {
                googleAPI.getErrorDialog(this, result, 1000).show();
            }
            return false;
        }
        return true;
    }
}