package com.example.fitlog.domain;

/**
 * Profilden günlük enerji ve makro hedefi hesaplar.
 *
 * BMR: Mifflin-St Jeor
 *   erkek: 10*kg + 6.25*cm - 5*yaş + 5
 *   kadın: 10*kg + 6.25*cm - 5*yaş - 161
 * TDEE = BMR * aktivite katsayısı
 * Hedef: ver -%20 (en fazla -500), koru 0, al +%10 (en fazla +400)
 * Makro: protein kg başına (ver 2.0, koru 1.6, al 1.8 g), yağ enerjinin %25'i,
 *        kalan enerji karbonhidrat.
 */
public final class NutritionCalculator {

    public enum Activity {
        SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), ACTIVE(1.725), VERY_ACTIVE(1.9);

        public final double factor;
        Activity(double factor) { this.factor = factor; }

        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static Activity fromKey(String key) {
            if (key != null) {
                for (Activity a : values()) if (a.key().equalsIgnoreCase(key)) return a;
            }
            return MODERATE;
        }
    }

    public enum Goal {
        LOSE, MAINTAIN, GAIN;

        public static Goal fromKey(String key) {
            if ("lose".equalsIgnoreCase(key)) return LOSE;
            if ("gain".equalsIgnoreCase(key)) return GAIN;
            return MAINTAIN;
        }
    }

    public static final int MIN_CALORIES = 1200;
    public static final int MAX_CALORIES = 5000;

    /** Hesaplama sonucu. */
    public static final class Targets {
        public final int bmr;
        public final int tdee;
        public final int calories;
        public final int proteinG;
        public final int carbsG;
        public final int fatG;
        public final int waterMl;

        Targets(int bmr, int tdee, int calories, int proteinG, int carbsG, int fatG, int waterMl) {
            this.bmr = bmr; this.tdee = tdee; this.calories = calories;
            this.proteinG = proteinG; this.carbsG = carbsG; this.fatG = fatG; this.waterMl = waterMl;
        }
    }

    private NutritionCalculator() {}

    public static boolean isMale(String gender) {
        return "male".equalsIgnoreCase(gender);
    }

    public static double bmr(boolean male, double weightKg, double heightCm, int age) {
        double base = 10 * weightKg + 6.25 * heightCm - 5 * age;
        return male ? base + 5 : base - 161;
    }

    /**
     * @throws IllegalArgumentException geçersiz profil değerlerinde
     */
    public static Targets calculate(boolean male, double weightKg, double heightCm, int age,
                                    Activity activity, Goal goal) {
        if (weightKg < 20 || weightKg > 400) throw new IllegalArgumentException("weight");
        if (heightCm < 50 || heightCm > 300) throw new IllegalArgumentException("height");
        if (age < 13 || age > 120) throw new IllegalArgumentException("age");

        double bmr = bmr(male, weightKg, heightCm, age);
        double tdee = bmr * activity.factor;

        double calories;
        double proteinPerKg;
        switch (goal) {
            case LOSE:
                calories = tdee - Math.min(tdee * 0.20, 500);
                proteinPerKg = 2.0;
                break;
            case GAIN:
                calories = tdee + Math.min(tdee * 0.10, 400);
                proteinPerKg = 1.8;
                break;
            default:
                calories = tdee;
                proteinPerKg = 1.6;
        }
        int kcal = roundTo((int) Math.round(clamp(calories, MIN_CALORIES, MAX_CALORIES)), 10);

        int protein = (int) Math.round(weightKg * proteinPerKg);
        int fat = (int) Math.round(kcal * 0.25 / 9.0);
        int carbs = (int) Math.max(0, Math.round((kcal - protein * 4 - fat * 9) / 4.0));

        // Günlük su: kg başına ~33 ml, 100 ml'ye yuvarlanır, 1500-4000 aralığında.
        int water = roundTo((int) clamp(weightKg * 33, 1500, 4000), 100);

        return new Targets((int) Math.round(bmr), (int) Math.round(tdee), kcal, protein, carbs, fat, water);
    }

    public static int ageFromBirthYear(int birthYear, int currentYear) {
        return currentYear - birthYear;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    static int roundTo(int v, int step) {
        return Math.round(v / (float) step) * step;
    }
}
