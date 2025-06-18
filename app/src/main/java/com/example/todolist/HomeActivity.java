package com.example.todolist;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HomeActivity extends AppCompatActivity {

    // UI Components
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddTask;
    private ImageButton btnLogout;
    private TextView tvTaskCount;
    private TextView tvWelcomeMessage;
    private LinearLayout emptyState;

    // Firebase components
    private FirebaseAuth firebaseAuth;
    private DatabaseReference databaseReference;
    private FirebaseUser currentUser;

    // RecyclerView components
    private TodoAdapter todoAdapter;
    private List<Task> taskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // Initialize Firebase
        initializeFirebase();

        // Initialize Views
        initializeViews();

        // Setup RecyclerView
        setupRecyclerView();

        // Setup click listeners
        setupClickListeners();

        // Load tasks from Firebase
        loadTasksFromFirebase();

        // Set welcome message
        setWelcomeMessage();
    }

    private void initializeFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();

        if (currentUser == null) {
            // User not logged in, redirect to login
            redirectToLogin();
            return;
        }

        // User ID for Firebase path
        String userId = currentUser.getUid();
        databaseReference = FirebaseDatabase.getInstance().getReference("users").child(userId).child("tasks");
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerView);
        fabAddTask = findViewById(R.id.fab_add_task);
        btnLogout = findViewById(R.id.btn_logout);
        tvTaskCount = findViewById(R.id.tv_task_count);
        tvWelcomeMessage = findViewById(R.id.tv_welcome_message);
        emptyState = findViewById(R.id.empty_state);

        taskList = new ArrayList<>();
    }

    private void setupRecyclerView() {
        todoAdapter = new TodoAdapter(taskList, new TodoAdapter.OnTaskActionListener() {
            @Override
            public void onTaskComplete(Task task, boolean isCompleted) {
                updateTaskCompletion(task.getId(), isCompleted);
            }

            @Override
            public void onTaskEdit(Task task) {
                showEditTaskDialog(task);
            }

            @Override
            public void onTaskDelete(Task task) {
                showDeleteConfirmationDialog(task);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(todoAdapter);
    }

    private void setupClickListeners() {
        fabAddTask.setOnClickListener(v -> showAddTaskDialog());

        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
    }

    private void setWelcomeMessage() {
        if (currentUser != null) {
            String email = currentUser.getEmail();
            String displayName = currentUser.getDisplayName();

            String welcomeText;
            if (displayName != null && !displayName.isEmpty()) {
                welcomeText = "Welcome back, " + displayName + "!";
            } else if (email != null) {
                String name = email.substring(0, email.indexOf("@"));
                welcomeText = "Welcome back, " + name + "!";
            } else {
                welcomeText = "Welcome back!";
            }

            tvWelcomeMessage.setText(welcomeText);
        }
    }

    // CRUD Operations

    // CREATE - Add new task
    private void addTaskToFirebase(String title, String description) {
        if (databaseReference == null) return;

        String taskId = databaseReference.push().getKey();
        if (taskId == null) {
            Toast.makeText(this, "Error creating task", Toast.LENGTH_SHORT).show();
            return;
        }

        Task newTask = new Task(taskId, title, description, false, System.currentTimeMillis());

        databaseReference.child(taskId).setValue(newTask)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task added successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "Failed to add task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // READ - Load tasks from Firebase
    @SuppressLint("SetTextI18n")
    private void loadTasksFromFirebase() {
        if (databaseReference == null) return;

        tvTaskCount.setText("Loading tasks...");

        databaseReference.addValueEventListener(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                taskList.clear();

                for (DataSnapshot taskSnapshot : dataSnapshot.getChildren()) {
                    Task task = taskSnapshot.getValue(Task.class);
                    if (task != null) {
                        taskList.add(task);
                    }
                }

                // Sort tasks by timestamp (newest first)
                taskList.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                todoAdapter.notifyDataSetChanged();
                updateUI();
            }

            @SuppressLint("SetTextI18n")
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(HomeActivity.this, "Failed to load tasks: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
                tvTaskCount.setText("Failed to load tasks");
            }
        });
    }

    // UPDATE - Update task
    private void updateTaskInFirebase(String taskId, String title, String description) {
        if (databaseReference == null) return;

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title);
        updates.put("description", description);
        updates.put("timestamp", System.currentTimeMillis());

        databaseReference.child(taskId).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task updated successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "Failed to update task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // UPDATE - Update task completion status
    private void updateTaskCompletion(String taskId, boolean isCompleted) {
        if (databaseReference == null) return;

        databaseReference.child(taskId).child("completed").setValue(isCompleted)
                .addOnSuccessListener(aVoid -> {
                    String message = isCompleted ? "Task completed!" : "Task marked as incomplete";
                    Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "Failed to update task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // DELETE - Delete task
    private void deleteTaskFromFirebase(String taskId) {
        if (databaseReference == null) return;

        databaseReference.child(taskId).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task deleted successfully!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "Failed to delete task: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Dialog Methods

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);

        TextInputEditText etTaskTitle = dialogView.findViewById(R.id.et_task_title);
        TextInputEditText etTaskDescription = dialogView.findViewById(R.id.et_task_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnAddTask = dialogView.findViewById(R.id.btn_add_task);

        AlertDialog dialog = builder.setView(dialogView).create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAddTask.setOnClickListener(v -> {
            String title = Objects.requireNonNull(etTaskTitle.getText()).toString().trim();
            String description = Objects.requireNonNull(etTaskDescription.getText()).toString().trim();

            if (TextUtils.isEmpty(title)) {
                etTaskTitle.setError("Title is required");
                etTaskTitle.requestFocus();
                return;
            }

            addTaskToFirebase(title, description);
            dialog.dismiss();
        });

        dialog.show();
    }

    @SuppressLint("SetTextI18n")
    private void showEditTaskDialog(Task task) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);

        TextInputEditText etTaskTitle = dialogView.findViewById(R.id.et_task_title);
        TextInputEditText etTaskDescription = dialogView.findViewById(R.id.et_task_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnAddTask = dialogView.findViewById(R.id.btn_add_task);

        // Pre-fill existing data
        etTaskTitle.setText(task.getTitle());
        etTaskDescription.setText(task.getDescription());
        btnAddTask.setText("Update Task");

        AlertDialog dialog = builder.setView(dialogView).create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAddTask.setOnClickListener(v -> {
            String title = Objects.requireNonNull(etTaskTitle.getText()).toString().trim();
            String description = Objects.requireNonNull(etTaskDescription.getText()).toString().trim();

            if (TextUtils.isEmpty(title)) {
                etTaskTitle.setError("Title is required");
                etTaskTitle.requestFocus();
                return;
            }

            updateTaskInFirebase(task.getId(), title, description);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showDeleteConfirmationDialog(Task task) {
        new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?\n\n\"" + task.getTitle() + "\"")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteTaskFromFirebase(task.getId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    firebaseAuth.signOut();
                    redirectToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Helper Methods

    @SuppressLint("SetTextI18n")
    private void updateUI() {
        int totalTasks = taskList.size();
        int completedTasks = 0;

        for (Task task : taskList) {
            if (task.isCompleted()) {
                completedTasks++;
            }
        }

        if (totalTasks == 0) {
            tvTaskCount.setText("No tasks yet");
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvTaskCount.setText(completedTasks + " of " + totalTasks + " tasks completed");
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginFragment.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        // Show confirmation dialog before exiting
        new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Exit", (dialog, which) -> {
                    super.onBackPressed();
                    finishAffinity(); // Close all activities
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}

// Task Model Class
class Task {
    private String id;
    private String title;
    private String description;
    private boolean completed;
    private long timestamp;

    // Default constructor required for Firebase
    public Task() {}

    public Task(String id, String title, String description, boolean completed, long timestamp) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.completed = completed;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}