package com.example.fitlog;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * Analitik ve hata raporu onayı (KVKK / GDPR / Play politikası).
 *
 * Firebase Analytics ve Crashlytics manifestte varsayılan olarak KAPALI başlar;
 * yalnızca kullanıcı onay verirse açılır. Onay oturumdan bağımsızdır: çıkış
 * yapınca silinmesin diye PrefManager'dan ayrı bir dosyada tutulur.
 */
public final class ConsentManager {

    private static final String PREFS = "fitlog_consent";
    private static final String KEY_ASKED = "analytics_consent_asked";
    private static final String KEY_GRANTED = "analytics_consent_granted";

    private ConsentManager() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean wasAsked(Context ctx) { return prefs(ctx).getBoolean(KEY_ASKED, false); }

    public static boolean isGranted(Context ctx) { return prefs(ctx).getBoolean(KEY_GRANTED, false); }

    /** Kullanıcının seçimini kaydeder ve Firebase toplama ayarlarına uygular. */
    public static void setGranted(Context ctx, boolean granted) {
        prefs(ctx).edit().putBoolean(KEY_ASKED, true).putBoolean(KEY_GRANTED, granted).apply();
        apply(ctx);
    }

    /** Kayıtlı onayı Firebase'e uygular (uygulama açılışında çağrılır). */
    public static void apply(Context ctx) {
        // Kendi Firebase projesi olmayan kurulumlarda (yer tutucu google-services.json)
        // onay verilse bile hiçbir şey toplanmaz.
        boolean granted = isGranted(ctx) && BuildConfig.FIREBASE_CONFIGURED;
        FirebaseAnalytics.getInstance(ctx).setAnalyticsCollectionEnabled(granted);
        // Debug build'lerde Crashlytics'e hiç rapor gönderme.
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(granted && !BuildConfig.DEBUG);
        if (!granted) FirebaseAnalytics.getInstance(ctx).resetAnalyticsData();
    }

    /**
     * Henüz sorulmadıysa onay penceresini gösterir.
     * @return gösterilen pencere ya da null
     */
    public static androidx.appcompat.app.AlertDialog askIfNeeded(android.app.Activity activity) {
        if (wasAsked(activity)) return null;
        return new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.consent_title)
                .setMessage(R.string.consent_message)
                .setCancelable(false)
                .setPositiveButton(R.string.consent_accept, (d, w) -> setGranted(activity, true))
                .setNegativeButton(R.string.consent_decline, (d, w) -> setGranted(activity, false))
                .setNeutralButton(R.string.privacy_policy, (d, w) -> {
                    // Politika okunduktan sonra uygulamaya dönünce tekrar sorulur.
                    openPrivacyPolicy(activity);
                })
                .show();
    }

    public static void openPrivacyPolicy(Context ctx) {
        android.net.Uri uri = android.net.Uri.parse(ctx.getString(R.string.privacy_policy_url));
        try {
            new androidx.browser.customtabs.CustomTabsIntent.Builder().build().launchUrl(ctx, uri);
        } catch (RuntimeException e) {
            try {
                ctx.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (RuntimeException ignored) {}
        }
    }
}
