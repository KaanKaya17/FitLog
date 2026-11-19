package com.example.fitlog;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.fitlog.data.OfflineQueue;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

public class FitLogApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RetrofitClient.init(this);

        PrefManager pref = new PrefManager(this);
        AppCompatDelegate.setDefaultNightMode(SettingsFragment.nightModeOf(pref.getThemeMode()));

        FirebaseApp.initializeApp(this);
        // Debug build'lerde Crashlytics'e rapor gönderme.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG);
        FirebaseAnalytics.getInstance(this);

        // Önceki oturumdan kalan çevrimdışı kayıt varsa bağlantı gelince gönder.
        if (AuthManager.hasSession(this)) {
            OfflineQueue.get(this).scheduleSync();
            com.example.fitlog.data.WeeklySummaryWorker.schedule(this);
        }
    }
}
