package com.taskmateaditya.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Pair;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// PERBAIKAN: Menggunakan NetHttpTransport (Standar Java) agar tidak perlu library AndroidHttp khusus
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DriveServiceHelper {
    private final Executor mExecutor = Executors.newSingleThreadExecutor();
    private final Drive mDriveService;

    public DriveServiceHelper(Drive driveService) {
        mDriveService = driveService;
    }

    /**
     * 1. METHOD STATIC
     */
    public static Drive getGoogleDriveService(Context context, GoogleSignInAccount account, String appName) {
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccount(account.getAccount());

        // PERBAIKAN: Menggunakan new NetHttpTransport()
        return new Drive.Builder(
                new NetHttpTransport(),
                new GsonFactory(),
                credential)
                .setApplicationName(appName)
                .build();
    }

    /**
     * 2. UPLOAD FILE
     */
    public String uploadFile(ContentResolver contentResolver, Uri uri, String fileName) throws IOException {
        File metadata = new File()
                .setParents(Collections.singletonList("root"))
                .setMimeType("image/jpeg")
                .setName(fileName);

        InputStream inputStream = contentResolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("Gagal membaca file lokal");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while (-1 != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
        }
        inputStream.close();

        ByteArrayContent mediaContent = new ByteArrayContent("image/jpeg", outputStream.toByteArray());

        File googleFile = mDriveService.files().create(metadata, mediaContent)
                .setFields("id")
                .execute();

        if (googleFile == null) {
            throw new IOException("Null result dari Drive");
        }

        return googleFile.getId();
    }

    /**
     * 3. READ FILE
     */
    public Task<Pair<String, byte[]>> readFile(String fileId) {
        return Tasks.call(mExecutor, () -> {
            File metadata = mDriveService.files().get(fileId).execute();
            String name = metadata.getName();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            mDriveService.files().get(fileId).executeMediaAndDownloadTo(outputStream);

            return Pair.create(name, outputStream.toByteArray());
        });
    }
}