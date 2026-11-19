package com.example.fitlog;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.example.fitlog.data.ServiceLocator;
import com.example.fitlog.domain.MealType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Öğün hatırlatıcısı: kahvaltı, öğle ve akşam yemeği için günde birer kez,
 * o öğün henüz girilmemişse bildirim gösterir.
 */
public class MealReminderReceiver extends BroadcastReceiver {

    private static final String TAG = "MealReminder";
    private static final String EXTRA_MEAL = "meal_type";
    private static final String CHANNEL_ID = "meal_reminder_channel";

    /** Öğün → hatırlatma saati. Bu saate kadar girilmemişse hatırlatılır. */
    static final MealType[] MEALS = {MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER};
    static final int[] HOURS = {10, 14, 20};

    // ── Kurulum ────────────────────────────────────────────────────────

    /** Ayara göre kurar ya da iptal eder. */
    public static void apply(Context ctx) {
        if (new PrefManager(ctx).isMealReminderEnabled()) schedule(ctx);
        else cancel(ctx);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        ZonedDateTime now = ZonedDateTime.now();
        for (int i = 0; i < MEALS.length; i++) {
            long first = nextTrigger(now, HOURS[i]).toInstant().toEpochMilli();
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, AlarmManager.INTERVAL_DAY,
                    pendingIntent(ctx, i));
        }
    }

    static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        for (int i = 0; i < MEALS.length; i++) am.cancel(pendingIntent(ctx, i));
    }

    /** Verilen saatin bir sonraki geçişi (bugün geçtiyse yarın). */
    static ZonedDateTime nextTrigger(ZonedDateTime now, int hour) {
        ZonedDateTime t = now.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        return t.isAfter(now) ? t : t.plusDays(1);
    }

    private static PendingIntent pendingIntent(Context ctx, int index) {
        Intent intent = new Intent(ctx, MealReminderReceiver.class).putExtra(EXTRA_MEAL, MEALS[index].key);
        return PendingIntent.getBroadcast(ctx, 200 + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** O öğün için bugün kayıt yoksa hatırlatılmalı. */
    static boolean shouldRemind(MealType meal, List<String> loggedTypes) {
        for (String t : loggedTypes) if (MealType.fromKey(t) == meal) return false;
        return true;
    }

    // ── Alarm geldiğinde ───────────────────────────────────────────────

    @Override
    public void onReceive(Context context, Intent intent) {
        MealType meal = MealType.fromKey(intent.getStringExtra(EXTRA_MEAL));
        if (meal == null) return;
        PrefManager pref = new PrefManager(context);
        if (!pref.isMealReminderEnabled()) return;
        if (ReminderScheduler.isQuietHour(LocalTime.now().getHour(), pref.getQuietStartHour(), pref.getQuietEndHour())) return;
        if (pref.getId() == -1 || !AuthManager.hasSession(context)) return;

        Context appCtx = context.getApplicationContext();
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                List<String> types = new ArrayList<>();
                for (MealLog m : ServiceLocator.repository(appCtx).getMeals(LocalDate.now())) types.add(m.getMeal_type());
                if (shouldRemind(meal, types)) show(appCtx, meal);
            } catch (Exception e) {
                // Veri alınamadıysa yanlış hatırlatma yapmamak için sessiz kal.
                Log.w(TAG, "Öğünler alınamadı", e);
            } finally {
                pending.finish();
            }
        }).start();
    }

    private static void show(Context ctx, MealType meal) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                ctx.getString(R.string.notif_channel_meal), NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription(ctx.getString(R.string.notif_channel_meal_desc));
        nm.createNotificationChannel(ch);

        int text;
        switch (meal) {
            case BREAKFAST: text = R.string.meal_reminder_breakfast; break;
            case LUNCH:     text = R.string.meal_reminder_lunch; break;
            default:        text = R.string.meal_reminder_dinner; break;
        }
        Intent open = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(ctx, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        nm.notify(1100 + meal.ordinal(), new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(ctx.getString(R.string.meal_reminder_title))
                .setContentText(ctx.getString(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build());
    }
}
