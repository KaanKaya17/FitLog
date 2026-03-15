package com.example.fitlog;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

public class Navigation {

    private static void openActivity(Context context, Class<?> targetActivity) {
        Intent intent = new Intent(context, targetActivity);
        context.startActivity(intent);

        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    public static void goToHome(Context context) {
        openActivity(context, MainActivity.class);
    }

    public static void goToProfile(Context context) {
        openActivity(context, Profile.class);
    }

    public static void goToMealDetail(Context context, int mealId) {
        Intent intent = new Intent(context, MealDetail.class);
        intent.putExtra("EXTRA_MEAL_ID", mealId); // Veriyi ekledik
        context.startActivity(intent);
    }

    public static void goToAddMeal(Context context) {
        openActivity(context, AddMeal.class);
    }

    public static void goToSettings(Context context) {
        openActivity(context, Settings.class);
    }

    public static void goToTrackYourWeight(Context context) {
        openActivity(context, TrackYourWeight.class);
    }

    public static void goToWaterTrack(Context context) {
        openActivity(context, WaterTrack.class);
    }

    // ÖZEL DURUM: Giriş yaptıktan sonra geri dönülmesin istenirse
    public static void goToMainAndClearStack(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }
}