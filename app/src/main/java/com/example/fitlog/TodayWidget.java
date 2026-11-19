package com.example.fitlog;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.data.ServiceLocator;
import com.example.fitlog.ui.ErrorMessages;

import java.time.LocalDate;

/**
 * Ana ekran widget'ı: bugünün kalori ve su ilerlemesi, tek dokunuşla +250 ml su.
 * Uygulamadan çıkıldığında (MainActivity.onStop) ve 30 dakikada bir yenilenir.
 */
public class TodayWidget extends AppWidgetProvider {

    private static final String TAG = "TodayWidget";
    static final String ACTION_ADD_WATER = "com.example.fitlog.widget.ADD_WATER";

    /** Ekrandaki tüm FitLog widget'larını yeniler. */
    public static void refresh(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, TodayWidget.class));
        if (ids == null || ids.length == 0) return;
        ctx.sendBroadcast(new Intent(ctx, TodayWidget.class)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids));
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        Context app = context.getApplicationContext();
        if (!isLoggedIn(app)) {
            push(app, mgr, ids, loggedOutViews(app));
            return;
        }
        PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                push(app, mgr, ids, todayViews(app, load(app)));
            } catch (Exception e) {
                // Ağ yoksa son bilinen değerleri koru; yalnızca ilk kurulumda boş göster.
                Log.w(TAG, "Bugünün verisi alınamadı", e);
            } finally {
                pending.finish();
            }
        }).start();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (!ACTION_ADD_WATER.equals(intent.getAction())) return;
        Context app = context.getApplicationContext();
        if (!isLoggedIn(app)) return;
        PendingResult pending = goAsync();
        new Thread(() -> {
            String msg;
            try {
                boolean queued = ServiceLocator.repository(app).addWater(WaterTileService.AMOUNT_ML).queued;
                msg = queued ? app.getString(R.string.msg_saved_offline)
                        : app.getString(R.string.water_added_format, WaterTileService.AMOUNT_ML);
            } catch (Exception e) {
                msg = ErrorMessages.message(app, e);
            }
            final String m = msg;
            com.example.fitlog.data.AppExecutors.main(() -> Toast.makeText(app, m, Toast.LENGTH_SHORT).show());
            try {
                AppWidgetManager mgr = AppWidgetManager.getInstance(app);
                int[] ids = mgr.getAppWidgetIds(new ComponentName(app, TodayWidget.class));
                push(app, mgr, ids, todayViews(app, load(app)));
            } catch (Exception e) {
                Log.w(TAG, "Widget yenilenemedi", e);
            } finally {
                pending.finish();
            }
        }).start();
    }

    // ── Veri ───────────────────────────────────────────────────────────

    /** Bugünün toplamları. */
    static final class Today {
        final int calories, calorieGoal, waterMl, waterGoal;
        Today(int calories, int calorieGoal, int waterMl, int waterGoal) {
            this.calories = calories; this.calorieGoal = calorieGoal;
            this.waterMl = waterMl; this.waterGoal = waterGoal;
        }
    }

    private static Today load(Context app) throws Exception {
        FitLogRepository repo = ServiceLocator.repository(app);
        LocalDate day = LocalDate.now();
        int kcal = 0;
        for (MealLog m : repo.getMeals(day)) kcal += m.getCalories();
        int water = 0;
        for (WaterLog w : repo.getWater(day)) water += w.getAmount_ml();
        PrefManager pref = new PrefManager(app);
        return new Today(kcal, pref.getCalorieGoal(), water, pref.getWaterGoal());
    }

    /** İlerleme yüzdesi (0-100). Hedef yoksa 0. */
    static int percent(int value, int goal) {
        if (goal <= 0 || value <= 0) return 0;
        return (int) Math.min(100, Math.round(value * 100.0 / goal));
    }

    private static boolean isLoggedIn(Context ctx) {
        return new PrefManager(ctx).getId() != -1 && AuthManager.hasSession(ctx);
    }

    // ── Görünüm ────────────────────────────────────────────────────────

    private static RemoteViews todayViews(Context app, Today t) {
        RemoteViews v = baseViews(app);
        v.setViewVisibility(R.id.widgetContent, View.VISIBLE);
        v.setViewVisibility(R.id.widgetLoggedOut, View.GONE);
        v.setTextViewText(R.id.widgetCalories, app.getString(R.string.widget_kcal_format, t.calories, t.calorieGoal));
        v.setProgressBar(R.id.widgetCalorieProgress, 100, percent(t.calories, t.calorieGoal), false);
        v.setTextViewText(R.id.widgetWater, app.getString(R.string.widget_ml_format, t.waterMl, t.waterGoal));
        v.setProgressBar(R.id.widgetWaterProgress, 100, percent(t.waterMl, t.waterGoal), false);

        Intent add = new Intent(app, TodayWidget.class).setAction(ACTION_ADD_WATER);
        v.setOnClickPendingIntent(R.id.widgetAddWater, PendingIntent.getBroadcast(app, 0, add,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static RemoteViews loggedOutViews(Context app) {
        RemoteViews v = baseViews(app);
        v.setViewVisibility(R.id.widgetContent, View.GONE);
        v.setViewVisibility(R.id.widgetLoggedOut, View.VISIBLE);
        return v;
    }

    private static RemoteViews baseViews(Context app) {
        RemoteViews v = new RemoteViews(app.getPackageName(), R.layout.widget_today);
        Intent open = new Intent(app, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        v.setOnClickPendingIntent(R.id.widgetRoot, PendingIntent.getActivity(app, 4, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }

    private static void push(Context app, AppWidgetManager mgr, int[] ids, RemoteViews views) {
        if (ids == null) return;
        for (int id : ids) mgr.updateAppWidget(id, views);
    }
}
