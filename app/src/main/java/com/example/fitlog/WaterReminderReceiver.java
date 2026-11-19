package com.example.fitlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.fitlog.data.ServiceLocator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Periyodik su hatırlatması: sessiz saatlerde ve hedefe ulaşıldıysa bildirim göstermez. */
public class WaterReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "WaterReminder";

    @Override
    public void onReceive(Context context, Intent intent) {
        PrefManager pref = new PrefManager(context);
        int hour = LocalTime.now().getHour();
        if (ReminderScheduler.isQuietHour(hour, pref.getQuietStartHour(), pref.getQuietEndHour())) return;
        if (pref.getId() == -1 || !AuthManager.hasSession(context)) return;

        int waterGoal = pref.getWaterGoal();
        Context appCtx = context.getApplicationContext();
        PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                List<WaterLog> logs = ServiceLocator.repository(appCtx).getWater(LocalDate.now());
                int total = 0;
                for (WaterLog w : logs) total += w.getAmount_ml();

                if (total >= waterGoal) return; // hedef tamam, rahatsız etme
                if (total >= waterGoal * 0.8f) NotificationHelper.showAlmostThereNotification(appCtx);
                else NotificationHelper.showWaterNotification(appCtx);
            } catch (Exception e) {
                Log.w(TAG, "Su verisi alınamadı, standart hatırlatma gösteriliyor", e);
                NotificationHelper.showWaterNotification(appCtx);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }
}
