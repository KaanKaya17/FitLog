package com.example.fitlog;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.fitlog.data.OfflineQueue;
import com.google.firebase.FirebaseApp;

public class FitLogApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        RetrofitClient.init(this);

        PrefManager pref = new PrefManager(this);
        AppCompatDelegate.setDefaultNightMode(SettingsFragment.nightModeOf(pref.getThemeMode()));

        FirebaseApp.initializeApp(this);
        // Analytics/Crashlytics manifestte kapalı başlar; yalnızca kullanıcı onayıyla açılır.
        ConsentManager.apply(this);

        // Önceki oturumdan kalan çevrimdışı kayıt varsa bağlantı gelince gönder.
        if (AuthManager.hasSession(this)) {
            OfflineQueue.get(this).scheduleSync();
            com.example.fitlog.data.WeeklySummaryWorker.schedule(this);
        }
    }
}
