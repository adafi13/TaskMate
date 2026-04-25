package com.taskmateaditya.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface TaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Task task);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Task> tasks);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(Task task);

    @Delete
    void delete(Task task);

    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY createdAt DESC")
    LiveData<List<Task>> getAllTasks(String userId);

    @Query("SELECT * FROM task_table WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR mataKuliah LIKE '%' || :query || '%') ORDER BY deadline ASC")
    LiveData<List<Task>> searchTasks(String query, String userId);

    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY deadline ASC")
    LiveData<List<Task>> getTasksSortedByDeadline(String userId);

    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY CASE priority WHEN 'Tinggi' THEN 1 WHEN 'Sedang' THEN 2 WHEN 'Rendah' THEN 3 ELSE 4 END ASC")
    LiveData<List<Task>> getTasksSortedByPriority(String userId);

    @Query("SELECT * FROM task_table WHERE userId = :userId AND category = :category ORDER BY deadline ASC")
    LiveData<List<Task>> getTasksByCategory(String category, String userId);

    @Query("SELECT * FROM task_table WHERE id = :taskId")
    LiveData<Task> getTaskById(String taskId);

    @Query("SELECT * FROM task_table ORDER BY deadline ASC")
    LiveData<List<Task>> getAllTasksNoUser();

    @Query("SELECT * FROM task_table WHERE isCompleted = 0 ORDER BY deadline ASC")
    List<Task> getActiveTasksForWidget();

    @Query("SELECT COUNT(*) FROM task_table WHERE userId = :userId")
    int getTotalTasksCount(String userId);

    @Query("SELECT COUNT(*) FROM task_table WHERE userId = :userId AND isCompleted = 1")
    int getCompletedTasksCount(String userId);

    @Query("SELECT * FROM task_table")
    List<Task> getAllTasksForWidget();

    @Query("SELECT * FROM task_table WHERE userId = :userId AND deadline = :date AND isCompleted = 0")
    List<Task> getTasksForDateSync(String userId, String date);

    // RAG: Synchronous query for all pending (not completed) tasks — called from background thread
    @Query("SELECT * FROM task_table WHERE userId = :userId AND isCompleted = 0 ORDER BY deadline ASC")
    List<Task> getPendingTasksForUser(String userId);

}