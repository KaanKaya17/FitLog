package com.example.fitlog.domain;

import com.example.fitlog.DayHistory;

import java.util.List;

/** Haftalık özet sayıları (bildirim için). */
public final class WeeklySummary {

    public final int loggedDays;
    public final int avgCalories;
    /** Su hedefine ulaşılan gün sayısı. */
    public final int waterGoalDays;
    /** Kalorinin hedefin ±%10'u içinde kaldığı gün sayısı. */
    public final int calorieGoalDays;

    private WeeklySummary(int loggedDays, int avgCalories, int waterGoalDays, int calorieGoalDays) {
        this.loggedDays = loggedDays; this.avgCalories = avgCalories;
        this.waterGoalDays = waterGoalDays; this.calorieGoalDays = calorieGoalDays;
    }

    public static WeeklySummary of(List<DayHistory> days, int calorieGoal, int waterGoal) {
        HistoryAggregator.Averages a = HistoryAggregator.averages(days);
        int water = 0, onTarget = 0;
        for (DayHistory d : days) {
            if (waterGoal > 0 && d.totalWater >= waterGoal) water++;
            if (calorieGoal > 0 && !d.meals.isEmpty()
                    && Math.abs(d.totalCalories - calorieGoal) <= calorieGoal * 0.10) onTarget++;
        }
        return new WeeklySummary(a.loggedDays, a.calories, water, onTarget);
    }
}
