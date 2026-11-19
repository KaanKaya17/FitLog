package com.example.fitlog;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCM_SERVICE";

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        // Token değeri loglanmaz (cihaza bildirim göndermek için kullanılabilir).
        Log.d(TAG, "Yeni FCM token oluşturuldu.");

        PrefManager prefManager = new PrefManager(this);
        prefManager.setFcmToken(token);

        // Kullanıcı giriş yapmışsa veritabanını güncelle
        int userId = prefManager.getId();
        if (userId != -1 && AuthManager.hasSession(this)) {
            uploadTokenToSupabase(userId, token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        // Uygulama arka plandayken FCM bildirimleri otomatik gösterir.
        Log.d(TAG, "Ön planda mesaj alındı.");
    }

    private void uploadTokenToSupabase(int userId, String token) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                Map<String, Object> updateMap = new HashMap<>();
                updateMap.put("fcm_token", token);

                retrofit2.Response<Void> response = api.updateUser("eq." + userId, updateMap).execute();
                if (!response.isSuccessful()) {
                    Log.w(TAG, "FCM token güncellenemedi. HTTP " + response.code());
                }
            } catch (Exception e) {
                Log.w(TAG, "FCM token güncellenemedi", e);
            }
        }).start();
    }
}
