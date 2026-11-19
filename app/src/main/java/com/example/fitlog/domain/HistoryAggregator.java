package com.example.fitlog.domain;

import com.example.fitlog.DayHistory;
import com.example.fitlog.MealLog;
import com.example.fitlog.WaterLog;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ham kayıtları yerel günlere göre toplar. */
public final class HistoryAggregator {

    private HistoryAggregator() {}

    /**
     * @return [from, to] aralığındaki her gün için (boş günler dahil) toplam;
     *         en yeni gün ilk sırada.
     */
    public static List<DayHistory> aggregate(LocalDate from, LocalDate to,
                                             List<MealLog> meals, List<WaterLog> water,
                                             ZoneId zone) {
        Map<LocalDate, DayHistory> map = new LinkedHashMap<>();
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            map.put(d, new DayHistory(d.toString(), "", ""));
        }
        if (meals != null) {
            for (MealLog m : meals) {
                DayHistory day = map.get(DateRanges.toLocalDate(m.getEaten_at(), zone));
                if (day == null) continue;
                day.totalCalories += m.getCalories();
                day.totalProtein  += m.getProtein();
                day.totalCarbs    += m.getCarbohydrates();
                day.totalFat      += m.getFat();
                day.meals.add(m);
            }
        }
        if (water != null) {
            for (WaterLog w : water) {
                DayHistory day = map.get(DateRanges.toLocalDate(w.getConsumed_at(), zone));
                if (day != null) day.totalWater += w.getAmount_ml();
            }
        }
        return new ArrayList<>(map.values());
    }

    /** Ortalama (yalnızca kayıt girilen günler üzerinden). */
    public static final class Averages {
        public final int calories;
        public final int water;
        public final float protein;
        public final int loggedDays;

        Averages(int calories, int water, float protein, int loggedDays) {
            this.calories = calories; this.water = water; this.protein = protein; this.loggedDays = loggedDays;
        }
    }

    public static Averages averages(List<DayHistory> days) {
        int calSum = 0, waterSum = 0, mealDays = 0, waterDays = 0;
        float proteinSum = 0;
        for (DayHistory d : days) {
            if (!d.meals.isEmpty()) {
                calSum += d.totalCalories;
                proteinSum += d.totalProtein;
                mealDays++;
            }
            if (d.totalWater > 0) {
                waterSum += d.totalWater;
                waterDays++;
            }
        }
        return new Averages(
                mealDays == 0 ? 0 : Math.round(calSum / (float) mealDays),
                waterDays == 0 ? 0 : Math.round(waterSum / (float) waterDays),
                mealDays == 0 ? 0 : proteinSum / mealDays,
                Math.max(mealDays, waterDays));
    }
}
