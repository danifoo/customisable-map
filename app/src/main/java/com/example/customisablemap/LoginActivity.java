package com.example.customisablemap;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Activity that handles user login functionality.
 */
public class LoginActivity extends AppCompatActivity {
    private EditText emailEditText;
    private EditText passwordEditText;
    private Button loginButton;
    private Button registerButton;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;

    /**
     * Initializes the activity, sets up UI components and configures Firebase.
     *
     * @param savedInstanceState Used when activity is being re-initialized after previously
     *                           being shut down.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // UI elements
        emailEditText = findViewById(R.id.email);
        passwordEditText = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.register_button);
        progressBar = findViewById(R.id.progress_bar);

        // listener for login button
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginUser();
            }
        });

        // listener for register text
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }

    /**
     * Checks if a user is already signed in when the activity starts.
     * If a user is signed in, redirects them to MainActivity.
     */
    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // the user is already logged in and should be redirected to MainActivity
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }

    /**
     * Authenticates the user with email and password using Firebase Authentication.
     * This method validates user input, attempts to sign in the user, and on success,
     * syncs user profiles from Firebase to local storage.
     */
    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // input validation
        if (TextUtils.isEmpty(email)) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return;
        }

        // show progress bar
        progressBar.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    // successfully signed in
                    FirebaseUser user = mAuth.getCurrentUser();
                    Toast.makeText(LoginActivity.this, "Login successful", Toast.LENGTH_SHORT).show();

                    // sync profiles from Firebase to local
                    final FirebaseDataManager firebaseManager = new FirebaseDataManager(
                            LoginActivity.this, user.getUid());

                    // progress bar for data sync
                    progressBar.setVisibility(View.VISIBLE);

                    firebaseManager.syncFirebaseToLocal(new FirebaseDataManager.SyncCallback() {
                        /**
                         * Called when profile sync completes successfully and navigates to MainActivity after completion.
                         */
                        @Override
                        public void onSyncComplete() {
                            progressBar.setVisibility(View.GONE);

                            // go to MainActivity after sync
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);

                            // clear back stack so user can't go back to login screen
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }

                        /**
                         * Called when profile sync encounters an error.
                         * Navigates to MainActivity with the available local data.
                         *
                         * @param errorMessage The error message
                         */
                        @Override
                        public void onSyncError(String errorMessage) {
                            progressBar.setVisibility(View.GONE);
                            // sync failed, use local data
                            Toast.makeText(LoginActivity.this,
                                    "Profiles sync error: " + errorMessage + ". Using local data.",
                                    Toast.LENGTH_SHORT).show();

                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    });
                } else {
                    // login failure
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(LoginActivity.this, "Authentication failed: " +
                            task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}