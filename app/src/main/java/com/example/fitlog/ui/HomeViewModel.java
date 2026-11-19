package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.AdminNotification;
import com.example.fitlog.DayHistory;
import com.example.fitlog.WeightLog;
import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.domain.HistoryAggregator;
import com.example.fitlog.domain.StreakCalculator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HomeViewModel extends BaseViewModel {

    /** Ana sayfa özeti. */
    public static final class Dashboard {
        public final DayHistory today;
        public final int mealStreak;
        public final Float latestWeight;
        public final boolean stale;

        public Dashboard(DayHistory today, int mealStreak, Float latestWeight) {
            this(today, mealStreak, latestWeight, false);
        }

        Dashboard(DayHistory today, int mealStreak, Float latestWeight, boolean stale) {
            this.today = today; this.mealStreak = mealStreak; this.latestWeight = latestWeight; this.stale = stale;
        }
    }

    static final int STREAK_WINDOW_DAYS = 60;

    private final MutableLiveData<Resource<Dashboard>> dashboard = new MutableLiveData<>();
    private final MutableLiveData<Event<AdminNotification>> notification = new MutableLiveData<>();

    public HomeViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<Dashboard>> dashboard() { return dashboard; }
    public LiveData<Event<AdminNotification>> notification() { return notification; }

    public void refresh() {
        load(dashboard, () -> {
            Dashboard d = build(repo, LocalDate.now(), ZoneId.systemDefault());
            if (d.stale) postMessage(com.example.fitlog.R.string.msg_offline_cached);
            return d;
        });
    }

    static Dashboard build(FitLogRepository repo, LocalDate today, ZoneId zone) throws Exception {
        LocalDate from = today.minusDays(STREAK_WINDOW_DAYS - 1);
        FitLogRepository.RangeData data = repo.getRange(from, today);
        List<DayHistory> days = HistoryAggregator.aggregate(from, today, data.meals, data.water, zone);

        Set<LocalDate> logged = new HashSet<>();
        for (DayHistory d : days) if (!d.meals.isEmpty()) logged.add(LocalDate.parse(d.date));

        Float weight = null;
        try {
            List<WeightLog> w = repo.getWeights(1);
            if (!w.isEmpty()) weight = w.get(0).getWeight_kg();
        } catch (Exception ignored) {
            // Kilo tablosu henüz yoksa (migration çalışmadıysa) ana sayfa yine açılsın.
        }
        return new Dashboard(days.get(0), StreakCalculator.currentStreak(logged, today), weight, data.stale);
    }

    public void checkNotifications() {
        runSilently(repo::unreadNotifications, list -> {
            if (list != null && !list.isEmpty()) notification.setValue(new Event<>(list.get(0)));
        });
    }

    public void markRead(String id) {
        runSilently(() -> { repo.markNotificationRead(id); return null; }, null);
    }

    public void uploadFcmToken(String token) {
        runSilently(() -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("fcm_token", token);
            repo.updateProfile(m);
            return null;
        }, null);
    }
}
