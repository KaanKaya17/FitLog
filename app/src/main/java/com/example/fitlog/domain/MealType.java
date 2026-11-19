package com.example.fitlog.domain;

/** Öğün türleri (veritabanındaki meal_type değerleri). */
public enum MealType {
    BREAKFAST("breakfast"), LUNCH("lunch"), DINNER("dinner"), SNACK("snack");

    public final String key;

    MealType(String key) { this.key = key; }

    public static MealType fromKey(String key) {
        if (key != null) {
            for (MealType t : values()) if (t.key.equalsIgnoreCase(key)) return t;
        }
        return null;
    }

    /** Saate göre varsayılan öğün: 05-11 kahvaltı, 11-16 öğle, 16-22 akşam, diğer ara öğün. */
    public static MealType forHour(int hour) {
        if (hour >= 5 && hour < 11) return BREAKFAST;
        if (hour >= 11 && hour < 16) return LUNCH;
        if (hour >= 16 && hour < 22) return DINNER;
        return SNACK;
    }
}
