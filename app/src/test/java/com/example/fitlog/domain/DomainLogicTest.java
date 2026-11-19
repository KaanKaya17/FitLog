package com.example.fitlog.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.fitlog.DayHistory;
import com.example.fitlog.MealLog;
import com.example.fitlog.NutritionData;
import com.example.fitlog.WaterLog;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DomainLogicTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul"); // UTC+3
    private static final LocalDate DAY = LocalDate.of(2026, 11, 19);

    // ── DateRanges ─────────────────────────────────────────────────────

    @Test
    public void startOfDay_isLocalMidnightInUtc() {
        // 19 Kasım 00:00 İstanbul = 18 Kasım 21:00 UTC (eski kod "2026-11-19T00:00:00Z" kullanıyordu)
        assertEquals("2026-11-18T21:00:00Z", DateRanges.startOfDayUtc(DAY, ISTANBUL));
    }

    @Test
    public void dayFilter_coversExactlyOneLocalDay() {
        assertEquals(Arrays.asList("gte.2026-11-18T21:00:00Z", "lt.2026-11-19T21:00:00Z"),
                DateRanges.dayFilter(DAY, ISTANBUL));
    }

    @Test
    public void rangeFilter_isInclusiveOfLastDay() {
        List<String> f = DateRanges.rangeFilter(DAY.minusDays(6), DAY, ISTANBUL);
        assertEquals("gte.2026-11-12T21:00:00Z", f.get(0));
        assertEquals("lt.2026-11-19T21:00:00Z", f.get(1));
    }

    @Test
    public void toLocalDate_lateNightUtcBelongsToNextLocalDay() {
        // 22:30 UTC = 01:30 İstanbul ertesi gün
        assertEquals(DAY, DateRanges.toLocalDate("2026-11-18T22:30:00.123456+00:00", ISTANBUL));
        assertEquals(DAY.minusDays(1), DateRanges.toLocalDate("2026-11-18T20:59:59+00:00", ISTANBUL));
    }

    @Test
    public void toLocalDate_handlesShortOffsetsAndGarbage() {
        assertEquals(DAY, DateRanges.toLocalDate("2026-11-19 10:00:00+00", ISTANBUL));
        assertEquals(DAY, DateRanges.toLocalDate("2026-11-19", ISTANBUL));
        assertNull(DateRanges.toLocalDate(null, ISTANBUL));
        assertNull(DateRanges.toLocalDate("not a date", ISTANBUL));
    }

    @Test
    public void localNoon_isConvertedToUtc() {
        assertEquals("2026-11-19T09:00:00Z", DateRanges.localNoonUtc(DAY, ISTANBUL));
    }

    // ── NutritionCalculator ────────────────────────────────────────────

    @Test
    public void bmr_matchesMifflinStJeor() {
        // 80 kg, 180 cm, 30 yaş erkek: 800 + 1125 - 150 + 5 = 1780
        assertEquals(1780, NutritionCalculator.bmr(true, 80, 180, 30), 0.001);
        // 60 kg, 165 cm, 25 yaş kadın: 600 + 1031.25 - 125 - 161 = 1345.25
        assertEquals(1345.25, NutritionCalculator.bmr(false, 60, 165, 25), 0.001);
    }

    @Test
    public void calculate_maintain_equalsTdeeAndMacrosAddUp() {
        NutritionCalculator.Targets t = NutritionCalculator.calculate(true, 80, 180, 30,
                NutritionCalculator.Activity.MODERATE, NutritionCalculator.Goal.MAINTAIN);
        assertEquals(1780, t.bmr);
        assertEquals(2759, t.tdee);              // 1780 × 1.55
        assertEquals(2760, t.calories);          // 10'a yuvarlanır
        assertEquals(128, t.proteinG);           // 80 × 1.6
        int kcalFromMacros = t.proteinG * 4 + t.carbsG * 4 + t.fatG * 9;
        assertTrue(Math.abs(kcalFromMacros - t.calories) <= 10);
        assertEquals(2600, t.waterMl);           // 80 × 33 = 2640 → 2600
    }

    @Test
    public void calculate_lose_capsDeficitAt500() {
        NutritionCalculator.Targets t = NutritionCalculator.calculate(true, 120, 190, 35,
                NutritionCalculator.Activity.ACTIVE, NutritionCalculator.Goal.LOSE);
        assertTrue(t.tdee - t.calories <= 505);
        assertEquals(240, t.proteinG); // 120 × 2.0
    }

    @Test
    public void calculate_neverBelowSafeMinimum() {
        NutritionCalculator.Targets t = NutritionCalculator.calculate(false, 40, 150, 70,
                NutritionCalculator.Activity.SEDENTARY, NutritionCalculator.Goal.LOSE);
        assertEquals(NutritionCalculator.MIN_CALORIES, t.calories);
    }

    @Test(expected = IllegalArgumentException.class)
    public void calculate_rejectsChildren() {
        NutritionCalculator.calculate(true, 40, 150, 10,
                NutritionCalculator.Activity.LIGHT, NutritionCalculator.Goal.MAINTAIN);
    }

    @Test
    public void activityAndGoal_parseKeysSafely() {
        assertEquals(NutritionCalculator.Activity.VERY_ACTIVE, NutritionCalculator.Activity.fromKey("very_active"));
        assertEquals(NutritionCalculator.Activity.MODERATE, NutritionCalculator.Activity.fromKey(null));
        assertEquals(NutritionCalculator.Goal.LOSE, NutritionCalculator.Goal.fromKey("LOSE"));
        assertEquals(NutritionCalculator.Goal.MAINTAIN, NutritionCalculator.Goal.fromKey("?"));
        assertTrue(NutritionCalculator.isMale("Male"));
        assertFalse(NutritionCalculator.isMale("Female"));
    }

    // ── StreakCalculator ───────────────────────────────────────────────

    private static Set<LocalDate> days(int... offsets) {
        Set<LocalDate> s = new HashSet<>();
        for (int o : offsets) s.add(DAY.minusDays(o));
        return s;
    }

    @Test
    public void streak_countsBackFromToday() {
        assertEquals(3, StreakCalculator.currentStreak(days(0, 1, 2, 4), DAY));
    }

    @Test
    public void streak_todayNotDoneYetDoesNotBreakIt() {
        assertEquals(2, StreakCalculator.currentStreak(days(1, 2), DAY));
    }

    @Test
    public void streak_gapResetsIt() {
        assertEquals(0, StreakCalculator.currentStreak(days(2, 3), DAY));
        assertEquals(0, StreakCalculator.currentStreak(Collections.emptySet(), DAY));
    }

    @Test
    public void bestStreak_findsLongestRun() {
        assertEquals(4, StreakCalculator.bestStreak(days(0, 1, 5, 6, 7, 8, 12)));
        assertEquals(0, StreakCalculator.bestStreak(Collections.emptySet()));
    }

    // ── HistoryAggregator ──────────────────────────────────────────────

    private static MealLog meal(String eatenAt, int kcal, float protein) {
        MealLog m = new MealLog();
        m.setFood_name("x");
        m.setCalories(kcal);
        m.setProtein(protein);
        try {
            java.lang.reflect.Field f = MealLog.class.getDeclaredField("eaten_at");
            f.setAccessible(true);
            f.set(m, eatenAt);
        } catch (Exception e) { throw new RuntimeException(e); }
        return m;
    }

    private static WaterLog water(String at, int ml) {
        WaterLog w = new WaterLog(1, ml);
        w.setConsumed_at(at);
        return w;
    }

    @Test
    public void aggregate_groupsByLocalDayIncludingEmptyDays() {
        List<DayHistory> d = HistoryAggregator.aggregate(DAY.minusDays(2), DAY,
                Arrays.asList(
                        meal("2026-11-18T22:00:00+00:00", 500, 20),   // İstanbul'da 19 Kasım 01:00
                        meal("2026-11-19T10:00:00+00:00", 700, 30),
                        meal("2026-11-17T12:00:00+00:00", 300, 10),
                        meal("2026-10-01T12:00:00+00:00", 999, 99)),  // aralık dışı
                Arrays.asList(water("2026-11-19T08:00:00+00:00", 250), water("2026-11-19T09:00:00+00:00", 500)),
                ISTANBUL);

        assertEquals(3, d.size());
        assertEquals("2026-11-19", d.get(0).date);          // en yeni önce
        assertEquals(1200, d.get(0).totalCalories);
        assertEquals(50f, d.get(0).totalProtein, 0.001);
        assertEquals(750, d.get(0).totalWater);
        assertEquals(0, d.get(1).totalCalories);            // 18 Kasım boş
        assertEquals(300, d.get(2).totalCalories);
    }

    @Test
    public void averages_ignoreEmptyDays() {
        List<DayHistory> d = HistoryAggregator.aggregate(DAY.minusDays(6), DAY,
                Arrays.asList(meal("2026-11-19T10:00:00+00:00", 2000, 100), meal("2026-11-17T10:00:00+00:00", 1000, 50)),
                Collections.singletonList(water("2026-11-19T10:00:00+00:00", 2000)), ISTANBUL);
        HistoryAggregator.Averages a = HistoryAggregator.averages(d);
        assertEquals(1500, a.calories);
        assertEquals(75f, a.protein, 0.001);
        assertEquals(2000, a.water);
        assertEquals(2, a.loggedDays);
    }

    // ── MealType ───────────────────────────────────────────────────────

    @Test
    public void mealType_defaultsByHour() {
        assertEquals(MealType.BREAKFAST, MealType.forHour(7));
        assertEquals(MealType.LUNCH, MealType.forHour(13));
        assertEquals(MealType.DINNER, MealType.forHour(19));
        assertEquals(MealType.SNACK, MealType.forHour(23));
        assertEquals(MealType.SNACK, MealType.forHour(3));
        assertEquals(MealType.DINNER, MealType.fromKey("dinner"));
        assertNull(MealType.fromKey("brunch"));
    }

    // ── Pkce ───────────────────────────────────────────────────────────

    @Test
    public void pkce_challengeMatchesRfc7636Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                Pkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    public void pkce_verifierIsRandomAndUrlSafe() {
        String a = Pkce.newVerifier(), b = Pkce.newVerifier();
        assertFalse(a.equals(b));
        assertTrue(a.matches("[A-Za-z0-9_-]{43,128}"));
    }

    // ── FoodMath ───────────────────────────────────────────────────────

    private static NutritionData food() {
        NutritionData d = new NutritionData();
        d.foodName = "Pizza"; d.servingSize = "1 slice"; d.calories = 285;
        d.protein = 12.2f; d.carbohydrates = 35.7f; d.fat = 10.4f; d.fiber = 2.5f; d.confidence = "medium";
        return d;
    }

    @Test
    public void scale_multipliesAndRounds() {
        NutritionData s = FoodMath.scale(food(), 1.5);
        assertEquals(428, s.calories);
        assertEquals(18.3f, s.protein, 0.001);
        assertEquals("1.5 × 1 slice", s.servingSize);
        assertEquals(285, food().calories); // kaynak değişmez
        assertEquals("1 slice", FoodMath.scale(food(), 1.0).servingSize);
    }

    @Test(expected = IllegalArgumentException.class)
    public void scale_rejectsNonPositive() {
        FoodMath.scale(food(), 0);
    }

    @Test
    public void inconsistent_detectsWildlyWrongCalories() {
        assertFalse(FoodMath.isInconsistent(food()));      // 12.2*4+35.7*4+10.4*9 ≈ 285
        NutritionData bad = food();
        bad.calories = 1500;
        assertTrue(FoodMath.isInconsistent(bad));
    }

    @Test
    public void distinctRecent_dedupesCaseInsensitiveAndLimits() {
        MealLog a = new MealLog(); a.setFood_name("Yogurt");
        MealLog b = new MealLog(); b.setFood_name("yogurt ");
        MealLog c = new MealLog(); c.setFood_name("Apple");
        MealLog d = new MealLog(); d.setFood_name("Egg");
        List<MealLog> r = FoodMath.distinctRecent(Arrays.asList(a, b, c, d), 2);
        assertEquals(2, r.size());
        assertEquals("Yogurt", r.get(0).getFood_name());
        assertEquals("Apple", r.get(1).getFood_name());
    }
}
