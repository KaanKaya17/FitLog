package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.DayHistory;
import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.domain.HistoryAggregator;
import com.example.fitlog.domain.StreakCalculator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Geçmiş ekranı. Son 90 gün bir kez yüklenir; hafta/ay görünümü ve seriler
 * aynı veriden türetilir (görünüm değiştirmek yeni istek atmaz).
 */
public class HistoryViewModel extends BaseViewModel {

    static final int WINDOW_DAYS = 90;

    /** Hesaplanmış geçmiş. {@code days} en yeni gün ilk sırada, WINDOW_DAYS uzunluğunda. */
    public static final class History {
        public final List<DayHistory> days;
        public final int currentStreak;
        public final int bestStreak;
        public final int waterStreak;

        History(List<DayHistory> days, int currentStreak, int bestStreak, int waterStreak) {
            this.days = days; this.currentStreak = currentStreak;
            this.bestStreak = bestStreak; this.waterStreak = waterStreak;
        }
    }

    private final MutableLiveData<Resource<History>> history = new MutableLiveData<>();
    private final MutableLiveData<Integer> periodDays = new MutableLiveData<>(7);

    public HistoryViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<History>> history() { return history; }
    public LiveData<Integer> periodDays() { return periodDays; }

    public void setPeriod(int days) { periodDays.setValue(days); }

    public void refresh(int waterGoal) {
        load(history, () -> build(repo, LocalDate.now(), ZoneId.systemDefault(), waterGoal));
    }

    static History build(FitLogRepository repo, LocalDate today, ZoneId zone, int waterGoal) throws Exception {
        LocalDate from = today.minusDays(WINDOW_DAYS - 1);
        FitLogRepository.RangeData data = repo.getRange(from, today);
        List<DayHistory> days = HistoryAggregator.aggregate(from, today, data.meals, data.water, zone);

        Set<LocalDate> mealDays = new HashSet<>();
        Set<LocalDate> waterDays = new HashSet<>();
        for (DayHistory d : days) {
            LocalDate date = LocalDate.parse(d.date);
            if (!d.meals.isEmpty()) mealDays.add(date);
            if (waterGoal > 0 && d.totalWater >= waterGoal) waterDays.add(date);
        }
        return new History(days,
                StreakCalculator.currentStreak(mealDays, today),
                StreakCalculator.bestStreak(mealDays),
                StreakCalculator.currentStreak(waterDays, today));
    }
}
