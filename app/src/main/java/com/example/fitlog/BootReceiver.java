package com.example.fitlog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Cihaz yeniden başlayınca alarmlar silinir; hatırlatıcıyı yeniden kur. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            ReminderScheduler.apply(context);
            MealReminderReceiver.apply(context);
            com.example.fitlog.data.OfflineQueue.get(context).scheduleSync();
        }
    }
}
