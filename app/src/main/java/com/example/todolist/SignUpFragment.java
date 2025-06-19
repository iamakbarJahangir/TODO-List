package com.example.todolist;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import java.util.HashMap;
import java.util.Map;

public class SignUpFragment extends Fragment {

    private static final String TAG = "SignUpFragment";
    private TextInputEditText nameEditText, usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private TextInputLayout nameLayout, usernameLayout, emailLayout, passwordLayout, confirmPasswordLayout;
    private CheckBox termsCheckBox;
    private MaterialButton submitButton;
    private TextView signInRedirectText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private boolean isLoading = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sign_up, container, false);

        initViews(view);
        setupClickListeners();

        return view;
    }

    private void initViews(View view) {
        nameEditText = view.findViewById(R.id.name);
        usernameEditText = view.findViewById(R.id.username);
        emailEditText = view.findViewById(R.id.email);
        passwordEditText = view.findViewById(R.id.password);
        confirmPasswordEditText = view.findViewById(R.id.confirm_password);

        // Find TextInputLayouts by traversing parent hierarchy
        nameLayout = findTextInputLayout(nameEditText);
        usernameLayout = findTextInputLayout(usernameEditText);
        emailLayout = findTextInputLayout(emailEditText);
        passwordLayout = findTextInputLayout(passwordEditText);
        confirmPasswordLayout = findTextInputLayout(confirmPasswordEditText);

        termsCheckBox = view.findViewById(R.id.terms_checkbox);
        submitButton = view.findViewById(R.id.submit_button);
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
        submitButton.setOnClickListener(v -> {
            if (!isLoading) {
                // Check network connectivity first
                if (!isNetworkAvailable()) {
                    showToast("No internet connection. Please check your network settings.");
                    return;
                }
                validateAndSignUp();
            }
        });
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

    private void validateAndSignUp() {
        String name = nameEditText.getText().toString().trim();
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String confirmPassword = confirmPasswordEditText.getText().toString().trim();

        // Reset previous errors
        clearErrors();

        boolean isValid = true;

        // Name validation
        if (TextUtils.isEmpty(name)) {
            setError(nameLayout, "Full name is required");
            isValid = false;
        } else if (name.length() < 2) {
            setError(nameLayout, "Name must be at least 2 characters");
            isValid = false;
        }

        // Username validation
        if (TextUtils.isEmpty(username)) {
            setError(usernameLayout, "Username is required");
            isValid = false;
        } else if (username.length() < 3) {
            setError(usernameLayout, "Username must be at least 3 characters");
            isValid = false;
        } else if (username.length() > 20) {
            setError(usernameLayout, "Username must be less than 20 characters");
            isValid = false;
        }

        // Email validation
        if (TextUtils.isEmpty(email)) {
            setError(emailLayout, "Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(emailLayout, "Please enter a valid email address");
            isValid = false;
        }

        // Password validation
        if (TextUtils.isEmpty(password)) {
            setError(passwordLayout, "Password is required");
            isValid = false;
        } else if (password.length() < 6) {
            setError(passwordLayout, "Password must be at least 6 characters");
            isValid = false;
        }

        // Confirm password validation
        if (TextUtils.isEmpty(confirmPassword)) {
            setError(confirmPasswordLayout, "Please confirm your password");
            isValid = false;
        } else if (!password.equals(confirmPassword)) {
            setError(confirmPasswordLayout, "Passwords do not match");
            isValid = false;
        }

        // Terms and conditions validation
        if (!termsCheckBox.isChecked()) {
            showToast("Please accept Terms & Conditions");
            isValid = false;
        }

        if (isValid) {
            signUpUser(name, username, email, password);
        }
    }

    private void signUpUser(String name, String username, String email, String password) {
        Log.d(TAG, "Creating user account for email: " + email);
        setLoading(true);

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase Auth account created successfully");
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Update user profile
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        if (profileTask.isSuccessful()) {
                                            Log.d(TAG, "User profile updated successfully");
                                            // Save additional user data to Firestore
                                            saveUserDataToFirestore(user.getUid(), name, username, email);
                                        } else {
                                            Log.e(TAG, "Failed to update profile", profileTask.getException());
                                            handleSignupCompletion("Account created successfully! Please sign in.", false);
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Profile update failed", e);
                                        handleSignupCompletion("Account created successfully! Please sign in.", false);
                                    });
                        } else {
                            Log.e(TAG, "Failed to get user information after account creation");
                            handleSignupCompletion("Account created successfully! Please sign in.", false);
                        }
                    } else {
                        Log.e(TAG, "Failed to create Firebase Auth account", task.getException());
                        setLoading(false);
                        String errorMessage = getFirebaseErrorMessage(task.getException());
                        showToast(errorMessage);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Registration failed with exception", e);
                    setLoading(false);
                    String errorMessage = getFirebaseErrorMessage(e);
                    showToast(errorMessage);
                });
    }

    private void saveUserDataToFirestore(String uid, String name, String username, String email) {
        Log.d(TAG, "Saving user data to Firestore for uid: " + uid);

        Map<String, Object> userData = new HashMap<>();
        userData.put("name", name);
        userData.put("username", username);
        userData.put("email", email);
        userData.put("createdAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .set(userData)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User data saved to Firestore successfully");
                        handleSignupCompletion("Account created successfully! Please sign in.", false);
                    } else {
                        Log.e(TAG, "Failed to save user data to Firestore", task.getException());
                        // Even if Firestore fails, the account was created successfully
                        handleSignupCompletion("Account created successfully! Please sign in.", false);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firestore save failed", e);
                    // Even if Firestore fails, the account was created successfully
                    handleSignupCompletion("Account created successfully! Please sign in.", false);
                });
    }

    private void handleSignupCompletion(String message, boolean hasError) {
        Log.d(TAG, "Handling signup completion: " + message);

        // Always set loading to false first
        setLoading(false);

        // Sign out user after successful registration so they can sign in
        if (mAuth.getCurrentUser() != null && !hasError) {
            mAuth.signOut();
            Log.d(TAG, "User signed out after account creation");
        }

        // Show success message
        showToast(message);

        // Clear form
        clearForm();

        // Switch to sign in tab after a short delay
        if (getView() != null && !hasError) {
            getView().postDelayed(() -> {
                if (getActivity() instanceof MainActivity && !isDetached() && isAdded()) {
                    Log.d(TAG, "Switching to login tab");
                    ((MainActivity) getActivity()).switchToTab(0);
                }
            }, 1500); // 1.5 second delay to show the success message
        }
    }

    private String getFirebaseErrorMessage(Exception exception) {
        if (exception == null) return "Registration failed. Please try again.";

        String exceptionMessage = exception.getMessage();
        Log.e(TAG, "Firebase error: " + exceptionMessage);

        // Handle specific Firebase exceptions
        if (exception instanceof FirebaseNetworkException) {
            return "Network error. Please check your internet connection and try again.";
        }

        if (exception instanceof FirebaseAuthException) {
            FirebaseAuthException authException = (FirebaseAuthException) exception;
            String errorCode = authException.getErrorCode();

            switch (errorCode) {
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return "This email is already registered. Please use a different email or sign in.";
                case "ERROR_WEAK_PASSWORD":
                    return "Password is too weak. Please use a stronger password.";
                case "ERROR_INVALID_EMAIL":
                    return "Invalid email address format.";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "Email/password accounts are not enabled. Please contact support.";
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Network error. Please check your internet connection and try again.";
                default:
                    return "Registration failed: " + authException.getMessage();
            }
        }

        if (exceptionMessage != null) {
            if (exceptionMessage.contains("email-already-in-use")) {
                return "This email is already registered. Please use a different email or sign in.";
            } else if (exceptionMessage.contains("weak-password")) {
                return "Password is too weak. Please use a stronger password.";
            } else if (exceptionMessage.contains("invalid-email")) {
                return "Invalid email address format.";
            } else if (exceptionMessage.contains("operation-not-allowed")) {
                return "Email/password accounts are not enabled. Please contact support.";
            } else if (exceptionMessage.contains("network") || exceptionMessage.contains("timeout")) {
                return "Network error. Please check your internet connection and try again.";
            }
        }
        return "Registration failed: " + (exceptionMessage != null ? exceptionMessage : "Unknown error");
    }

    private void setError(TextInputLayout layout, String error) {
        if (layout != null) {
            layout.setError(error);
        }
    }

    private void clearErrors() {
        setError(nameLayout, null);
        setError(usernameLayout, null);
        setError(emailLayout, null);
        setError(passwordLayout, null);
        setError(confirmPasswordLayout, null);
    }

    private void clearForm() {
        Log.d(TAG, "Clearing form");
        if (nameEditText != null) nameEditText.setText("");
        if (usernameEditText != null) usernameEditText.setText("");
        if (emailEditText != null) emailEditText.setText("");
        if (passwordEditText != null) passwordEditText.setText("");
        if (confirmPasswordEditText != null) confirmPasswordEditText.setText("");
        if (termsCheckBox != null) termsCheckBox.setChecked(false);
        clearErrors();
    }

    private void setLoading(boolean loading) {
        Log.d(TAG, "Setting loading state to: " + loading);
        isLoading = loading;

        if (submitButton != null) {
            submitButton.setEnabled(!loading);
            submitButton.setText(loading ? "Creating Account..." : "Create Account");
        }

        // Disable input fields during loading
        if (nameEditText != null) nameEditText.setEnabled(!loading);
        if (usernameEditText != null) usernameEditText.setEnabled(!loading);
        if (emailEditText != null) emailEditText.setEnabled(!loading);
        if (passwordEditText != null) passwordEditText.setEnabled(!loading);
        if (confirmPasswordEditText != null) confirmPasswordEditText.setEnabled(!loading);
        if (termsCheckBox != null) termsCheckBox.setEnabled(!loading);
    }

    private void showToast(String message) {
        Log.d(TAG, "Showing toast: " + message);
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}