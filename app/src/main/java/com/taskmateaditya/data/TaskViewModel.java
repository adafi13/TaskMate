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
    private final FirebaseAuth.AuthStateListener authStateListener;

    public enum SortType {
        NEWEST, DEADLINE, PRIORITY
    }

    public static class FilterState {
        String searchQuery = "";
        SortType sortType = SortType.NEWEST;
        String categoryFilter = null;

        public FilterState() {
        }

        public FilterState(String query, SortType sort, String category) {
            this.searchQuery = query;
            this.sortType = sort;
            this.categoryFilter = category;
        }
    }

    public TaskViewModel(@NonNull Application application) {
        super(application);
        repository = new TaskRepository(application);

        // Fix: Force the filter to re-evaluate whenever Firebase auth state is confirmed.
        // This prevents the switchMap from getting stuck on a static empty list
        // when getCurrentUserId() returned null on the very first evaluation.
        authStateListener = auth -> {
            if (auth.getCurrentUser() != null) {
                // Re-emit the current filter — switchMap re-runs and picks up the valid userId
                currentFilter.setValue(currentFilter.getValue());
            }
        };
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener);

        tasks = Transformations.switchMap(currentFilter, state -> {
            String userId = getCurrentUserId();

            if (userId == null) {
                return new MutableLiveData<>(Collections.emptyList());
            }

            LiveData<List<Task>> rawTasks;

            switch (state.sortType) {
                case DEADLINE:
                    rawTasks = repository.getTasksSortedByDeadline(userId);
                    break;
                case PRIORITY:
                    rawTasks = repository.getTasksSortedByPriority(userId);
                    break;
                default:
                    rawTasks = repository.getAllTasks(userId);
                    break;
            }

            return Transformations.map(rawTasks, list -> {
                if (list == null)
                    return null;
                List<Task> activeTasks = new ArrayList<>();
                List<Task> completedTasks = new ArrayList<>();

                for (Task task : list) {
                    // Cek Category Filter
                    if (state.categoryFilter != null && !state.categoryFilter.equals("Semua")) {
                        if (task.getCategory() == null || !task.getCategory().equals(state.categoryFilter)) {
                            continue;
                        }
                    }

                    // Cek Search Query
                    if (state.searchQuery != null && !state.searchQuery.isEmpty()) {
                        String query = state.searchQuery.toLowerCase();
                        boolean matchTitle = task.getTitle() != null && task.getTitle().toLowerCase().contains(query);
                        boolean matchSub = task.getMataKuliah() != null && task.getMataKuliah().toLowerCase().contains(query);
                        if (!matchTitle && !matchSub) {
                            continue;
                        }
                    }

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

    /** Stop the Firestore realtime listener — call this in Activity.onStop(). */
    public void stopSync() {
        if (repository != null) {
            repository.stopRealtimeSync();
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (authStateListener != null) {
            FirebaseAuth.getInstance().removeAuthStateListener(authStateListener);
        }
        stopSync();
        if (repository != null) {
            repository.shutdown();
        }
    }

    public void refreshTasksFromCloud() {
        if (repository != null) {
            repository.refreshTasksFromCloud();
        }
    }

    /** Restore on Login: calls back on main thread after data is written to Room. */
    public void refreshTasksFromCloud(Runnable onComplete) {
        if (repository != null) {
            repository.refreshTasksFromCloud(onComplete);
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
        if (current != null)
            currentFilter.setValue(new FilterState(query, current.sortType, current.categoryFilter));
    }

    public void setSortOrder(SortType sort) {
        FilterState current = currentFilter.getValue();
        if (current != null)
            currentFilter.setValue(new FilterState(current.searchQuery, sort, current.categoryFilter));
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