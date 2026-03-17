package com.example.fitlog;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.util.List;

/**
 * Kullanıcının Supabase'deki is_blocked durumunu kontrol eder.
 * Engellenmiş kullanıcıları login sayfasına yönlendirir ve oturumu temizler.
 *
 * Kullanım:
 *   UserStatusChecker.check(this);   ← onResume() içinde çağır
 */
public class UserStatusChecker {

    private static final String TAG = "UserStatusChecker";

    /**
     * Mevcut kullanıcının blok durumunu Supabase'den kontrol eder.
     * Bloklu ise oturumu kapatır ve UserLogin sayfasına yönlendirir.
     *
     * @param activity Kontrol yapılacak Activity (context + runOnUiThread için)
     */
    public static void check(Activity activity) {
        PrefManager pref = new PrefManager(activity);
        int userId = pref.getId();

        Log.d(TAG, "check() başlatıldı. Mevcut userId: " + userId + " - Activity: " + activity.getClass().getSimpleName());

        // Giriş yapmamış kullanıcı için kontrol yapma
        if (userId == -1) {
            Log.d(TAG, "Kullanıcı giriş yapmamış (userId == -1). Kontrol iptal edildi.");
            return;
        }

        String apiKey     = BuildConfig.SUPABASE_API_KEY;
        String authHeader = "Bearer " + apiKey;
        String userIdFilter = "eq." + userId;

        new Thread(() -> {
            try {
                Log.d(TAG, "Supabase API isteği gönderiliyor... Filtre: " + userIdFilter);
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

                retrofit2.Response<List<User>> response = api.getUserById(
                        apiKey,
                        authHeader,
                        "application/json",
                        userIdFilter
                ).execute();

                Log.d(TAG, "API Yanıt Kodu: " + response.code() + ", Başarılı mı: " + response.isSuccessful());

                if (response.isSuccessful() && response.body() != null) {
                    List<User> userList = response.body();
                    Log.d(TAG, "API Yanıtı alındı. Liste boyutu: " + userList.size());

                    if (!userList.isEmpty()) {
                        User user = userList.get(0);
                        Log.d(TAG, "Kullanıcı bulundu -> ID: " + user.getId() + 
                                ", Email: " + user.getEmail() + 
                                ", is_blocked: " + user.isBlocked());

                        if (user.isBlocked()) {
                            Log.w(TAG, "🚨 DİKKAT: Kullanıcı bloklu! Oturum kapatılıyor... userId=" + userId);
                            activity.runOnUiThread(() -> {
                                if (activity.isFinishing() || activity.isDestroyed()) {
                                    Log.d(TAG, "Activity kapanıyor, yönlendirme iptal edildi.");
                                    return;
                                }
                                
                                Log.d(TAG, "Kullanıcı verileri temizleniyor ve login ekranına yönlendiriliyor.");
                                // Oturumu temizle
                                pref.clearUser();
                                // Kullanıcıya bildir
                                Toast.makeText(
                                        activity,
                                        "Hesabınız engellenmiştir. Lütfen destek ekibiyle iletişime geçin.",
                                        Toast.LENGTH_LONG
                                ).show();
                                // Login sayfasına yönlendir (back stack temizlenerek)
                                Navigation.toLoginAndClear(activity);
                            });
                        } else {
                            Log.d(TAG, "✅ Kullanıcı bloklu DEĞİL. İşleme devam ediliyor.");
                        }
                    } else {
                        Log.e(TAG, "Sunucu başarılı yanıt verdi fakat kullanıcı listesi BOŞ geldi!");
                    }
                } else {
                    String errorBody = response.errorBody() != null ? response.errorBody().string() : "Bilinmeyen hata";
                    Log.e(TAG, "HATA: Kullanıcı bilgisi alınamadı. HTTP Kodu: " + response.code() + " - Detay: " + errorBody);
                }

            } catch (Exception e) {
                Log.e(TAG, "İSTİSNA (Exception): Bağlantı hatası veya JSON parse hatası -> " + e.getMessage(), e);
            }
        }).start();
    }
}
