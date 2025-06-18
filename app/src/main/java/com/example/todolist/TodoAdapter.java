package com.example.todolist;

import android.graphics.Paint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TodoAdapter extends RecyclerView.Adapter<TodoAdapter.TodoViewHolder> {

    private List<Task> taskList;
    private OnTaskActionListener listener;

    public interface OnTaskActionListener {
        void onTaskComplete(Task task, boolean isCompleted);
        void onTaskEdit(Task task);
        void onTaskDelete(Task task);
    }

    public TodoAdapter(List<Task> taskList, OnTaskActionListener listener) {
        this.taskList = taskList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public TodoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_todo, parent, false);
        return new TodoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TodoViewHolder holder, int position) {
        Task task = taskList.get(position);
        holder.bind(task);
    }

    @Override
    public int getItemCount() {
        return taskList.size();
    }

    public class TodoViewHolder extends RecyclerView.ViewHolder {
        private CheckBox cbIsCompleted;
        private TextView tvTitle;
        private TextView tvDescription;
        private MaterialButton btnEdit;
        private MaterialButton btnDelete;
        private View statusIndicator;

        public TodoViewHolder(@NonNull View itemView) {
            super(itemView);

            cbIsCompleted = itemView.findViewById(R.id.cb_is_completed);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
        }

        public void bind(Task task) {
            // Set task title
            tvTitle.setText(task.getTitle());

            // Set task description
            if (TextUtils.isEmpty(task.getDescription())) {
                tvDescription.setVisibility(View.GONE);
            } else {
                tvDescription.setVisibility(View.VISIBLE);
                tvDescription.setText(task.getDescription());
            }

            // Set completion status
            cbIsCompleted.setOnCheckedChangeListener(null); // Remove listener temporarily
            cbIsCompleted.setChecked(task.isCompleted());

            // Apply completion styling
            updateCompletionStyling(task.isCompleted());

            // Set status indicator
            if (task.isCompleted()) {
                statusIndicator.setVisibility(View.VISIBLE);
                statusIndicator.setBackgroundColor(itemView.getContext().getColor(android.R.color.holo_green_light));
            } else {
                statusIndicator.setVisibility(View.GONE);
            }

            // Set click listeners
            cbIsCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onTaskComplete(task, isChecked);
                    updateCompletionStyling(isChecked);
                }
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskEdit(task);
                }
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTaskDelete(task);
                }
            });

            // Add click listener to entire card
            itemView.setOnClickListener(v -> {
                // Toggle completion status when card is clicked
                boolean newStatus = !task.isCompleted();
                cbIsCompleted.setChecked(newStatus);
                if (listener != null) {
                    listener.onTaskComplete(task, newStatus);
                }
            });

            // Add long click listener for additional actions
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onTaskEdit(task);
                }
                return true;
            });
        }

        private void updateCompletionStyling(boolean isCompleted) {
            if (isCompleted) {
                // Apply strikethrough and reduce opacity
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvTitle.setAlpha(0.6f);
                tvDescription.setAlpha(0.6f);

                // Update status indicator
                statusIndicator.setVisibility(View.VISIBLE);
                statusIndicator.setBackgroundColor(itemView.getContext().getColor(android.R.color.holo_green_light));
            } else {
                // Remove strikethrough and restore opacity
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                tvTitle.setAlpha(1.0f);
                tvDescription.setAlpha(1.0f);

                // Hide status indicator
                statusIndicator.setVisibility(View.GONE);
            }
        }
    }

    // Helper method to format timestamp (optional)
    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }

    // Method to update the task list
    public void updateTaskList(List<Task> newTaskList) {
        this.taskList = newTaskList;
        notifyDataSetChanged();
    }

    // Method to add a single task
    public void addTask(Task task) {
        taskList.add(0, task); // Add to beginning
        notifyItemInserted(0);
    }

    // Method to remove a task
    public void removeTask(int position) {
        if (position >= 0 && position < taskList.size()) {
            taskList.remove(position);
            notifyItemRemoved(position);
        }
    }

    // Method to update a task
    public void updateTask(int position, Task updatedTask) {
        if (position >= 0 && position < taskList.size()) {
            taskList.set(position, updatedTask);
            notifyItemChanged(position);
        }
    }
}