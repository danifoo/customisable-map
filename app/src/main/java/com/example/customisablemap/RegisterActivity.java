package com.example.customisablemap;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity that handles user registration functionality.
 */
public class RegisterActivity extends AppCompatActivity {
    private EditText nameEditText;
    private EditText emailEditText;
    private EditText passwordEditText;
    private EditText confirmPasswordEditText;
    private Button registerButton;
    private TextView loginTextView;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    /**
     * Initializes the activity, sets up UI components, and configures Firebase.
     *
     * @param savedInstanceState For when the activity is being re-initialized after previously being shut down.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Firebase Database
        mDatabase = FirebaseDatabase.getInstance().getReference();

        // UI elements
        nameEditText = findViewById(R.id.name);
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        confirmPasswordEditText = findViewById(R.id.confirm_password);
        registerButton = findViewById(R.id.register_button);
        loginTextView = findViewById(R.id.login_text);
        progressBar = findViewById(R.id.progress_bar);

        // listener for register button
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerUser();
            }
        });

        // listener for login text
        loginTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Handles the user registration process.
     */
    private void registerUser() {

        Log.d("RegisterActivity", "Register button clicked");
        progressBar.setVisibility(View.VISIBLE);

        final String username = nameEditText.getText().toString().trim();
        final String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // input validation
        if (TextUtils.isEmpty(username)) {
            progressBar.setVisibility(View.GONE);
            nameEditText.setError("Username is required");
            nameEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email)) {
            progressBar.setVisibility(View.GONE);
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            progressBar.setVisibility(View.GONE);
            emailEditText.setError("Please provide a valid email");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            progressBar.setVisibility(View.GONE);
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        if (password.length() < 6) {
            progressBar.setVisibility(View.GONE);
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return;
        }

        if (!password.equals(confirmPassword)) {
            progressBar.setVisibility(View.GONE);
            confirmPasswordEditText.setError("Passwords don't match");
            confirmPasswordEditText.requestFocus();
            return;
        }
        Log.d("RegisterActivity", "passed the checks");

        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Account Created.", Toast.LENGTH_SHORT).show();
                    // user creation successful
                    Log.d("RegisterActivity", "Authentication successful");
                    FirebaseUser user = mAuth.getCurrentUser();
                    String userId = user.getUid();

                    // update profile with display name
                    UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder().setDisplayName(username).build();

                    user.updateProfile(profileUpdates);

                    // save username to index
                    mDatabase.child("usernames").child(username).setValue(userId);

                    // create User
                    User userObj = new User(username, email);

                    // save user to database
                    mDatabase.child("users").child(userId).setValue(userObj).addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            if (task.isSuccessful()) {
                                // default profile
                                createDefaultProfile(userId);

                                progressBar.setVisibility(View.GONE);
                                Toast.makeText(RegisterActivity.this, "Account created successfully", Toast.LENGTH_SHORT).show();

                                // going to main activity
                                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            } else {
                                progressBar.setVisibility(View.GONE);
                                Log.e("RegisterActivity", "Failed to save user data: " + (task.getException() != null ? task.getException().getMessage() : "Unknown error"));
                                Toast.makeText(RegisterActivity.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } else {
                    // in the case of sign in failure, user will be informed
                    String errorMessage = task.getException() != null ?
                            task.getException().getMessage() : "Unknown error";
                    Log.e("RegisterActivity", "Authentication failed: " + errorMessage);
                    Toast.makeText(RegisterActivity.this,
                            "Sign-in failed!", Toast.LENGTH_LONG).show();
                }
            }
        });

    }

    /**
     * Creates a default transportation profile for a newly registered user.
     * <p>
     * This method creates a profile with ID 1 in both Firebase and the local
     * SQLite database. The default profile includes standard transportation
     * preferences (bus, subway, train enabled with 1000m max walking distance).
     * </p>
     *
     * @param userId The ID of the newly registered user
     */
    private void createDefaultProfile(String userId) {
        try {
            // create default profile with numeric ID
            long defaultProfileId = 1;

            Map<String, Object> defaultProfile = new HashMap<>();
            defaultProfile.put("profileName", "Default Profile");
            defaultProfile.put("preferBus", true);
            defaultProfile.put("preferSubway", true);
            defaultProfile.put("preferTrain", true);
            defaultProfile.put("maxWalkingDistance", 1000);
            defaultProfile.put("isDefault", true);

            // making sure the key is a string in Firebase
            mDatabase.child("profiles").child(userId).child(String.valueOf(defaultProfileId)).setValue(defaultProfile);

            // creating SQLite profile with matching ID
            ProfileSQLiteManager profileManager = new ProfileSQLiteManager(this, userId);
            profileManager.createProfileWithId(defaultProfileId, "Default Profile", true);
            profileManager.createFilterForProfile(defaultProfileId, true, true, true, 1000);

            // delayed transition
            new Handler().postDelayed(() -> {
                // hide progress bar
                if (progressBar != null) progressBar.setVisibility(View.GONE);

                // Navigate to main activity
                Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }, 150);
        } catch (Exception e) {
            Log.e("RegisterActivity", "Error creating default profile", e);
        }
    }
}