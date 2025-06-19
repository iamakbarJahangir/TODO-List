package com.example.todolist;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class HomeActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;
    private long selectedReminderTimestamp = 0;

    // --- DECLARED CLASS-LEVEL VARIABLES ---
    private RecyclerView recyclerView;
    private FloatingActionButton fabAddTask;
    private ImageButton btnLogout;
    private TextView tvTaskCount;
    private TextView tvWelcomeMessage;
    private LinearLayout emptyState;
    private FirebaseAuth firebaseAuth;
    private FirebaseUser currentUser;
    private DatabaseReference databaseReference;
    private TodoAdapter todoAdapter;
    private List<Task> taskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        initializeFirebase();
        // If user is not logged in, initializeFirebase will redirect, so we stop here.
        if (currentUser == null) return;

        initializeViews();
        setupRecyclerView();
        setupClickListeners();
        loadTasksFromFirebase();
        setWelcomeMessage();

        requestNotificationPermission();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Notifications permission denied. Reminders will not work.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void scheduleReminder(Task task) {
        if (task.getReminderTimestamp() <= System.currentTimeMillis()) return;

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("TASK_TITLE", task.getTitle());

        int pendingIntentId = task.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, pendingIntentId, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, task.getReminderTimestamp(), pendingIntent);
            } catch (SecurityException e) {
                Toast.makeText(this, "Permission to schedule exact alarms not granted.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void cancelReminder(Task task) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, AlarmReceiver.class);

        int pendingIntentId = task.getId().hashCode();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, pendingIntentId, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    private void showDateTimePickerDialog(TextView tvReminderTime, long existingTimestamp) {
        final Calendar calendar = Calendar.getInstance();
        if (existingTimestamp > 0) {
            calendar.setTimeInMillis(existingTimestamp);
        }

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);

            new TimePickerDialog(this, (timeView, hourOfDay, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);

                if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Please select a future time", Toast.LENGTH_SHORT).show();
                    return;
                }

                selectedReminderTimestamp = calendar.getTimeInMillis();
                String formattedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(calendar.getTime());
                tvReminderTime.setText(getString(R.string.reminder_set_for, formattedDate));
                tvReminderTime.setVisibility(View.VISIBLE);

            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void initializeFirebase() {
        firebaseAuth = FirebaseAuth.getInstance();
        currentUser = firebaseAuth.getCurrentUser();
        if (currentUser == null) {
            redirectToLogin();
            return;
        }
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
                updateTaskCompletion(task, isCompleted);
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

    private void addTaskToFirebase(String title, String description) {
        if (databaseReference == null) return;
        String taskId = databaseReference.push().getKey();
        if (taskId == null) return;

        Task newTask = new Task(taskId, title, description, false, System.currentTimeMillis(), selectedReminderTimestamp);

        databaseReference.child(taskId).setValue(newTask)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task added!", Toast.LENGTH_SHORT).show();
                    if (newTask.getReminderTimestamp() > 0) {
                        scheduleReminder(newTask);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(HomeActivity.this, "Failed to add task", Toast.LENGTH_SHORT).show());
    }

    private void loadTasksFromFirebase() {
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
                taskList.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                todoAdapter.notifyDataSetChanged();
                updateUI();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(HomeActivity.this, "Failed to load tasks.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateTaskInFirebase(Task taskToUpdate, String title, String description, long reminderTimestamp) {
        if (databaseReference == null) return;

        if (taskToUpdate.getReminderTimestamp() > 0 && taskToUpdate.getReminderTimestamp() != reminderTimestamp) {
            cancelReminder(taskToUpdate);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", title);
        updates.put("description", description);
        updates.put("timestamp", System.currentTimeMillis());
        updates.put("reminderTimestamp", reminderTimestamp);

        databaseReference.child(taskToUpdate.getId()).updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task updated!", Toast.LENGTH_SHORT).show();
                    taskToUpdate.setReminderTimestamp(reminderTimestamp);
                    if (reminderTimestamp > 0) {
                        scheduleReminder(taskToUpdate);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(HomeActivity.this, "Failed to update task", Toast.LENGTH_SHORT).show());
    }

    private void updateTaskCompletion(Task task, boolean isCompleted) {
        if (databaseReference == null) return;
        databaseReference.child(task.getId()).child("completed").setValue(isCompleted)
                .addOnSuccessListener(aVoid -> {
                    if (isCompleted) {
                        Toast.makeText(HomeActivity.this, "Task completed!", Toast.LENGTH_SHORT).show();
                        if (task.getReminderTimestamp() > 0) {
                            cancelReminder(task);
                        }
                    }
                });
    }

    private void deleteTaskFromFirebase(Task task) {
        if (databaseReference == null) return;
        databaseReference.child(task.getId()).removeValue()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Task deleted!", Toast.LENGTH_SHORT).show();
                    if (task.getReminderTimestamp() > 0) {
                        cancelReminder(task);
                    }
                });
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);

        TextInputEditText etTaskTitle = dialogView.findViewById(R.id.et_task_title);
        TextInputEditText etTaskDescription = dialogView.findViewById(R.id.et_task_description);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_cancel);
        MaterialButton btnAddTask = dialogView.findViewById(R.id.btn_add_task);
        MaterialButton btnSetReminder = dialogView.findViewById(R.id.btn_set_reminder);
        TextView tvReminderTime = dialogView.findViewById(R.id.tv_reminder_time);

        AlertDialog dialog = builder.setView(dialogView).create();

        selectedReminderTimestamp = 0;

        btnSetReminder.setOnClickListener(v -> showDateTimePickerDialog(tvReminderTime, 0));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnAddTask.setOnClickListener(v -> {
            String title = Objects.requireNonNull(etTaskTitle.getText()).toString().trim();
            String description = Objects.requireNonNull(etTaskDescription.getText()).toString().trim();
            if (TextUtils.isEmpty(title)) {
                etTaskTitle.setError("Title is required");
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
        MaterialButton btnUpdateTask = dialogView.findViewById(R.id.btn_add_task);
        MaterialButton btnSetReminder = dialogView.findViewById(R.id.btn_set_reminder);
        TextView tvReminderTime = dialogView.findViewById(R.id.tv_reminder_time);

        etTaskTitle.setText(task.getTitle());
        etTaskDescription.setText(task.getDescription());
        btnUpdateTask.setText("Update Task");

        selectedReminderTimestamp = task.getReminderTimestamp();
        if (selectedReminderTimestamp > 0) {
            String formattedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(selectedReminderTimestamp));
            tvReminderTime.setText(getString(R.string.reminder_set_for, formattedDate));
            tvReminderTime.setVisibility(View.VISIBLE);
        }

        AlertDialog dialog = builder.setView(dialogView).create();

        btnSetReminder.setOnClickListener(v -> showDateTimePickerDialog(tvReminderTime, selectedReminderTimestamp));

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnUpdateTask.setOnClickListener(v -> {
            String title = Objects.requireNonNull(etTaskTitle.getText()).toString().trim();
            String description = Objects.requireNonNull(etTaskDescription.getText()).toString().trim();
            if (TextUtils.isEmpty(title)) {
                etTaskTitle.setError("Title is required");
                return;
            }
            updateTaskInFirebase(task, title, description, selectedReminderTimestamp);
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showDeleteConfirmationDialog(Task task) {
        new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom)
                .setTitle("Delete Task")
                .setMessage("Are you sure you want to delete this task?")
                .setPositiveButton("Delete", (dialog, which) -> deleteTaskFromFirebase(task))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- IMPLEMENTED HELPER METHODS ---

    @SuppressLint("SetTextI18n")
    private void setWelcomeMessage() {
        if (currentUser != null) {
            String displayName = currentUser.getDisplayName();
            if (displayName != null && !displayName.isEmpty()) {
                tvWelcomeMessage.setText("Welcome, " + displayName);
            } else {
                tvWelcomeMessage.setText("Welcome!");
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void updateUI() {
        if (taskList.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvTaskCount.setText("No tasks yet!");
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            tvTaskCount.setText(taskList.size() + " tasks");
        }
    }

    private void redirectToLogin() {
        // Redirect to MainActivity which handles the login/signup flow
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this, R.style.MaterialAlertDialog_Custom)
                .setTitle("Exit App")
                .setMessage("Are you sure you want to exit the app?")
                .setPositiveButton("Exit", (dialog, which) -> {
                    super.onBackPressed();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}