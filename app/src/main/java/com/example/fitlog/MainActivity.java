package com.example.fitlog;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

public class MainActivity extends AppCompatActivity {

    private NavController navController;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    public NavController getNavController() {
        return navController;
    }

    // ── Bottom NavBar onClick methods (called from nav_bar.xml) ──────────

    public void navHomePage(View view) {
        navigateTo(R.id.homeFragment);
    }

    public void navProfile(View view) {
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if (userId != -1) {
            navigateTo(R.id.profileFragment);
        } else {
            navigateTo(R.id.loginFragment);
        }
    }

    public void navMealList(View view) {
        navigateTo(R.id.mealListFragment);
    }

    public void navStatistic(View view) {
        navigateTo(R.id.historyFragment);
    }

    // ── Helper: safe navigate avoiding duplicate destinations ─────────────

    private void navigateTo(int destinationId) {
        if (navController == null) return;
        try {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != destinationId) {
                // Pop back to home before navigating to a top-level tab
                if (isTopLevel(destinationId)) {
                    navController.popBackStack(R.id.homeFragment, false);
                }
                navController.navigate(destinationId);
            }
        } catch (Exception e) {
            // Fallback: try direct navigate
            try {
                navController.navigate(destinationId);
            } catch (Exception ignored) {}
        }
    }

    private boolean isTopLevel(int id) {
        return id == R.id.homeFragment || id == R.id.profileFragment
                || id == R.id.mealListFragment || id == R.id.historyFragment;
    }

    // Legacy methods that some xml layouts may still reference
    public void closeInformation(View view) {
        InformationCard.getInstance().hide();
    }
}