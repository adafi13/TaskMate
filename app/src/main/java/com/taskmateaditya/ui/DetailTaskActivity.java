package com.taskmateaditya.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.DriveScopes;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;
import com.taskmateaditya.data.Task;
import com.taskmateaditya.data.TaskViewModel;
import com.taskmateaditya.databinding.ActivityDetailTaskBinding;
import com.taskmateaditya.utils.DriveServiceHelper;
import com.taskmateaditya.utils.ReminderHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DetailTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "EXTRA_TASK_ID";
    private static final int REQUEST_CODE_SIGN_IN_DRIVE = 100;

    private ActivityDetailTaskBinding binding;
    private TaskViewModel taskViewModel;

    private String taskId = null;
    private Task currentTask;
    private Calendar alarmCalendar = Calendar.getInstance();

    // Variabel Google Drive
    private DriveServiceHelper mDriveServiceHelper;
    private Uri selectedLocalUri = null;
    private String cloudFileUrl = null;

    private final Executor mExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedLocalUri = result.getData().getData();
                    if (selectedLocalUri != null) {
                        showAttachmentInfo("File Siap Diupload (Klik Simpan)", true);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDetailTaskBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        taskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        setupDropdowns();
        setupInputBehavior();

        if (getIntent().hasExtra(EXTRA_TASK_ID)) {
            taskId = getIntent().getStringExtra(EXTRA_TASK_ID);
            if (taskId != null) {
                loadTaskData(taskId);
                binding.btnDelete.setVisibility(View.VISIBLE);
                binding.btnUpdate.setText("PERBARUI TUGAS");
            } else {
                setupNewTaskMode();
            }
        } else {
            setupNewTaskMode();
        }

        setupListeners();
    }

    // --- SETUP GOOGLE DRIVE AUTH ---
    private void requestDriveSignIn() {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, signInOptions);
        startActivityForResult(client.getSignInIntent(), REQUEST_CODE_SIGN_IN_DRIVE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SIGN_IN_DRIVE) {
            if (resultCode == RESULT_OK) {
                handleDriveSignIn();
                openFilePicker();
            } else {
                Toast.makeText(this, "Gagal login Google Drive", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleDriveSignIn() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            mDriveServiceHelper = new DriveServiceHelper(
                    DriveServiceHelper.getGoogleDriveService(this, account, "TaskMate")
            );
        }
    }

    private void checkDrivePermissionAndPickFile() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null && GoogleSignIn.hasPermissions(account, new Scope(DriveScopes.DRIVE_FILE))) {
            handleDriveSignIn();
            openFilePicker();
        } else {
            requestDriveSignIn();
        }
    }

    // --- LOGIKA UPLOAD ---
    private void prepareSaveTask() {
        String title = binding.editTextTitle.getText().toString().trim();
        String deadline = binding.editTextDeadline.getText().toString().trim();

        if (title.isEmpty() || deadline.isEmpty()) {
            Toast.makeText(this, "Judul dan Deadline wajib diisi.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Cek lagi apakah waktu alarm valid sebelum simpan (Double Check)
        if (binding.switchReminder.isChecked()) {
            if (alarmCalendar.getTimeInMillis() <= System.currentTimeMillis()) {
                Toast.makeText(this, "Waktu pengingat sudah lewat, mohon atur ulang waktu.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        if (selectedLocalUri != null) {
            if (mDriveServiceHelper == null) {
                GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
                if (account != null) handleDriveSignIn();

                if (mDriveServiceHelper == null) {
                    Toast.makeText(this, "Sesi Drive habis, silakan pilih file ulang", Toast.LENGTH_SHORT).show();
                    checkDrivePermissionAndPickFile();
                    return;
                }
            }
            uploadFileToDrive();
        } else {
            saveTaskToDatabase(cloudFileUrl);
        }
    }

    private void uploadFileToDrive() {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Mengupload ke Google Drive...");
        progressDialog.setMessage("Mohon tunggu");
        progressDialog.setCancelable(false);
        progressDialog.show();

        mExecutor.execute(() -> {
            try {
                String fileName = "task_file_" + System.currentTimeMillis();
                String fileId = mDriveServiceHelper.uploadFile(getContentResolver(), selectedLocalUri, fileName);

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (fileId != null) {
                        Toast.makeText(this, "Upload Berhasil!", Toast.LENGTH_SHORT).show();
                        String driveLink = "googledrive://" + fileId;
                        saveTaskToDatabase(driveLink);
                    } else {
                        Toast.makeText(this, "Gagal mendapatkan ID File", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Gagal Upload: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // --- STANDARD UI METHODS ---
    private void setupNewTaskMode() {
        taskId = null;
        binding.btnDelete.setVisibility(View.GONE);
        binding.btnUpdate.setText("TAMBAH TUGAS");
    }

    private void setupDropdowns() {
        binding.editTextPriority.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, getResources().getStringArray(R.array.priority_options)));
        binding.editTextCategory.setAdapter(new ArrayAdapter<>(this, R.layout.dropdown_item, getResources().getStringArray(R.array.category_options)));
    }

    private void setupInputBehavior() {
        binding.editTextDeadline.setFocusable(false);
        binding.editTextDeadline.setClickable(true);
        binding.editTextTime.setFocusable(false);
        binding.editTextTime.setClickable(true);
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnUpdate.setOnClickListener(v -> prepareSaveTask());
        binding.btnDelete.setOnClickListener(v -> showDeleteConfirmation());
        binding.editTextDeadline.setOnClickListener(v -> showDatePickerDialog());
        binding.editTextTime.setOnClickListener(v -> showTimePickerDialog());
        binding.editTextPriority.setOnClickListener(v -> binding.editTextPriority.showDropDown());
        binding.editTextCategory.setOnClickListener(v -> binding.editTextCategory.showDropDown());

        binding.btnAttachFile.setOnClickListener(v -> checkDrivePermissionAndPickFile());

        binding.btnRemoveAttachment.setOnClickListener(v -> {
            selectedLocalUri = null;
            cloudFileUrl = null;
            binding.layoutAttachmentInfo.setVisibility(View.GONE);
        });

        binding.tvAttachmentName.setOnClickListener(v -> openAttachment());
    }

    private void loadTaskData(String id) {
        taskViewModel.getTaskById(id).observe(this, task -> {
            if (task != null) {
                currentTask = task;
                binding.editTextTitle.setText(task.getTitle());
                binding.editTextMataKuliah.setText(task.getMataKuliah());
                binding.editTextDeadline.setText(task.getDeadline());
                binding.editTextNotes.setText(task.getNotes());
                binding.editTextPriority.setText(task.getPriority(), false);
                binding.editTextCategory.setText(task.getCategory(), false);
                binding.switchReminder.setChecked(task.isReminderActive());
                if (task.getReminderTime() > 0) {
                    SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
                    binding.editTextTime.setText(timeFormat.format(task.getReminderTime()));
                    alarmCalendar.setTimeInMillis(task.getReminderTime());
                }

                if (task.getAttachmentPath() != null && !task.getAttachmentPath().isEmpty()) {
                    cloudFileUrl = task.getAttachmentPath();
                    showAttachmentInfo("Lihat File Drive", false);
                }
            }
        });
    }

    private void saveTaskToDatabase(String finalAttachmentUrl) {
        boolean isReminderActive = binding.switchReminder.isChecked();
        long reminderTime = isReminderActive ? alarmCalendar.getTimeInMillis() : 0;

        if (isReminderActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                return;
            }
        }
        if (isReminderActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUserId = (currentUser != null) ? currentUser.getUid() : "";

        Task taskToSave;
        if (taskId == null) {
            taskToSave = new Task(
                    binding.editTextTitle.getText().toString().trim(),
                    binding.editTextMataKuliah.getText().toString(),
                    binding.editTextDeadline.getText().toString(),
                    binding.editTextNotes.getText().toString(),
                    binding.editTextPriority.getText().toString().isEmpty() ? "Sedang" : binding.editTextPriority.getText().toString(),
                    binding.editTextCategory.getText().toString().isEmpty() ? "Pribadi" : binding.editTextCategory.getText().toString(),
                    false,
                    reminderTime,
                    isReminderActive,
                    finalAttachmentUrl
            );
            taskToSave.setUserId(currentUserId);
            taskViewModel.insert(taskToSave);
        } else {
            if (currentTask != null) {
                currentTask.setTitle(binding.editTextTitle.getText().toString().trim());
                currentTask.setMataKuliah(binding.editTextMataKuliah.getText().toString());
                currentTask.setDeadline(binding.editTextDeadline.getText().toString());
                currentTask.setNotes(binding.editTextNotes.getText().toString());
                currentTask.setPriority(binding.editTextPriority.getText().toString());
                currentTask.setCategory(binding.editTextCategory.getText().toString());
                currentTask.setReminderTime(reminderTime);
                currentTask.setReminderActive(isReminderActive);
                currentTask.setAttachmentPath(finalAttachmentUrl);
                currentTask.setUserId(currentUserId);
                taskViewModel.update(currentTask);
                taskToSave = currentTask;
            } else { return; }
        }

        if (isReminderActive) ReminderHelper.setReminder(this, taskToSave);
        else ReminderHelper.cancelReminder(this, taskToSave);

        Toast.makeText(this, "Tugas disimpan!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        String[] mimeTypes = {"image/*", "application/pdf"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        filePickerLauncher.launch(intent);
    }

    private void showAttachmentInfo(String text, boolean isNew) {
        binding.layoutAttachmentInfo.setVisibility(View.VISIBLE);
        binding.tvAttachmentName.setText(text);
        if (isNew) binding.tvAttachmentName.setTextColor(getResources().getColor(android.R.color.holo_blue_dark));
        else binding.tvAttachmentName.setTextColor(getResources().getColor(android.R.color.black));
    }

    private void openAttachment() {
        if (cloudFileUrl != null && !cloudFileUrl.isEmpty()) {
            if (cloudFileUrl.startsWith("googledrive://")) {
                String fileId = cloudFileUrl.replace("googledrive://", "");
                String webUrl = "https://drive.google.com/file/d/" + fileId + "/view?usp=drivesdk";
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(webUrl));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Tidak ada aplikasi untuk membuka link ini", Toast.LENGTH_SHORT).show();
                }
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(cloudFileUrl));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show();
                }
            }
        } else if (selectedLocalUri != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedLocalUri, getContentResolver().getType(selectedLocalUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Tidak bisa membuka preview file lokal", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "File belum dipilih", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Hapus Tugas")
                .setMessage("Apakah Anda yakin ingin menghapus tugas ini?")
                .setPositiveButton("Hapus", (dialog, which) -> {
                    if (currentTask != null) {
                        ReminderHelper.cancelReminder(this, currentTask);
                        taskViewModel.delete(currentTask);
                        Toast.makeText(this, "Tugas dihapus", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // --- PERBAIKAN: MEMBATASI TANGGAL MASA LALU ---
    private void showDatePickerDialog() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, year, month, day) -> {
            alarmCalendar.set(Calendar.YEAR, year);
            alarmCalendar.set(Calendar.MONTH, month);
            alarmCalendar.set(Calendar.DAY_OF_MONTH, day);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            binding.editTextDeadline.setText(sdf.format(alarmCalendar.getTime()));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));

        // Set batas minimal tanggal ke hari ini (dikurangi 1 detik agar hari ini tetap bisa dipilih)
        datePickerDialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        datePickerDialog.show();
    }

    // --- PERBAIKAN: MEMBATASI WAKTU MASA LALU ---
    private void showTimePickerDialog() {
        Calendar c = Calendar.getInstance();
        int currentHour = c.get(Calendar.HOUR_OF_DAY);
        int currentMinute = c.get(Calendar.MINUTE);

        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            // Simpan waktu yang dipilih user ke variabel sementara
            Calendar tempCalendar = (Calendar) alarmCalendar.clone();
            tempCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            tempCalendar.set(Calendar.MINUTE, minute);
            tempCalendar.set(Calendar.SECOND, 0);

            // Cek apakah waktu yang dipilih kurang dari waktu sekarang
            if (tempCalendar.getTimeInMillis() < System.currentTimeMillis()) {
                Toast.makeText(this, "Alarm hanya dapat disetel untuk waktu yang akan datang.", Toast.LENGTH_SHORT).show();
            } else {
                // Jika valid, simpan ke alarmCalendar
                alarmCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
                alarmCalendar.set(Calendar.MINUTE, minute);
                alarmCalendar.set(Calendar.SECOND, 0);

                String timeFormat = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute);
                binding.editTextTime.setText(timeFormat);
                binding.switchReminder.setChecked(true);
            }
        }, currentHour, currentMinute, true).show();
    }
}