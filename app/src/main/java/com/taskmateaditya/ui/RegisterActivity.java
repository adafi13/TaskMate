package com.taskmateaditya.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText editTextUsername, editTextEmail, editTextPassword, editTextConfirmPassword;
    private Button buttonRegister;
    private TextView textViewLogin;
    private ImageButton backButton;
    private ProgressBar progressBar; // Tambahkan progress bar di layout jika mau (opsional)

    private FirebaseAuth mAuth; // Variabel Firebase

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Inisialisasi Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Hubungkan View
        editTextUsername = findViewById(R.id.editTextUsername);
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        buttonRegister = findViewById(R.id.buttonRegister);
        textViewLogin = findViewById(R.id.textViewLogin);
        backButton = findViewById(R.id.backButton);

        buttonRegister.setOnClickListener(v -> validateAndRegister());

        textViewLogin.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

        backButton.setOnClickListener(v -> finish());
    }

    private void validateAndRegister() {
        String username = editTextUsername.getText().toString().trim();
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "Semua kolom wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validasi format email menggunakan Regex
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Format email tidak valid. Silakan masukkan email yang benar.", Toast.LENGTH_SHORT).show();
            return;
        }


        if (password.length() < 8 || !Character.isUpperCase(password.charAt(0))) {
            Toast.makeText(this, "Oops! Kata Sandi minimal 8 karakter & diawali huruf besar.", Toast.LENGTH_SHORT).show();
            return;
        }


        // --- PROSES DAFTAR KE FIREBASE ---
        buttonRegister.setEnabled(false); // Matikan tombol agar tidak diklik 2x
        buttonRegister.setText("Loading...");

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sukses
                        FirebaseUser user = mAuth.getCurrentUser();
                        Toast.makeText(RegisterActivity.this, "Registrasi berhasil! Selamat datang di TaskMate! 🚀", Toast.LENGTH_SHORT).show();

                        // Pindah ke Home langsung atau ke Login
                        Intent intent = new Intent(RegisterActivity.this, HomeActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        // Gagal
                        buttonRegister.setEnabled(true);
                        buttonRegister.setText("REGISTER");
                        Toast.makeText(RegisterActivity.this, "Oops! Email ini sudah terdaftar. Silakan coba dengan email lain.", Toast.LENGTH_LONG).show();
                    }
                });
    }
}