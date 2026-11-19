package com.example.fitlog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.fitlog.data.AppExecutors;
import com.example.fitlog.ui.ErrorMessages;

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
        if (navHostFragment != null) navController = navHostFragment.getNavController();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) handleOAuthRedirect(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthRedirect(intent);
    }

    /** fitlog://auth-callback?code=... (Google girişi dönüşü). */
    private void handleOAuthRedirect(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null || !"fitlog".equals(data.getScheme()) || !"auth-callback".equals(data.getHost())) return;
        intent.setData(null); // ekran döndürmede tekrar işlenmesin

        String error = data.getQueryParameter("error");
        String code = data.getQueryParameter("code");
        if (error != null || code == null) {
            if (!"access_denied".equals(error)) Toast.makeText(this, R.string.auth_error_oauth_failed, Toast.LENGTH_LONG).show();
            return;
        }
        android.content.Context appCtx = getApplicationContext();
        AppExecutors.io().execute(() -> {
            try {
                User user = AuthManager.exchangeOAuthCode(appCtx, code);
                AppExecutors.main(() -> {
                    if (isFinishing()) return;
                    String name = user.getFirst_name() != null ? user.getFirst_name() : "";
                    Toast.makeText(this, getString(R.string.login_welcome, name), Toast.LENGTH_SHORT).show();
                    Navigation.toHomeAndClear(this);
                });
            } catch (Exception e) {
                Log.w("OAuth", "Code exchange failed", e);
                AppExecutors.main(() -> ErrorMessages.toast(this, e));
            }
        });
    }

    public NavController getNavController() {
        return navController;
    }

    // ── Alt gezinme çubuğu (nav_bar.xml onClick) ───────────────────────

    public void navHomePage(View view) {
        navigateTo(R.id.homeFragment);
    }

    public void navProfile(View view) {
        boolean loggedIn = new PrefManager(this).getId() != -1 && AuthManager.hasSession(this);
        navigateTo(loggedIn ? R.id.profileFragment : R.id.loginFragment);
    }

    public void navMealList(View view) {
        navigateTo(R.id.mealListFragment);
    }

    public void navStatistic(View view) {
        navigateTo(R.id.historyFragment);
    }

    private void navigateTo(int destinationId) {
        if (navController == null) return;
        try {
            if (navController.getCurrentDestination() != null
                    && navController.getCurrentDestination().getId() != destinationId) {
                if (isTopLevel(destinationId)) navController.popBackStack(R.id.homeFragment, false);
                navController.navigate(destinationId);
            }
        } catch (Exception e) {
            try { navController.navigate(destinationId); } catch (Exception ignored) {}
        }
    }

    private boolean isTopLevel(int id) {
        return id == R.id.homeFragment || id == R.id.profileFragment
                || id == R.id.mealListFragment || id == R.id.historyFragment;
    }

    // Bazı eski XML'lerin hâlâ referans verdiği metot
    public void closeInformation(View view) {
        InformationCard.getInstance().hide();
    }
}
