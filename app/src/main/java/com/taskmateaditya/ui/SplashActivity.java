package com.taskmateaditya.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.taskmateaditya.R;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Delay 2000ms (2 detik) sudah cukup ideal.
        // 3 detik (3000ms) seringkali dianggap terlalu lama oleh pengguna yang terburu-buru.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkLoginStatus();
        }, 2000);
    }

    private void checkLoginStatus() {
        // 1. Ambil user yang sedang aktif dari Firebase
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        Intent intent;

        if (currentUser != null) {
            // JIKA SUDAH LOGIN -> Langsung ke Home (Melewati Login/Splash2)
            intent = new Intent(SplashActivity.this, HomeActivity.class);
        } else {
            // JIKA BELUM LOGIN -> Ke Splash2Activity (Halaman Welcome)
            intent = new Intent(SplashActivity.this, Splash2Activity.class);
        }

        startActivity(intent);

        // 2. Tambahkan Animasi Transisi Halus (Fade In / Fade Out)
        // Ini membuat aplikasi terasa lebih mahal/premium
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        // 3. Tutup Splash agar user tidak bisa kembali ke sini saat tekan tombol Back
        finish();
    }
}