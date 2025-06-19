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

// =========== NEW IMPORTS START ===========
import java.text.DateFormat;
import java.util.Date;
// =========== NEW IMPORTS END ===========
import java.util.List;

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
        // =========== NEW VIEW START ===========
        private TextView tvReminder;
        // =========== NEW VIEW END ===========

        public TodoViewHolder(@NonNull View itemView) {
            super(itemView);

            cbIsCompleted = itemView.findViewById(R.id.cb_is_completed);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvDescription = itemView.findViewById(R.id.tv_description);
            btnEdit = itemView.findViewById(R.id.btn_edit);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            // =========== NEW VIEW FIND START ===========
            tvReminder = itemView.findViewById(R.id.tv_reminder);
            // =========== NEW VIEW FIND END ===========
        }

        public void bind(Task task) {
            tvTitle.setText(task.getTitle());

            if (TextUtils.isEmpty(task.getDescription())) {
                tvDescription.setVisibility(View.GONE);
            } else {
                tvDescription.setVisibility(View.VISIBLE);
                tvDescription.setText(task.getDescription());
            }

            cbIsCompleted.setOnCheckedChangeListener(null);
            cbIsCompleted.setChecked(task.isCompleted());
            updateCompletionStyling(task.isCompleted());

            // =========== BIND REMINDER DATA START ===========
            if (task.getReminderTimestamp() > 0 && !task.isCompleted()) {
                String formattedDate = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(new Date(task.getReminderTimestamp()));
                tvReminder.setText(itemView.getContext().getString(R.string.reminder_set_for, formattedDate));
                tvReminder.setVisibility(View.VISIBLE);
            } else {
                tvReminder.setVisibility(View.GONE);
            }
            // =========== BIND REMINDER DATA END ===========

            cbIsCompleted.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (listener != null) {
                    listener.onTaskComplete(task, isChecked);
                }
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onTaskEdit(task);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onTaskDelete(task);
            });
        }

        private void updateCompletionStyling(boolean isCompleted) {
            if (isCompleted) {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tvTitle.setAlpha(0.6f);
                tvDescription.setAlpha(0.6f);
                // Hide reminder if task is completed
                tvReminder.setVisibility(View.GONE);
            } else {
                tvTitle.setPaintFlags(tvTitle.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                tvDescription.setPaintFlags(tvDescription.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
                tvTitle.setAlpha(1.0f);
                tvDescription.setAlpha(1.0f);
            }
        }
    }
}