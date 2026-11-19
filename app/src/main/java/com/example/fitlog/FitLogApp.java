package com.example.fitlog;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class FitLogApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RetrofitClient.init(this);

        FirebaseApp.initializeApp(this);
        // Debug build'lerde Crashlytics'e rapor gönderme.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        FirebaseAnalytics.getInstance(this);
    }
}
