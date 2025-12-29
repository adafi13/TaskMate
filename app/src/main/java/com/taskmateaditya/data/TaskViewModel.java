package com.taskmateaditya.data;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.widget.DashboardWidgetProvider; // IMPORT BARU
import com.taskmateaditya.widget.TaskWidgetProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskViewModel extends AndroidViewModel {
    private final TaskRepository repository;
    private final MutableLiveData<FilterState> currentFilter = new MutableLiveData<>(new FilterState());
    private final LiveData<List<Task>> tasks;

    public enum SortType { NEWEST, DEADLINE, PRIORITY }

    public static class FilterState {
        String searchQuery = "";
        SortType sortType = SortType.NEWEST;
        String categoryFilter = null;

        public FilterState() {}
        public FilterState(String query, SortType sort, String category) {
            this.searchQuery = query;
            this.sortType = sort;
            this.categoryFilter = category;
        }
    }

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);

        tasks = Transformations.switchMap(currentFilter, state -> {
            String userId = getCurrentUserId();

            if (userId == null) {
                return new MutableLiveData<>(Collections.emptyList());
            }

            LiveData<List<Task>> rawTasks;

            if (state.searchQuery != null && !state.searchQuery.isEmpty()) {
                rawTasks = repository.searchTasks(state.searchQuery, userId);
            } else if (state.categoryFilter != null && !state.categoryFilter.equals("Semua")) {
                rawTasks = repository.getTasksByCategory(state.categoryFilter, userId);
            } else {
                switch (state.sortType) {
                    case DEADLINE: rawTasks = repository.getTasksSortedByDeadline(userId); break;
                    case PRIORITY: rawTasks = repository.getTasksSortedByPriority(userId); break;
                    default: rawTasks = repository.getAllTasks(userId); break;
                }
            }

            return Transformations.map(rawTasks, list -> {
                if (list == null) return null;
                List<Task> activeTasks = new ArrayList<>();
                List<Task> completedTasks = new ArrayList<>();

                for (Task task : list) {
                    if (task.isCompleted()) {
                        completedTasks.add(task);
                    } else {
                        activeTasks.add(task);
                    }
                }

                List<Task> combined = new ArrayList<>(activeTasks);
                combined.addAll(completedTasks);
                return combined;
            });
        });
    }

    public LiveData<Boolean> getIsLoading() {
        return repository.getIsLoading();
    }

    public void syncData() {
        if (repository != null) {
            repository.startRealtimeSync();
        }
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    // --- UPDATED METHODS: REFRESH KEDUA WIDGET ---

    public void insert(Task task) {
        String uid = getCurrentUserId();
        if (uid != null) {
            task.setUserId(uid);
            repository.insert(task);

            // Refresh Widget List
            TaskWidgetProvider.sendRefreshBroadcast(getApplication());
            // Refresh Widget Dashboard (BARU)
            DashboardWidgetProvider.sendRefreshBroadcast(getApplication());
        }
    }

    public void update(Task task) {
        repository.update(task);

        // Refresh Widget List
        TaskWidgetProvider.sendRefreshBroadcast(getApplication());
        // Refresh Widget Dashboard (BARU)
        DashboardWidgetProvider.sendRefreshBroadcast(getApplication());
    }

    public void delete(Task task) {
        repository.delete(task);

        // Refresh Widget List
        TaskWidgetProvider.sendRefreshBroadcast(getApplication());
        // Refresh Widget Dashboard (BARU)
        DashboardWidgetProvider.sendRefreshBroadcast(getApplication());
    }

    // ------------------------------------------

    public LiveData<Task> getTaskById(String taskId) {
        return repository.getTaskById(taskId);
    }

    public LiveData<List<Task>> getSearchResults() {
        return tasks;
    }

    public void setSearchQuery(String query) {
        FilterState current = currentFilter.getValue();
        if (current != null) currentFilter.setValue(new FilterState(query, current.sortType, current.categoryFilter));
    }

    public void setSortOrder(SortType sort) {
        FilterState current = currentFilter.getValue();
        if (current != null) currentFilter.setValue(new FilterState(current.searchQuery, sort, current.categoryFilter));
    }

    public void setCategoryFilter(String category) {
        FilterState current = currentFilter.getValue();
        if (current != null) {
            String newCategory = (category == null || category.equals("Semua")) ? null : category;
            currentFilter.setValue(new FilterState(current.searchQuery, current.sortType, newCategory));
        }
    }

    public LiveData<List<Task>> getAllTasks() {
        String userId = getCurrentUserId();
        if (userId != null) {
            return repository.getAllTasks(userId);
        } else {
            return new MutableLiveData<>(Collections.emptyList());
        }
    }
}