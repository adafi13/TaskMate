package com.taskmateaditya.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.api.services.drive.DriveScopes;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;
import com.taskmateaditya.R;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText editTextEmail;
    private TextInputEditText editTextPassword;
    private Button buttonSignIn;
    private Button buttonGoogle;
    private TextView textViewRegister;
    // --- PERBAIKAN: Deklarasi variabel harus ada di sini agar tidak merah ---
    private TextView textViewForgotPassword;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                int resultCode = result.getResultCode();
                Log.d("Login", "Google Sign-In result code: " + resultCode);

                if (resultCode == RESULT_OK) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    try {
                        GoogleSignInAccount account = task.getResult(ApiException.class);
                        if (account != null) {
                            Log.d("Login", "Google account retrieved: " + account.getEmail());
                            firebaseAuthWithGoogle(account.getIdToken());
                        } else {
                            Log.e("Login", "GoogleSignInAccount is null");
                            Toast.makeText(this, "Gagal mengambil akun Google.", Toast.LENGTH_SHORT).show();
                        }
                    } catch (ApiException e) {
                        Log.e("Login", "Google Sign-In failed. Status code: " + e.getStatusCode(), e);
                        String detail = "Unknown Error";
                        if (e.getStatusCode() == 10)
                            detail = "Developer Error (SHA-1 mismatch?)";
                        if (e.getStatusCode() == 7)
                            detail = "Network Error";
                        if (e.getStatusCode() == 12501)
                            detail = "Sign-in Canceled/Blocked";

                        Toast.makeText(this, "Login Error (" + e.getStatusCode() + "): " + detail, Toast.LENGTH_LONG)
                                .show();
                    }
                } else {
                    Log.e("Login", "Sign-in intent result not OK. Likely blocked by MIUI or canceled.");
                    if (resultCode == RESULT_CANCELED) {
                        Toast.makeText(this, "Login dibatalkan atau diblokir oleh sistem HP.", Toast.LENGTH_LONG)
                                .show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // --- KONFIGURASI GOOGLE SIGN IN ---
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Inisialisasi View
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        buttonSignIn = findViewById(R.id.buttonSignIn);
        buttonGoogle = findViewById(R.id.buttonGoogle);
        textViewRegister = findViewById(R.id.textViewRegister);

        // --- Hubungkan ke ID yang ada di XML ---
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword);

        // Listener Login Biasa
        buttonSignIn.setOnClickListener(v -> {
            String email = editTextEmail.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();
            handleSignIn(email, password);
        });

        // Listener Google Login
        buttonGoogle.setOnClickListener(v -> signInWithGoogle());

        // Listener Pindah ke Register
        textViewRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        // --- Listener Klik Lupa Password ---
        textViewForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToHome();
        }
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        googleSignInLauncher.launch(signInIntent);
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d("Login", "FirebaseAuth success: " + (user != null ? user.getUid() : "null"));
                        if (user != null) {
                            syncUserProfileToFirestore(user);
                        }
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::goToHome, 1000);
                    } else {
                        Exception e = task.getException();
                        Log.e("Login", "FirebaseAuth with Google failed", e);
                        String errorMsg = e != null ? e.getMessage() : "Unknown error";
                        Toast.makeText(LoginActivity.this, "Firebase Auth Error: " + errorMsg,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void syncUserProfileToFirestore(FirebaseUser user) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("uid", user.getUid());
                    userMap.put("name", user.getDisplayName());
                    userMap.put("email", user.getEmail());

                    boolean shouldUpdatePhoto = true;
                    if (documentSnapshot.exists()) {
                        String existingPhotoUrl = documentSnapshot.getString("photoUrl");
                        if (existingPhotoUrl != null && existingPhotoUrl.startsWith("googledrive://")) {
                            shouldUpdatePhoto = false;
                            Log.d("Login", "Found existing Drive photo URL, skipping overwrite");
                        }
                    }

                    if (shouldUpdatePhoto && user.getPhotoUrl() != null) {
                        userMap.put("photoUrl", user.getPhotoUrl().toString());
                    }

                    db.collection("users").document(user.getUid())
                            .set(userMap, com.google.firebase.firestore.SetOptions.merge())
                            .addOnSuccessListener(aVoid -> Log.d("Login", "Profile synced to Firestore"))
                            .addOnFailureListener(e -> Log.e("Login", "Failed to sync profile", e));
                })
                .addOnFailureListener(e -> Log.e("Login", "Failed to fetch existing profile", e));
    }

    private void handleSignIn(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Email dan Password harus diisi.", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonSignIn.setEnabled(false);
        buttonSignIn.setText("Loading...");

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        goToHome();
                    } else {
                        buttonSignIn.setEnabled(true);
                        buttonSignIn.setText("Login ke TaskMate");
                        Toast.makeText(LoginActivity.this,
                                "Login gagal! Format email atau kata sandi salah. Coba lagi.", Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private void goToHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}