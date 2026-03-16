package com.example.fitlog;

import android.app.Activity;
import android.content.Intent;

public class Navigation {

    // Temel geçiş
    public static void go(Activity from, Class<?> to) {
        Intent intent = new Intent(from, to);
        from.startActivity(intent);
    }

    // Geri dönülemesin (login sonrası vb.)
    public static void goAndClear(Activity from, Class<?> to) {
        Intent intent = new Intent(from, to);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        from.startActivity(intent);
        from.finish();
    }

    // Veri taşıyarak geçiş
    public static void goWithData(Activity from, Class<?> to, String key, String value) {
        Intent intent = new Intent(from, to);
        intent.putExtra(key, value);
        from.startActivity(intent);
    }

    // ── Sayfalar ────────────────────────────────────────────────────

    public static void toMain(Activity from)        { go(from, MainActivity.class); }
    public static void toLogin(Activity from)       { go(from, UserLogin.class); }
    public static void toRegister(Activity from)    { go(from, UserRegister.class); }
    public static void toProfile(Activity from)     { go(from, Profile.class); }
    public static void toAddMeal(Activity from)     { go(from, AddMeal.class); }
    public static void toWaterTrack(Activity from)  { go(from, WaterTrack.class); }

    // Logout özel - stack temizlenir
    public static void toLoginAndClear(Activity from) { goAndClear(from, UserLogin.class); }
    public static void toMealList(Activity from) { go(from, MealListActivity.class); }

    public static void toSettings(Activity from) { go(from, Settings.class); }

}