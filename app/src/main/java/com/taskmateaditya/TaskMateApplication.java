package com.taskmateaditya;

import android.app.Application;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

/**
 * Application class — single source of truth untuk shared resources.
 * OkHttpClient dibuat sekali di sini dan di-reuse di seluruh app,
 * menghindari duplikasi thread pool & connection pool yang boros memori.
 */
public class TaskMateApplication extends Application {

    private static TaskMateApplication instance;
    private OkHttpClient sharedHttpClient;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Satu OkHttpClient untuk seluruh app
        sharedHttpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static TaskMateApplication getInstance() {
        return instance;
    }

    public OkHttpClient getHttpClient() {
        return sharedHttpClient;
    }
}
