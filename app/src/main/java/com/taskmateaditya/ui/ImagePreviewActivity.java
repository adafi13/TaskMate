package com.taskmateaditya.ui;

import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.taskmateaditya.R;

public class ImagePreviewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_preview);

        ImageView imgFull = findViewById(R.id.imgFullProfile);
        ImageButton btnClose = findViewById(R.id.btnClose);

        // Ambil data URI dari Intent
        String uriString = getIntent().getStringExtra("IMAGE_URI");
        if (uriString != null) {
            imgFull.setImageURI(Uri.parse(uriString));
        }

        // Klik tombol kembali untuk menutup preview
        btnClose.setOnClickListener(v -> finishAfterTransition());
    }
}