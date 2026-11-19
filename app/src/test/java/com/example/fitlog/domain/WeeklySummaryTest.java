package com.example.fitlog.domain;

import static org.junit.Assert.assertEquals;

import com.example.fitlog.DayHistory;
import com.example.fitlog.MealLog;

import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WeeklySummaryTest {

    private static DayHistory day(int kcal, int water) {
        DayHistory d = new DayHistory("2026-11-19", "", "");
        d.totalCalories = kcal;
        d.totalWater = water;
        if (kcal > 0) d.meals.add(new MealLog());
        return d;
    }

    @Test
    public void countsGoalDays() {
        List<DayHistory> days = new ArrayList<>();
        days.add(day(2000, 2500)); // hedefte, su tamam
        days.add(day(2150, 1000)); // hedef +%7.5 → hedefte
        days.add(day(2400, 2000)); // +%20 → değil, su tamam
        days.add(day(0, 0));       // kayıt yok
        WeeklySummary s = WeeklySummary.of(days, 2000, 2000);
        assertEquals(3, s.loggedDays);
        assertEquals(2183, s.avgCalories);
        assertEquals(2, s.waterGoalDays);
        assertEquals(2, s.calorieGoalDays);
    }

    @Test
    public void nextSundayEvening() {
        // 19 Kasım 2026 Perşembe 18:00 → 22 Kasım Pazar 20:00
        assertEquals(Duration.ofHours(74), com.example.fitlog.data.WeeklySummaryWorker
                .delayUntilNextSundayEvening(LocalDateTime.of(2026, 11, 19, 18, 0)));
        // Pazar 21:00 → bir sonraki pazar
        assertEquals(Duration.ofHours(167), com.example.fitlog.data.WeeklySummaryWorker
                .delayUntilNextSundayEvening(LocalDateTime.of(2026, 11, 22, 21, 0)));
    }
}
