package com.example.fitlog;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

/**
 * Oturumun hâlâ geçerli olduğunu ve kullanıcının engellenmediğini kontrol eder.
 *
 * Not: Bu yalnızca kullanıcı deneyimi içindir. Asıl koruma sunucudadır —
 * engellenmiş kullanıcılar RLS politikaları nedeniyle hiçbir veriye erişemez.
 *
 * Kullanım: UserStatusChecker.check(activity);  // onResume() içinde
 */
public class UserStatusChecker {

    private static final String TAG = "UserStatusChecker";

    public static void check(Activity activity) {
        Context appCtx = activity.getApplicationContext();
        PrefManager pref = new PrefManager(appCtx);
        int userId = pref.getId();
        if (userId == -1) return; // giriş yapılmamış

        // Eski sürümden kalan (Supabase Auth oturumu olmayan) giriş: yeniden giriş iste.
        if (!AuthManager.hasSession(appCtx)) {
            forceLogout(activity, R.string.auth_relogin_required);
            return;
        }

        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                retrofit2.Response<List<User>> response = api.getUserById("eq." + userId).execute();

                if (!AuthManager.hasSession(appCtx)) {
                    // Token yenilenemedi ve oturum silindi.
                    forceLogout(activity, R.string.error_session_expired);
                    return;
                }

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    User user = response.body().get(0);
                    if (user.isBlocked()) {
                        AuthManager.signOut(appCtx);
                        forceLogout(activity, R.string.auth_error_blocked);
                    } else {
                        // Başka cihazda yapılan profil/hedef değişikliklerini senkronize et.
                        pref.saveUser(user);
                    }
                } else if (!response.isSuccessful()) {
                    Log.w(TAG, "Kullanıcı durumu alınamadı. HTTP " + response.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "Kullanıcı durumu kontrol edilemedi", e);
            }
        }).start();
    }

    private static void forceLogout(Activity activity, int message) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            AuthManager.clearLocalSession(activity.getApplicationContext());
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            Navigation.toLoginAndClear(activity);
        });
    }
}
