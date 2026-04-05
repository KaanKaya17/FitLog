package com.example.fitlog;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Yeni FCM Token oluşturuldu: " + token);

        // Token'ı yerel belleğe kaydet
        PrefManager prefManager = new PrefManager(this);
        prefManager.setFcmToken(token);

        // Kullanıcı giriş yapmışsa anında veritabanını güncelle
        int userId = prefManager.getId();
        if (userId != -1) {
            uploadTokenToSupabase(userId, token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Firebase Cloud Messaging, uygulama arkaplanda iken bildirimleri otomatik gösterir.
        // Uygulama ön plandayken bildirimlerin nasıl ele alınacağını buraya yazabilirsiniz.
        Log.d(TAG, "Ön planda mesaj alındı. Gönderen: " + remoteMessage.getFrom());
        
        if (remoteMessage.getNotification() != null) {
            Log.d(TAG, "Mesaj başlığı: " + remoteMessage.getNotification().getTitle());
            Log.d(TAG, "Mesaj gövdesi: " + remoteMessage.getNotification().getBody());
            // isterseniz bir Toast veya custom NotificationManager gösterebilirsiniz.
        }
    }

    private void uploadTokenToSupabase(int userId, String token) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                Map<String, Object> updateMap = new HashMap<>();
                updateMap.put("fcm_token", token);

                api.updateUserProfile(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        "eq." + userId,
                        updateMap
                ).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Log.d(TAG, "Supabase Token güncellemesi: " + response.isSuccessful());
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Log.e(TAG, "Supabase Token güncellemesi başarısız: " + t.getMessage());
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Hata: " + e.getMessage());
            }
        }).start();
    }
}
