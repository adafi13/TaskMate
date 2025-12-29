package com.taskmateaditya.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.taskmateaditya.databinding.ActivitySplash2Binding;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Splash2Activity extends AppCompatActivity {

    private ActivitySplash2Binding binding;
    private FusedLocationProviderClient fusedClient;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false))) {
                    fetchLocation();
                } else {
                    useLocaleFallback();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplash2Binding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fusedClient = LocationServices.getFusedLocationProviderClient(this);

        // 1. Set sapaan & jalankan animasi pulsing pada teks lokasi
        setInitialGreetingAndAnimation();

        // 2. Animasi Tombol Muncul (Slide Up)
        animateButtonEntry();

        // 3. Cek lokasi
        checkOrRequestPermission();

        binding.btnGetStarted.setOnClickListener(v -> {
            startActivity(new Intent(Splash2Activity.this, LoginActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        });
    }

    private void setInitialGreetingAndAnimation() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greet = (hour < 11) ? "Selamat Pagi" : (hour < 15) ? "Selamat Siang" : (hour < 18) ? "Selamat Sore" : "Selamat Malam";

        binding.greetingText.setText(greet + "");
        binding.locationText.setText("Mencari lokasi...");

        // Efek Pulsing: Teks lokasi berdenyut tipis selama mencari sinyal
        binding.locationText.setAlpha(0.4f);
        binding.locationText.animate()
                .alpha(1f)
                .setDuration(1000)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        binding.locationText.animate().alpha(0.4f).setDuration(1000).withEndAction(this).start();
                    }
                }).start();
    }

    private void animateButtonEntry() {
        // Tombol mulai dari posisi lebih rendah (100px kebawah) dan transparan
        binding.btnGetStarted.setTranslationY(100f);
        binding.btnGetStarted.setAlpha(0f);

        // Muncul dengan halus setelah delay 600ms
        binding.btnGetStarted.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(800)
                .setStartDelay(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }

    private void checkOrRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLocation();
        } else {
            requestPermissionsLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLocation() {
        fusedClient.getLastLocation().addOnSuccessListener(this, location -> {
            // Hentikan animasi pulsing sebelum update lokasi
            binding.locationText.animate().cancel();
            binding.locationText.setAlpha(1f);

            if (location != null) {
                updateCityInfo(location);
            } else {
                useLocaleFallback();
            }
        });
    }

    private void updateCityInfo(@NonNull Location loc) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);

            if (addresses != null && !addresses.isEmpty()) {
                String city = addresses.get(0).getLocality();
                if (city == null) city = addresses.get(0).getSubAdminArea();

                String finalCity = (city != null) ? city : "Indonesia";
                String countryCode = addresses.get(0).getCountryCode();

                binding.locationText.setText(finalCity);
                binding.tagline.setText("Siap mendukung produktivitasmu di " + finalCity + " 🌟");
                binding.flagTopRight.setText(countryCodeToFlagEmoji(countryCode));
            }
        } catch (Exception e) {
            useLocaleFallback();
        }
    }

    private void useLocaleFallback() {
        binding.locationText.setText("Ups! Lokasi tidak terjangkau saat ini.");
        binding.flagTopRight.setText("🇮🇩");
        // Tetap ramah
        binding.tagline.setText("Tetap semangat dan produktif, di mana pun Anda berada! ✨");
    }


    private String countryCodeToFlagEmoji(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) return "🏳️";
        int firstLetter = Character.codePointAt(countryCode.toUpperCase(), 0) - 0x41 + 0x1F1E6;
        int secondLetter = Character.codePointAt(countryCode.toUpperCase(), 1) - 0x41 + 0x1F1E6;
        return new String(Character.toChars(firstLetter)) + new String(Character.toChars(secondLetter));
    }
}