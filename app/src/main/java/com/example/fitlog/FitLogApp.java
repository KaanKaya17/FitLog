package com.example.fitlog;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class FitLogApp extends Application {

    private static final String TAG = "FitLogApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Firebase'i başlat
        FirebaseApp.initializeApp(this);

        // Crashlytics'i yapılandır
        FirebaseCrashlytics crashlytics = FirebaseCrashlytics.getInstance();
        crashlytics.setCrashlyticsCollectionEnabled(true);

        // Analytics'i başlat
        FirebaseAnalytics.getInstance(this);

        Log.d(TAG, "Firebase Analytics ve Crashlytics başarıyla başlatıldı");
    }
}
