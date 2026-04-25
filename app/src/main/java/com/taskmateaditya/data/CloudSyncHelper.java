package com.taskmateaditya.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.List;

public class CloudSyncHelper {
    private static final String TAG = "CloudSyncHelper";
    private static final String COLLECTION_NAME = "tasks";
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // Variabel untuk menyimpan listener
    private ListenerRegistration listenerRegistration;

    public CloudSyncHelper() {
    }

    private String getCurrentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    public interface SyncCallback {
        void onSyncSuccess();

        void onSyncError(String error);
    }

    // --- INTERFACE WAJIB (Diperbarui untuk UUID String) ---
    public interface OnRealtimeUpdateListener {
        void onTaskAdded(Task task);

        void onTaskModified(Task task);

        void onTaskDeleted(String taskId); // Ubah int ke String

        void onError(String error);
    }

    public interface OnSyncCompleteListener {
        void onSuccess(List<Task> tasks);

        void onFailure(String error);
    }
    // ----------------------------------------------------------------

    // --- METODE REAL-TIME ---
    public void startRealtimeListener(OnRealtimeUpdateListener listener) {
        String uid = getCurrentUserId();
        if (uid == null)
            return;

        // Hentikan listener lama jika ada
        stopRealtimeListener();

        listenerRegistration = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "SnapshotListener error: " + e.getMessage());
                        listener.onError(e.getMessage());
                        return;
                    }

                    if (snapshots != null) {
                        for (DocumentChange dc : snapshots.getDocumentChanges()) {
                            try {
                                Task task = dc.getDocument().toObject(Task.class);

                                // PERUBAHAN UTAMA:
                                // Langsung gunakan ID dokumen (String/UUID) dari Firestore
                                task.setId(dc.getDocument().getId());

                                switch (dc.getType()) {
                                    case ADDED:
                                        Log.d(TAG, "Task added from Cloud: " + task.getTitle());
                                        listener.onTaskAdded(task);
                                        break;
                                    case MODIFIED:
                                        Log.d(TAG, "Task modified from Cloud: " + task.getTitle());
                                        listener.onTaskModified(task);
                                        break;
                                    case REMOVED:
                                        Log.d(TAG, "Task removed from Cloud: " + task.getId());
                                        // Kirim ID String ke listener
                                        listener.onTaskDeleted(task.getId());
                                        break;
                                }
                            } catch (Exception ex) {
                                Log.e(TAG, "Error parsing task change: " + ex.getMessage());
                                ex.printStackTrace();
                            }
                        }
                    }
                });
    }

    public void stopRealtimeListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    // --- CRUD BIASA ---
    public void syncTaskToCloud(Task task, SyncCallback callback) {
        String uid = getCurrentUserId();
        if (uid == null) {
            if (callback != null)
                callback.onSyncError("User tidak terautentikasi (UID null)");
            return;
        }
        task.setUserId(uid);

        db.collection(COLLECTION_NAME)
                .document(task.getId())
                .set(task, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Task synced to Cloud: " + task.getTitle());
                    if (callback != null)
                        callback.onSyncSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error syncing task to Cloud: " + e.getMessage());
                    if (callback != null)
                        callback.onSyncError(e.getMessage());
                });
    }

    public void deleteTaskFromCloud(String taskId) { // Parameter sekarang String
        if (getCurrentUserId() == null)
            return;

        db.collection(COLLECTION_NAME)
                .document(taskId)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Task deleted from Cloud: " + taskId))
                .addOnFailureListener(e -> Log.e(TAG, "Error deleting task from Cloud: " + e.getMessage()));
    }

    public void fetchTasksFromCloud(final OnSyncCompleteListener listener) {
        String uid = getCurrentUserId();
        if (uid == null) {
            listener.onFailure("User belum login");
            return;
        }
        db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    Log.d(TAG, "Fetched " + queryDocumentSnapshots.size() + " tasks from Firestore.");
                    List<Task> tasks = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            // Set ID dari nama dokumen (String)
                            task.setId(document.getId());
                            tasks.add(task);
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing task: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    listener.onSuccess(tasks);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching from Firestore: " + e.getMessage());
                    listener.onFailure(e.getMessage());
                });
    }
}