package com.example.fitlog;

import android.app.Activity;

import androidx.navigation.NavController;
import androidx.navigation.NavOptions;

public class Navigation {

    /** Login ekranına gider ve geri tuşuyla korumalı ekranlara dönülmesini engeller. */
    public static void toLoginAndClear(Activity activity) {
        if (activity instanceof MainActivity) {
            NavController navController = ((MainActivity) activity).getNavController();
            if (navController != null) {
                NavOptions options = new NavOptions.Builder()
                        .setPopUpTo(navController.getGraph().getId(), true)
                        .build();
                navController.navigate(R.id.loginFragment, null, options);
            }
        }
    }
}
