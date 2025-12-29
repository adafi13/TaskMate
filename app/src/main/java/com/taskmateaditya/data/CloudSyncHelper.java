package com.taskmateaditya.data;

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
        if (uid == null) return;

        // Hentikan listener lama jika ada
        stopRealtimeListener();

        listenerRegistration = db.collection(COLLECTION_NAME)
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
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
                                        listener.onTaskAdded(task);
                                        break;
                                    case MODIFIED:
                                        listener.onTaskModified(task);
                                        break;
                                    case REMOVED:
                                        // Kirim ID String ke listener
                                        listener.onTaskDeleted(task.getId());
                                        break;
                                }
                            } catch (Exception ex) {
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
    public void syncTaskToCloud(Task task) {
        String uid = getCurrentUserId();
        if (uid == null) return;
        task.setUserId(uid);

        // Gunakan task.getId() (String) langsung sebagai nama dokumen
        db.collection(COLLECTION_NAME)
                .document(task.getId())
                .set(task, SetOptions.merge());
    }

    public void deleteTaskFromCloud(String taskId) { // Parameter sekarang String
        if (getCurrentUserId() == null) return;

        db.collection(COLLECTION_NAME)
                .document(taskId)
                .delete();
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
                    List<Task> tasks = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        try {
                            Task task = document.toObject(Task.class);
                            // Set ID dari nama dokumen (String)
                            task.setId(document.getId());
                            tasks.add(task);
                        } catch (Exception e) { e.printStackTrace(); }
                    }
                    listener.onSuccess(tasks);
                })
                .addOnFailureListener(e -> listener.onFailure(e.getMessage()));
    }
}