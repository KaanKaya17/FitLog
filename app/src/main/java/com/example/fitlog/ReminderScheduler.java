package com.example.fitlog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Su hatırlatıcısını kullanıcı ayarlarına göre kurar / iptal eder. */
public final class ReminderScheduler {

    private static final int REQUEST_CODE = 100;

    private ReminderScheduler() {}

    /** Ayarlara göre kurar (kapalıysa iptal eder). */
    public static void apply(Context ctx) {
        PrefManager p = new PrefManager(ctx);
        if (p.isWaterReminderEnabled()) schedule(ctx, p.getReminderIntervalHours());
        else cancel(ctx);
    }

    public static void schedule(Context ctx, int intervalHours) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long interval = Math.max(1, intervalHours) * 60L * 60L * 1000L;
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + interval, interval, pendingIntent(ctx));
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent(ctx));
    }

    private static PendingIntent pendingIntent(Context ctx) {
        Intent intent = new Intent(ctx, WaterReminderReceiver.class);
        return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Saat sessiz aralıkta mı? start > end ise gece yarısını kapsar
     * (ör. 23 → 8: 23:00-07:59 sessiz). start == end ise sessiz saat yok.
     */
    public static boolean isQuietHour(int hour, int start, int end) {
        if (start == end) return false;
        if (start < end) return hour >= start && hour < end;
        return hour >= start || hour < end;
    }
}
