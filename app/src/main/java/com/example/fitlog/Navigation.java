package com.example.fitlog;

import android.app.Activity;

import androidx.navigation.NavController;

public class Navigation {

    public static void toLoginAndClear(Activity activity) {
        if (activity instanceof MainActivity) {
            NavController navController = ((MainActivity) activity).getNavController();
            if (navController != null) {
                navController.navigate(R.id.loginFragment);
            }
        }
    }
}
