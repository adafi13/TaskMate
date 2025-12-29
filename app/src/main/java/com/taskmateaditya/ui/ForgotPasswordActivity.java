package com.taskmateaditya.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.taskmateaditya.R;

public class ForgotPasswordActivity extends AppCompatActivity {

    private TextInputEditText editTextEmail;
    private Button btnSendReset;
    private ImageButton btnBack;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        // 1. Inisialisasi View
        editTextEmail = findViewById(R.id.editTextEmailReset);
        btnSendReset = findViewById(R.id.btnSendReset);
        btnBack = findViewById(R.id.btnBack);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        // 2. Setup Listener
        btnBack.setOnClickListener(v -> finish());
        tvBackToLogin.setOnClickListener(v -> finish());

        // 3. Logika Reset Password
        btnSendReset.setOnClickListener(v -> handleResetPassword());
    }

    private void handleResetPassword() {
        String email = editTextEmail.getText().toString().trim();

        // Validasi 1: Cek Kosong
        if (TextUtils.isEmpty(email)) {
            editTextEmail.setError("Email wajib diisi");
            editTextEmail.requestFocus();
            return;
        }

        // Validasi 2: Cek Format Email (agar tidak membuang request ke server)
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editTextEmail.setError("Format email tidak valid");
            editTextEmail.requestFocus();
            return;
        }

        // Matikan tombol saat loading
        btnSendReset.setEnabled(false);
        btnSendReset.setText("Sedang Mengirim...");

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    btnSendReset.setEnabled(true);
                    btnSendReset.setText("Pulihkan Akun");

                    if (task.isSuccessful()) {
                        // SUKSES: Tampilkan Dialog Edukasi SPAM
                        showSuccessDialog(email);
                    } else {
                        // GAGAL: Analisis Error
                        String errorMessage;
                        try {
                            throw task.getException();
                        } catch (FirebaseAuthInvalidUserException e) {
                            errorMessage = "Email tidak terdaftar atau telah dinonaktifkan.";
                        } catch (Exception e) {
                            errorMessage = "Gagal mengirim email. Periksa koneksi internet.";
                        }
                        showSnackbar(errorMessage, true);
                    }
                });
    }

    private void showSuccessDialog(String email) {
        new AlertDialog.Builder(this)
                .setTitle("Email Terkirim! 📧")
                .setMessage("Link untuk mereset password telah dikirim ke:\n" + email + "\n\n" +
                        "⚠️ PENTING: JIKA TIDAK ADA DI INBOX, MOHON PERIKSA FOLDER SPAM ATAU PROMOSI.")
                .setPositiveButton("Mengerti, Login Sekarang", (dialog, which) -> {
                    finish(); // Kembali ke halaman Login
                })
                .setCancelable(false) // Tidak bisa ditutup dengan klik luar
                .show();
    }

    private void showSnackbar(String message, boolean isError) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG);
        if (isError) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.tm_green));
        }
        snackbar.setTextColor(ContextCompat.getColor(this, android.R.color.white));
        snackbar.show();
    }
}