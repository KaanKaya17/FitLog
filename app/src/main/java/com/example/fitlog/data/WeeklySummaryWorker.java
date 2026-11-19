package com.example.fitlog.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.fitlog.AuthManager;
import com.example.fitlog.DayHistory;
import com.example.fitlog.LocaleHelper;
import com.example.fitlog.NotificationHelper;
import com.example.fitlog.PrefManager;
import com.example.fitlog.R;
import com.example.fitlog.domain.HistoryAggregator;
import com.example.fitlog.domain.WeeklySummary;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Her pazar akşamı geçen 7 günün özetini bildirim olarak gösterir. */
public class WeeklySummaryWorker extends Worker {

    public static final String WORK = "fitlog-weekly-summary";

    public WeeklySummaryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Sonraki pazar 20:00'ye hizalanmış haftalık iş kurar (zaten kuruluysa dokunmaz). */
    public static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(WeeklySummaryWorker.class, 7, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextSundayEvening(LocalDateTime.now()).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    public static void cancel(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK);
    }

    public static Duration delayUntilNextSundayEvening(LocalDateTime now) {
        LocalDateTime target = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(20, 0);
        if (!target.isAfter(now)) target = target.plusWeeks(1);
        return Duration.between(now, target);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = LocaleHelper.setLocale(getApplicationContext());
        PrefManager pref = new PrefManager(ctx);
        if (pref.getId() == -1 || !AuthManager.hasSession(ctx)) return Result.success();
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return Result.success();

        try {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            LocalDate from = today.minusDays(6);
            FitLogRepository.RangeData d = ServiceLocator.repository(ctx).getRange(from, today);
            List<DayHistory> days = HistoryAggregator.aggregate(from, today, d.meals, d.water, zone);
            WeeklySummary s = WeeklySummary.of(days, pref.getCalorieGoal(), pref.getWaterGoal());
            if (s.loggedDays == 0) return Result.success(); // boş hafta: rahatsız etme

            String text = ctx.getString(R.string.weekly_summary_text, s.loggedDays, s.avgCalories,
                    s.waterGoalDays, s.calorieGoalDays);
            NotificationHelper.showWeeklySummary(ctx, ctx.getString(R.string.weekly_summary_title), text);
            return Result.success();
        } catch (java.io.IOException e) {
            return Result.retry();
        } catch (Exception e) {
            return Result.success();
        }
    }
}
