package com.example.todolist;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;

public class LoginFragment extends Fragment {

    private static final String TAG = "LoginFragment";
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
        emailLayout = findTextInputLayout(emailEditText);
        passwordLayout = findTextInputLayout(passwordEditText);
    }

    private TextInputLayout findTextInputLayout(View editText) {
        if (editText == null) return null;
        View parent = (View) editText.getParent();
        while (parent != null && !(parent instanceof TextInputLayout)) {
            parent = (View) parent.getParent();
        }
        return (TextInputLayout) parent;
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> {
            if (!isLoading) {
                // Check network connectivity first
                if (!isNetworkAvailable()) {
                    showToast("No internet connection. Please check your network settings.");
                    return;
                }
                validateAndSignIn();
            }
        });

        forgotPasswordText.setOnClickListener(v -> handleForgotPassword());
    }

    private boolean isNetworkAvailable() {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager)
                    getContext().getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager != null) {
                NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
                return activeNetworkInfo != null && activeNetworkInfo.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
        }
        return false;
    }

    private void validateAndSignIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        Log.d(TAG, "Attempting to sign in with email: " + email);

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
        Log.d(TAG, "Starting sign in process");
        setLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    setLoading(false);

                    if (task.isSuccessful()) {
                        Log.d(TAG, "Sign in successful");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "User authenticated: " + user.getEmail());
                            navigateToMainApp();
                        } else {
                            Log.e(TAG, "User is null after successful authentication");
                            showToast("Authentication error. Please try again.");
                        }
                    } else {
                        Log.e(TAG, "Sign in failed", task.getException());
                        String errorMessage = getFirebaseErrorMessage(task.getException());
                        showToast(errorMessage);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Sign in failed with exception", e);
                    setLoading(false);
                    String errorMessage = getFirebaseErrorMessage(e);
                    showToast(errorMessage);
                });
    }

    private String getFirebaseErrorMessage(Exception exception) {
        if (exception == null) return "Authentication failed. Please try again.";

        String exceptionMessage = exception.getMessage();
        Log.e(TAG, "Firebase auth error: " + exceptionMessage);

        // Handle specific Firebase exceptions
        if (exception instanceof FirebaseNetworkException) {
            return "Network error. Please check your internet connection and try again.";
        }

        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authException = (FirebaseAuthException) exception;
            String errorCode = authException.getErrorCode();

            switch (errorCode) {
                case "ERROR_USER_NOT_FOUND":
                    return "No account found with this email address. Please check your email or sign up.";
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_INVALID_CREDENTIAL":
                    return "Incorrect email or password. Please check your credentials.";
                case "ERROR_USER_DISABLED":
                    return "This account has been disabled. Please contact support.";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Too many failed attempts. Please try again later.";
                case "ERROR_INVALID_EMAIL":
                    return "Invalid email address format.";
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Network error. Please check your internet connection and try again.";
                default:
                    return "Sign in failed: " + authException.getMessage();
            }
        }

        if (exceptionMessage != null) {
            if (exceptionMessage.contains("no user record") ||
                    exceptionMessage.contains("user-not-found")) {
                return "No account found with this email address. Please check your email or sign up.";
            } else if (exceptionMessage.contains("wrong-password") ||
                    exceptionMessage.contains("invalid-credential")) {
                return "Incorrect email or password. Please check your credentials.";
            } else if (exceptionMessage.contains("user-disabled")) {
                return "This account has been disabled. Please contact support.";
            } else if (exceptionMessage.contains("too-many-requests")) {
                return "Too many failed attempts. Please try again later.";
            } else if (exceptionMessage.contains("invalid-email")) {
                return "Invalid email address format.";
            } else if (exceptionMessage.contains("network") || exceptionMessage.contains("timeout")) {
                return "Network error. Please check your internet connection and try again.";
            } else if (exceptionMessage.contains("email-already-in-use")) {
                return "This email is already registered. Please sign in instead.";
            }
        }

        return "Sign in failed: " + (exceptionMessage != null ? exceptionMessage : "Unknown error");
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

        // Check network connectivity
        if (!isNetworkAvailable()) {
            showToast("No internet connection. Please check your network settings.");
            return;
        }

        Log.d(TAG, "Sending password reset email to: " + email);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Password reset email sent successfully");
                        showToast("Password reset email sent to " + email);
                        // Clear the error if it was set
                        if (emailLayout != null) emailLayout.setError(null);
                    } else {
                        Log.e(TAG, "Failed to send password reset email", task.getException());
                        String errorMessage = "Failed to send reset email";
                        if (task.getException() != null && task.getException().getMessage() != null) {
                            if (task.getException().getMessage().contains("user-not-found")) {
                                errorMessage = "No account found with this email address";
                            } else if (task.getException().getMessage().contains("network")) {
                                errorMessage = "Network error. Please check your internet connection.";
                            }
                        }
                        showToast(errorMessage);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Password reset failed", e);
                    String errorMessage = getFirebaseErrorMessage(e);
                    showToast(errorMessage);
                });
    }

    /**
     * This method handles the navigation to HomeActivity.
     * It creates an Intent, clears the activity stack, and starts HomeActivity.
     */
    private void navigateToMainApp() {
        try {
            Log.d(TAG, "Navigating to HomeActivity");
            Intent intent = new Intent(getActivity(), HomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) {
                getActivity().finish();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to navigate to HomeActivity", e);
            showToast("Login successful! Welcome back.");
        }
    }

    private void setLoading(boolean loading) {
        Log.d(TAG, "Setting loading state to: " + loading);
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
        Log.d(TAG, "Showing toast: " + message);
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is already signed in and navigate accordingly
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: " + currentUser.getEmail());
            navigateToMainApp();
        }
    }
}