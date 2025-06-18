package com.example.todolist;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginFragment extends Fragment {

    private TextInputEditText emailEditText, passwordEditText;
    private TextInputLayout emailLayout, passwordLayout;
    private MaterialButton loginButton;
    private TextView forgotPasswordText, signUpRedirectText;
    private FirebaseAuth mAuth;
    private boolean isLoading = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_in, container, false);

        initViews(view);
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        emailEditText = view.findViewById(R.id.email);
        passwordEditText = view.findViewById(R.id.password);
        loginButton = view.findViewById(R.id.login_button);
        forgotPasswordText = view.findViewById(R.id.forgot_password);

        // Find TextInputLayouts by traversing parent hierarchy
        View parent = (View) emailEditText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        emailLayout = (TextInputLayout) parent;

        parent = (View) passwordEditText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        passwordLayout = (TextInputLayout) parent;
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> {
            if (!isLoading) {
                validateAndSignIn();
            }
        });

        forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
    }

    private void validateAndSignIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        // Reset previous errors
        if (emailLayout != null) emailLayout.setError(null);
        if (passwordLayout != null) passwordLayout.setError(null);

        boolean isValid = true;

        // Email validation
        if (TextUtils.isEmpty(email)) {
            if (emailLayout != null) emailLayout.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (emailLayout != null) emailLayout.setError("Please enter a valid email address");
            isValid = false;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            if (passwordLayout != null) passwordLayout.setError("Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            if (passwordLayout != null) passwordLayout.setError("Password must be at least 6 characters");
            isValid = false;
        }

        if (isValid) {
            signInUser(email, password);
        }
    }

    private void signInUser(String email, String password) {
        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // On successful login, navigate to the main app (HomeActivity)
                            navigateToMainApp();
                        }
                    } else {
                        String errorMessage = "Authentication failed";
                        if (task.getException() != null) {
                            String exceptionMessage = task.getException().getMessage();
                            if (exceptionMessage != null) {
                                if (exceptionMessage.contains("no user record") ||
                                        exceptionMessage.contains("user-not-found")) {
                                    errorMessage = "No account found with this email";
                                } else if (exceptionMessage.contains("wrong-password")) {
                                    errorMessage = "Incorrect password";
                                } else if (exceptionMessage.contains("user-disabled")) {
                                    errorMessage = "This account has been disabled";
                                } else if (exceptionMessage.contains("too-many-requests")) {
                                    errorMessage = "Too many failed attempts. Please try again later";
                                } else if (exceptionMessage.contains("invalid-email")) {
                                    errorMessage = "Invalid email address";
                                }
                            }
                        }
                        showToast(errorMessage);
                    }
                });
    }

    private void handleForgotPassword() {
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            if (emailLayout != null) emailLayout.setError("Please enter your email address");
            emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (emailLayout != null) emailLayout.setError("Please enter a valid email address");
            emailEditText.requestFocus();
            return;
        }

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        showToast("Password reset email sent to " + email);
                    } else {
                        String errorMessage = "Failed to send reset email";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            if (task.getException().getMessage().contains("user-not-found")) {
                                errorMessage = "No account found with this email address";
                            }
                        }
                        showToast(errorMessage);
                    }
                });
    }

    /**
     * This method handles the navigation to HomeActivity.
     * It creates an Intent, clears the activity stack, and starts HomeActivity.
     */
    private void navigateToMainApp() {
        try {
            // This is the line that moves the user to HomeActivity
            Intent intent = new Intent(getActivity(), HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish(); // Finish the current activity
            }
        } catch (Exception e) {
            // Fallback in case HomeActivity doesn't exist
            showToast("Login successful!");
        }
    }

    private void setLoading(boolean loading) {
        isLoading = loading;
        if (loginButton != null) {
            loginButton.setEnabled(!loading);
            loginButton.setText(loading ? "Signing In..." : "Sign In");
        }

        // Disable input fields during loading
        if (emailEditText != null) emailEditText.setEnabled(!loading);
        if (passwordEditText != null) passwordEditText.setEnabled(!loading);
    }

    private void showToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is already signed in and navigate accordingly
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            navigateToMainApp();
        }
    }
}