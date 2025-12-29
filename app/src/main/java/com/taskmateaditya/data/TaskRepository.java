package com.taskmateaditya.data;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.taskmateaditya.utils.ReminderHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskRepository {
    // MENAMBAHKAN 'final' UNTUK MENGHILANGKAN WARNING
    private final TaskDao taskDao;
    private final CloudSyncHelper cloudSyncHelper;
    private final ExecutorService executorService;
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final Application application;

    public TaskRepository(Application application) {
        this.application = application;
        TaskDatabase database = TaskDatabase.getDatabase(application);
        this.taskDao = database.taskDao();
        this.cloudSyncHelper = new CloudSyncHelper();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }

    // --- CRUD ---
    public void insert(Task task) {
        executorService.execute(() -> {
            taskDao.insert(task);
            cloudSyncHelper.syncTaskToCloud(task);
        });
    }

    public void update(Task task) {
        executorService.execute(() -> {
            taskDao.update(task);
            cloudSyncHelper.syncTaskToCloud(task);
        });
    }

    public void delete(Task task) {
        executorService.execute(() -> {
            taskDao.delete(task);
            cloudSyncHelper.deleteTaskFromCloud(task.getId());
        });
    }

    // --- QUERY METHODS ---
    public LiveData<List<Task>> getAllTasks(String userId) { return taskDao.getAllTasks(userId); }
    public LiveData<List<Task>> searchTasks(String query, String userId) { return taskDao.searchTasks(query, userId); }
    public LiveData<List<Task>> getTasksSortedByDeadline(String userId) { return taskDao.getTasksSortedByDeadline(userId); }
    public LiveData<List<Task>> getTasksSortedByPriority(String userId) { return taskDao.getTasksSortedByPriority(userId); }
    public LiveData<List<Task>> getTasksByCategory(String category, String userId) { return taskDao.getTasksByCategory(category, userId); }

    // Parameter String sudah benar
    public LiveData<Task> getTaskById(String taskId) {
        return taskDao.getTaskById(taskId);
    }

    public LiveData<List<Task>> getAllTasksNoUser() { return taskDao.getAllTasksNoUser(); }

    // --- REALTIME SYNC ---
    public void startRealtimeSync() {
        isLoading.postValue(true);
        cloudSyncHelper.startRealtimeListener(new CloudSyncHelper.OnRealtimeUpdateListener() {
            @Override
            public void onTaskAdded(Task task) {
                executorService.execute(() -> {
                    taskDao.insert(task);
                    // Jalankan di Main Thread untuk AlarmManager
                    new Handler(Looper.getMainLooper()).post(() ->
                            ReminderHelper.setReminder(application.getApplicationContext(), task)
                    );
                    isLoading.postValue(false);
                });
            }

            @Override
            public void onTaskModified(Task task) {
                executorService.execute(() -> {
                    taskDao.update(task);
                    new Handler(Looper.getMainLooper()).post(() ->
                            ReminderHelper.setReminder(application.getApplicationContext(), task)
                    );
                    isLoading.postValue(false);
                });
            }

            @Override
            public void onTaskDeleted(String taskId) {
                Task dummy = new Task();
                dummy.setId(taskId);

                executorService.execute(() -> {
                    taskDao.delete(dummy);
                    new Handler(Looper.getMainLooper()).post(() ->
                            ReminderHelper.cancelReminder(application.getApplicationContext(), dummy)
                    );
                    isLoading.postValue(false);
                });
            }

            @Override
            public void onError(String error) {
                isLoading.postValue(false);
            }
        });
    }

    // --- MANUAL SYNC ---
    public void refreshTasksFromCloud() {
        isLoading.postValue(true);
        cloudSyncHelper.fetchTasksFromCloud(new CloudSyncHelper.OnSyncCompleteListener() {
            @Override
            public void onSuccess(List<Task> cloudTasks) {
                executorService.execute(() -> {
                    taskDao.insertAll(cloudTasks);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        for (Task t : cloudTasks) {
                            ReminderHelper.setReminder(application.getApplicationContext(), t);
                        }
                    });
                    isLoading.postValue(false);
                });
            }
            @Override
            public void onFailure(String error) {
                isLoading.postValue(false);
            }
        });
    }
}