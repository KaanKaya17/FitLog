package com.example.fitlog;

import android.app.Activity;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

public class Navigation {

    /** Login ekranına gider ve geri tuşuyla korumalı ekranlara dönülmesini engeller. */
    public static void toLoginAndClear(Activity activity) {
        navigateClearing(activity, R.id.loginFragment);
    }

    /** Girişten sonra ana sayfaya gider; geri tuşu login'e dönmez. */
    public static void toHomeAndClear(Activity activity) {
        navigateClearing(activity, R.id.homeFragment);
    }

    private static void navigateClearing(Activity activity, int destination) {
        if (activity instanceof MainActivity) {
            NavController nav = ((MainActivity) activity).getNavController();
            if (nav != null) {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(nav.getGraph().getId(), true)
                        .build();
                nav.navigate(destination, null, options);
            }
        }
    }
}
