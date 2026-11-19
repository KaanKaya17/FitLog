package com.example.fitlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WaterReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("WaterReminder", "Alarm triggered!");

        // 1. Uyku saatleri kontrolü (Gece 12 ile Sabah 8 arası)
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        if (hour >= 0 && hour < 8) {
            Log.d("WaterReminder", "Quiet hours (00:00 - 08:00), skipping notification.");
            return;
        }

        PrefManager pref = new PrefManager(context);
        int userId = pref.getId();
        if (userId == -1) {
            Log.d("WaterReminder", "User not logged in, skipping.");
            return;
        }

        int waterGoal = pref.getWaterGoal();
        Context appCtx = context.getApplicationContext();
        // Arka plan işi bitene kadar receiver'ı canlı tut.
        PendingResult pendingResult = goAsync();

        // 2. Supabase'den bugünün su loglarını al ve toplamı hesapla
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

                String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
                String userIdFilter = "eq." + userId;
                String dateFilter = "gte." + today + "T00:00:00Z";

                retrofit2.Response<List<WaterLog>> response = api.getWaterLogs(userIdFilter, dateFilter).execute();

                int totalWater = 0;
                if (response.isSuccessful() && response.body() != null) {
                    for (WaterLog w : response.body()) {
                        totalWater += w.getAmount_ml();
                    }
                }

                // 3. İlerlemeye göre bildirim at 
                if (totalWater >= waterGoal) {
                    Log.d("WaterReminder", "Daily goal reached, no notification needed.");
                    // Hedef tamamlandı, hiçbir şey yapma (kullanıcıyı rahatsız etme)
                } else if (totalWater >= waterGoal * 0.8f) {
                    // %80 ve üzeri suyunu içmiş (Örn: 2000'in 1600'ünü içmiş). Cesaretlendirici bildirim at!
                    Log.d("WaterReminder", "Almost there! Sending 'almost' notification.");
                    NotificationHelper.showAlmostThereNotification(appCtx);
                } else {
                    // Normal uyarı
                    Log.d("WaterReminder", "Sending regular notification.");
                    NotificationHelper.showWaterNotification(appCtx);
                }

            } catch (Exception e) {
                Log.e("WaterReminder", "Failed to fetch water data: " + e.getMessage());
                // İstisnai durumlarda (internetsizlik v.b) standart hatırlatma atsın mı?
                // Hadi interneti yoksa yine hatırlatma atalım madem :)
                NotificationHelper.showWaterNotification(appCtx);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
