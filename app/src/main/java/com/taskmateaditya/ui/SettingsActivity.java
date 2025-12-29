package com.taskmateaditya.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.api.services.drive.DriveScopes; // Pastikan library Drive API ada
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.taskmateaditya.R;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.utils.DriveServiceHelper;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private MaterialButton btnLogout, btnFeedback, btnChangePassword, btnDeleteAccount;
    private MaterialSwitch switchNotification, switchReminder, switchDarkMode;
    private TextView tvUserName, tvUserEmail, tvStatTotal, tvStatDone, tvStatPending, tvAppVersion;
    private ImageView imgProfile;
    private ProgressBar progressBarProfile;
    private CardView cardProfileImage, btnEditProfile;
    private LinearLayout btnEditName;

    private static final String PREFS_NAME = "TaskMatePrefs";
    private static final String KEY_NOTIFICATION = "key_notification";
    private static final String KEY_REMINDER = "key_reminder";
    private static final String KEY_DARK_MODE = "key_dark_mode";
    private static final String KEY_PROFILE_URI = "key_profile_uri";
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 101;
    private static final int ALARM_REQ_CODE = 100;
    private static final String CHANNEL_ID = "realtime_notif_channel";

    private SharedPreferences sharedPreferences;
    private GoogleSignInClient mGoogleSignInClient;
    private TaskViewModel taskViewModel;
    private FirebaseFirestore db;
    private ListenerRegistration userProfileListener;

    private Uri currentProfileUri;
    private Uri previewUri;
    private Object currentImageModel;

    private DriveServiceHelper mDriveServiceHelper;
    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    // Launcher untuk Image Picker
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        previewUri = imageUri;
                        loadProfileImageWithGlide(imageUri);

                        // 🔥 PERBAIKAN: Cek Izin Drive sebelum Upload 🔥
                        checkDrivePermissionAndUpload(imageUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        setContentView(R.layout.activity_settings);

        db = FirebaseFirestore.getInstance();

        try {
            taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        } catch (Exception e) { e.printStackTrace(); }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Inisialisasi Drive Helper jika sudah login
        updateDriveServiceHelper();

        initViews();
        displayAppVersion();
        displayUserData();
        loadStatistics();
        updateSwitchStates();
        setupListeners();
        createNotificationChannel();
    }

    // 🔥 METHOD BARU: Update Helper agar selalu sinkron dengan akun aktif
    private void updateDriveServiceHelper() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            mDriveServiceHelper = new DriveServiceHelper(
                    DriveServiceHelper.getGoogleDriveService(this, account, "TaskMate"));
        }
    }

    // 🔥 LOGIKA BARU: Cek & Minta Izin Drive 🔥
    private void checkDrivePermissionAndUpload(Uri imageUri) {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);

        // 1. Cek apakah user login via Google
        if (account == null) {
            // Login via Email biasa -> Simpan Lokal
            saveLocalOnly(imageUri);
            return;
        }

        // 2. Cek apakah izin Drive File sudah diberikan
        Scope driveScope = new Scope(DriveScopes.DRIVE_FILE);
        if (!GoogleSignIn.hasPermissions(account, driveScope)) {
            // Jika belum ada izin, minta izin ke user
            GoogleSignIn.requestPermissions(this, 9002, account, driveScope);
            // Simpan sementara di lokal sambil menunggu (opsional)
            saveLocalOnly(imageUri);
        } else {
            // Jika izin sudah ada, langsung upload
            if (mDriveServiceHelper == null) updateDriveServiceHelper();
            uploadProfileImageToDrive(imageUri);
        }
    }

    // Handle hasil permintaan izin
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 9002) {
            if (resultCode == RESULT_OK) {
                // Izin diberikan, update helper dan coba upload lagi
                updateDriveServiceHelper();
                if (previewUri != null) {
                    uploadProfileImageToDrive(previewUri);
                }
            } else {
                Toast.makeText(this, "Izin Drive ditolak. Foto hanya disimpan lokal.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        startListeningToUserProfile();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (userProfileListener != null) {
            userProfileListener.remove();
        }
    }

    private void startListeningToUserProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userProfileListener = db.collection("users").document(user.getUid())
                    .addSnapshotListener((documentSnapshot, e) -> {
                        if (e != null) return;

                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (name != null && !name.isEmpty()) {
                                tvUserName.setText(name);
                            }

                            String photoUrl = documentSnapshot.getString("photoUrl");
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                if (photoUrl.startsWith("googledrive://")) {
                                    previewUri = null;
                                    if (mDriveServiceHelper != null) {
                                        loadDriveImage(photoUrl);
                                    } else {
                                        // Coba inisialisasi lagi jika helper null
                                        updateDriveServiceHelper();
                                        if (mDriveServiceHelper != null) loadDriveImage(photoUrl);
                                    }
                                } else {
                                    previewUri = Uri.parse(photoUrl);
                                    loadProfileImageWithGlide(photoUrl);
                                }
                            } else {
                                loadLocalFallback();
                            }
                        } else {
                            loadLocalFallback();
                        }
                    });
        }
    }

    private void loadDriveImage(String photoUrl) {
        String fileId = photoUrl.replace("googledrive://", "");
        progressBarProfile.setVisibility(View.VISIBLE);

        mDriveServiceHelper.readFile(fileId)
                .addOnSuccessListener(pair -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBarProfile.setVisibility(View.GONE);
                    byte[] imageBytes = pair.second;
                    currentImageModel = imageBytes;
                    loadProfileImageWithGlide(imageBytes);
                })
                .addOnFailureListener(e -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBarProfile.setVisibility(View.GONE);
                    // Jika gagal (misal 403 Forbidden), fallback ke lokal
                    Log.e("DriveLoad", "Error: " + e.getMessage());
                    loadLocalFallback();
                });
    }

    private void uploadProfileImageToDrive(Uri uri) {
        Toast.makeText(this, getString(R.string.msg_uploading), Toast.LENGTH_SHORT).show();
        progressBarProfile.setVisibility(View.VISIBLE);

        mExecutor.execute(() -> {
            try {
                String fileId = mDriveServiceHelper.uploadFile(
                        getContentResolver(),
                        uri,
                        "tm_profile_" + System.currentTimeMillis() + ".jpg");

                if (fileId != null) {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null) {
                        String driveUriString = "googledrive://" + fileId;
                        Uri driveUri = Uri.parse(driveUriString);

                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setPhotoUri(driveUri)
                                .build();

                        user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                updateFirestoreProfile(user.getUid(), null, driveUriString);

                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    progressBarProfile.setVisibility(View.GONE);
                                    Toast.makeText(SettingsActivity.this, getString(R.string.msg_sync_success), Toast.LENGTH_SHORT).show();
                                    sharedPreferences.edit().remove(KEY_PROFILE_URI).apply();
                                });
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        progressBarProfile.setVisibility(View.GONE);
                        // Pesan error spesifik jika 403 (Permission Denied)
                        if (e.getMessage() != null && e.getMessage().contains("403")) {
                            Toast.makeText(SettingsActivity.this, "Gagal: Email ini belum terdaftar di Test Users Google Console.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(SettingsActivity.this, "Gagal upload ke Drive, menyimpan lokal.", Toast.LENGTH_SHORT).show();
                        }
                        saveLocalOnly(uri);
                    }
                });
            }
        });
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        btnFeedback = findViewById(R.id.btnFeedback);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        switchNotification = findViewById(R.id.switchNotification);
        switchReminder = findViewById(R.id.switchReminder);
        switchDarkMode = findViewById(R.id.switchDarkMode);

        tvUserName = findViewById(R.id.tvUserName);
        btnEditName = findViewById(R.id.btnEditName);
        tvUserEmail = findViewById(R.id.tvUserEmail);
        tvAppVersion = findViewById(R.id.tvAppVersion);

        imgProfile = findViewById(R.id.imgProfile);
        progressBarProfile = findViewById(R.id.progressBarProfile);

        cardProfileImage = findViewById(R.id.cardProfileImage);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        tvStatTotal = findViewById(R.id.tvStatTotal);
        tvStatDone = findViewById(R.id.tvStatDone);
        tvStatPending = findViewById(R.id.tvStatPending);
    }

    private void displayAppVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvAppVersion.setText(getString(R.string.app_version_prefix) + " " + pInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            tvAppVersion.setText(getString(R.string.app_version_prefix) + " 1.0");
        }
    }

    private void loadProfileImageWithGlide(Object model) {
        if (isFinishing() || isDestroyed()) return;
        currentImageModel = model;
        Glide.with(this)
                .load(model)
                .placeholder(R.drawable.ic_tm_logo)
                .error(R.drawable.ic_tm_logo)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .signature(new ObjectKey(System.currentTimeMillis()))
                .circleCrop()
                .into(imgProfile);
    }

    private void saveLocalOnly(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            sharedPreferences.edit().putString(KEY_PROFILE_URI, uri.toString()).apply();
            previewUri = uri;
            loadProfileImageWithGlide(uri);
            Toast.makeText(this, getString(R.string.msg_saved_local), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {}
    }

    private void loadLocalFallback() {
        String uriString = sharedPreferences.getString(KEY_PROFILE_URI, null);
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            previewUri = uri;
            loadProfileImageWithGlide(uri);
        } else {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && user.getPhotoUrl() != null) {
                previewUri = user.getPhotoUrl();
                loadProfileImageWithGlide(user.getPhotoUrl());
            }
        }
    }

    private void updateFirestoreProfile(String uid, String name, String photoUrl) {
        Map<String, Object> updates = new HashMap<>();
        if (name != null) updates.put("name", name);
        if (photoUrl != null) updates.put("photoUrl", photoUrl);

        db.collection("users").document(uid)
                .update(updates)
                .addOnFailureListener(e -> {
                    db.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge());
                });
    }

    private void showEditNameDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(getString(R.string.hint_new_name));
        if (user.getDisplayName() != null) {
            input.setText(user.getDisplayName());
            input.setSelection(input.getText().length());
        }

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = (int) (20 * getResources().getDisplayMetrics().density);
        params.rightMargin = (int) (20 * getResources().getDisplayMetrics().density);
        input.setLayoutParams(params);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_edit_name))
                .setView(container)
                .setPositiveButton(getString(R.string.btn_save), (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateProfileName(newName);
                    } else {
                        Toast.makeText(this, getString(R.string.msg_name_empty), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void updateProfileName(String newName) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(newName)
                    .build();

            user.updateProfile(profileUpdates)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            updateFirestoreProfile(user.getUid(), newName, null);
                            Toast.makeText(this, getString(R.string.msg_name_updated), Toast.LENGTH_SHORT).show();
                            tvUserName.setText(newName);
                        } else {
                            Toast.makeText(this, getString(R.string.msg_name_update_failed), Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showChangePasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            for (com.google.firebase.auth.UserInfo profile : user.getProviderData()) {
                if (profile.getProviderId().equals("google.com")) {
                    new AlertDialog.Builder(this)
                            .setTitle("Akun Google")
                            .setMessage("Anda login menggunakan Google. Silakan ubah password melalui pengaturan akun Google Anda.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_change_pass))
                .setMessage(getString(R.string.dialog_msg_change_pass))
                .setPositiveButton("Kirim Email", (dialog, which) -> {
                    if (user != null && user.getEmail() != null) {
                        FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail())
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        showSpamWarningDialog(user.getEmail());
                                    } else {
                                        Toast.makeText(this, "Gagal mengirim email: " +
                                                        (task.getException() != null ? task.getException().getMessage() : "Error"),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void showSpamWarningDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Email Terkirim! 📧")
                .setMessage("Link untuk mengubah password telah dikirim ke:\n" + email + "\n\n" +
                        "⚠️ PENTING: JIKA TIDAK MUNCUL DI INBOX, MOHON PERIKSA FOLDER SPAM ATAU PROMOSI.")
                .setPositiveButton("Mengerti", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_delete_account))
                .setMessage("PERINGATAN: Tindakan ini permanen. Semua data tugas dan profil akan hilang selamanya.\n\nYakin ingin menghapus?")
                .setPositiveButton(getString(R.string.btn_delete_confirm), (dialog, which) -> {
                    deleteAccountPermanently();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void deleteAccountPermanently() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    deleteFirebaseAuthUser(user);
                })
                .addOnFailureListener(e -> {
                    deleteFirebaseAuthUser(user);
                });
    }

    private void deleteFirebaseAuthUser(FirebaseUser user) {
        user.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(SettingsActivity.this, getString(R.string.msg_account_deleted), Toast.LENGTH_SHORT).show();
                signOutAndExit();
            } else {
                try {
                    throw task.getException();
                } catch (FirebaseAuthRecentLoginRequiredException e) {
                    new AlertDialog.Builder(SettingsActivity.this)
                            .setTitle("Login Ulang Diperlukan")
                            .setMessage("Demi keamanan, Anda harus logout dan login kembali sebelum menghapus akun ini.")
                            .setPositiveButton("Logout Sekarang", (dialog, which) -> performLogout())
                            .setCancelable(false)
                            .show();
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "Gagal hapus akun: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void showLogoutConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_title_logout))
                .setMessage(getString(R.string.dialog_msg_logout))
                .setPositiveButton(getString(R.string.btn_yes_logout), (dialog, which) -> performLogout())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    private void performLogout() {
        FirebaseAuth.getInstance().signOut();
        signOutAndExit();
    }

    private void signOutAndExit() {
        if (mGoogleSignInClient != null) {
            mGoogleSignInClient.signOut().addOnCompleteListener(this, task -> navigateToLogin());
        } else {
            navigateToLogin();
        }
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateSwitchStates() {
        switchNotification.setOnCheckedChangeListener(null);
        switchReminder.setOnCheckedChangeListener(null);
        switchDarkMode.setOnCheckedChangeListener(null);

        switchNotification.setChecked(sharedPreferences.getBoolean(KEY_NOTIFICATION, true));
        switchReminder.setChecked(sharedPreferences.getBoolean(KEY_REMINDER, false));
        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDark);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnLogout.setOnClickListener(v -> showLogoutConfirmationDialog());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        if (btnEditName != null) {
            btnEditName.setOnClickListener(v -> showEditNameDialog());
        }

        cardProfileImage.setOnClickListener(v -> {
            if (currentImageModel != null) {
                if (previewUri != null) {
                    Intent intent = new Intent(this, ImagePreviewActivity.class);
                    intent.putExtra("IMAGE_URI", previewUri.toString());
                    try {
                        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                                this, imgProfile, "profile_transform");
                        startActivity(intent, options.toBundle());
                    } catch (Exception e) {
                        startActivity(intent);
                    }
                } else {
                    Toast.makeText(this, "Foto tersinkron dari Drive", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, getString(R.string.msg_photo_not_ready), Toast.LENGTH_SHORT).show();
            }
        });

        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            });
        }

        btnFeedback.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:adityanovaldy721@gmail.com"));
            try {
                startActivity(Intent.createChooser(intent, "Kirim email:"));
            } catch (Exception ex) {}
        });

        switchNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_NOTIFICATION, isChecked).apply();
            if (isChecked) {
                checkNotificationPermission();
                showTestNotification();
            }
        });

        switchReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean(KEY_REMINDER, isChecked).apply();
            if (isChecked) {
                checkNotificationPermission();
                checkExactAlarmPermission();
                handleDailyReminder(true);
            } else {
                handleDailyReminder(false);
            }
        });

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                sharedPreferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
                buttonView.postDelayed(() -> {
                    AppCompatDelegate.setDefaultNightMode(isChecked ?
                            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                }, 200);
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void showTestNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_tm_logo)
                    .setContentTitle(getString(R.string.notif_active_title))
                    .setContentText(getString(R.string.notif_active_msg))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);
            nm.notify(1, builder.build());
        }
    }

    private void displayUserData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            tvUserEmail.setText(user.getEmail());
            String name = user.getDisplayName();
            if (name != null && !name.isEmpty()) {
                tvUserName.setText(name);
            } else {
                tvUserName.setText(getString(R.string.default_user_name));
            }
        }
    }

    private void loadStatistics() {
        if (taskViewModel == null) return;
        taskViewModel.getSearchResults().observe(this, tasks -> {
            if (tasks != null) {
                int total = tasks.size();
                int done = 0;
                for (com.taskmateaditya.data.Task t : tasks) {
                    if (t.isCompleted()) done++;
                }
                tvStatTotal.setText(String.valueOf(total));
                tvStatDone.setText(String.valueOf(done));
                tvStatPending.setText(String.valueOf(total - done));
            }
        });
    }

    private void handleDailyReminder(boolean isEnabled) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, DailyReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQ_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (isEnabled) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, 7);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
            if (alarmManager != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), pendingIntent);
                } else {
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
                }
            }
            Toast.makeText(this, getString(R.string.msg_reminder_on), Toast.LENGTH_SHORT).show();
        } else {
            if (alarmManager != null) alarmManager.cancel(pendingIntent);
            Toast.makeText(this, getString(R.string.msg_reminder_off), Toast.LENGTH_SHORT).show();
        }
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }

    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}