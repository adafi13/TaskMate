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

    // 1. Ambil Semua Tugas (User ID)
    // Saat ini sorting berdasarkan ID (UUID) yang acak.
    // Jika ingin benar-benar urut waktu, nanti perlu tambah field 'createdAt' di Task.java.
    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY id DESC")
    LiveData<List<Task>> getAllTasks(String userId);

    // 2. Pencarian
    @Query("SELECT * FROM task_table WHERE userId = :userId AND (title LIKE '%' || :query || '%' OR mataKuliah LIKE '%' || :query || '%') ORDER BY deadline ASC")
    LiveData<List<Task>> searchTasks(String query, String userId);

    // 3. Sortir Deadline
    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY deadline ASC")
    LiveData<List<Task>> getTasksSortedByDeadline(String userId);

    // 4. Sortir Prioritas
    @Query("SELECT * FROM task_table WHERE userId = :userId ORDER BY CASE priority WHEN 'Tinggi' THEN 1 WHEN 'Sedang' THEN 2 WHEN 'Rendah' THEN 3 ELSE 4 END ASC")
    LiveData<List<Task>> getTasksSortedByPriority(String userId);

    // 5. Filter Kategori
    @Query("SELECT * FROM task_table WHERE userId = :userId AND category = :category ORDER BY deadline ASC")
    LiveData<List<Task>> getTasksByCategory(String category, String userId);

    // 6. Ambil Satu Tugas by ID (Parameter String untuk UUID)
    @Query("SELECT * FROM task_table WHERE id = :taskId")
    LiveData<Task> getTaskById(String taskId);

    // 7. Ambil Semua Tanpa User (Untuk Debugging/Admin jika perlu)
    @Query("SELECT * FROM task_table ORDER BY deadline ASC")
    LiveData<List<Task>> getAllTasksNoUser();

    @Query("SELECT * FROM task_table WHERE isCompleted = 0 ORDER BY deadline ASC")
    List<Task> getActiveTasksForWidget();

    // Hitung total tugas user ini
    @Query("SELECT COUNT(*) FROM task_table WHERE userId = :userId")
    int getTotalTasksCount(String userId);

    // Hitung tugas yang SUDAH selesai
    @Query("SELECT COUNT(*) FROM task_table WHERE userId = :userId AND isCompleted = 1")
    int getCompletedTasksCount(String userId);

    // Tambahkan baris ini di TaskDao.java
    @Query("SELECT * FROM task_table")
    List<Task> getAllTasksForWidget();


}