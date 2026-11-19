package com.example.fitlog.ui;

import com.example.fitlog.DayHistory;
import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.domain.HistoryAggregator;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/** AI koçuna verilecek son 7 günlük özet. */
public final class CoachContext {

    public static final int DAYS = 7;

    public final int avgCalories;
    public final float avgProtein;
    public final int avgWater;
    public final int loggedDays;

    CoachContext(int avgCalories, float avgProtein, int avgWater, int loggedDays) {
        this.avgCalories = avgCalories; this.avgProtein = avgProtein;
        this.avgWater = avgWater; this.loggedDays = loggedDays;
    }

    static CoachContext load(FitLogRepository repo, LocalDate today, ZoneId zone) throws Exception {
        LocalDate from = today.minusDays(DAYS - 1);
        FitLogRepository.RangeData d = repo.getRange(from, today);
        List<DayHistory> days = HistoryAggregator.aggregate(from, today, d.meals, d.water, zone);
        HistoryAggregator.Averages a = HistoryAggregator.averages(days);
        return new CoachContext(a.calories, a.protein, a.water, a.loggedDays);
    }

    /**
     * Şablon sırası: %1$d kayıtlı gün, %2$d ort. kalori, %3$.0f ort. protein,
     * %4$d ort. su, %5$d kalori hedefi, %6$d su hedefi.
     */
    public String format(String template, int calorieGoal, int waterGoal) {
        return String.format(Locale.ROOT, template, loggedDays, avgCalories, avgProtein, avgWater,
                calorieGoal, waterGoal);
    }
}
