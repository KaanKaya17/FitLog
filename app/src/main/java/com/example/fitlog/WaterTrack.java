package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class WaterTrack extends AppCompatActivity {

    private FirebaseAnalytics firebaseAnalytics;
    private int dailyGoal;
    ProgressBar progressWater;
    TextView tvCurrentAmount;
    TextView tvPercent;
    EditText editTextCustomAmount;

    int currentAmount = 0;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_track);
        
        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        progressWater       = findViewById(R.id.progressWater);
        tvCurrentAmount     = findViewById(R.id.tvCurrentAmount);
        tvPercent           = findViewById(R.id.tvPercent);
        editTextCustomAmount = findViewById(R.id.editTextCustomAmount);

        // Bugünkü toplam suyu Supabase'den çek
        PrefManager pref = new PrefManager(this);
        dailyGoal = pref.getWaterGoal(); // 2000 yerine

        progressWater.setMax(dailyGoal); // ← max'ı da güncelle
        loadTodayWater();
    }

    // ─── Buton tıklamaları ──────────────────────────────────────────

    public void btnAdd200(View view) {
        PrefManager pref = new PrefManager(this);
        if(pref.getId() != -1){
            addWater(200);
        }
    }
    public void btnAdd300(View view) {
        PrefManager pref = new PrefManager(this);
        if(pref.getId() != -1){
            addWater(300);
        }
    }
    public void btnAdd500(View view){
        PrefManager pref = new PrefManager(this);
        if(pref.getId() != -1){
            addWater(500);
        }
    }

    public void btnAddCustom(View view) {
        String input = editTextCustomAmount.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, getString(R.string.water_error_empty_amount), Toast.LENGTH_SHORT).show();
            return;
        }
        int amount;
        try {
            amount = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            Toast.makeText(this, getString(R.string.water_error_invalid_number), Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(this, getString(R.string.water_error_negative_amount), Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount > 3000) {
            Toast.makeText(this, getString(R.string.water_error_too_much_once), Toast.LENGTH_SHORT).show();
            return;
        }
        editTextCustomAmount.setText("");
        PrefManager pref = new PrefManager(this);
        if(pref.getId() != -1){
            addWater(amount);
        }
    }

    public void btnReset(View view) {
        currentAmount = 0;
        updateUI(0);
        Toast.makeText(this, getString(R.string.water_msg_reset), Toast.LENGTH_SHORT).show();
    }

    // ─── Su ekleme ──────────────────────────────────────────────────

    private void addWater(int amountMl) {
        if (currentAmount + amountMl > 10000) {
            Toast.makeText(this, getString(R.string.water_error_daily_limit), Toast.LENGTH_SHORT).show();
            return;
        }
        currentAmount += amountMl;
        updateUI(currentAmount);
        saveWaterLog(amountMl);
        
        // Firebase Analytics: Su eklendi
        Bundle waterBundle = new Bundle();
        waterBundle.putInt("amount_ml", amountMl);
        firebaseAnalytics.logEvent("water_logged", waterBundle);
    }

    // ─── Animasyonlu UI güncelleme ──────────────────────────────────

    private void updateUI(int totalMl) {
        ObjectAnimator animator = ObjectAnimator.ofInt(progressWater, "progress", progressWater.getProgress(), totalMl);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        tvCurrentAmount.setText(String.valueOf(totalMl));

        int percent = (int) ((totalMl / (float) dailyGoal) * 100);
        percent = Math.min(percent, 100);

        if (totalMl >= dailyGoal) {
            tvPercent.setText(getString(R.string.water_msg_goal_reached));
            tvPercent.setTextColor(getColor(android.R.color.holo_blue_dark));
        } else {
            tvPercent.setText(getString(R.string.home_water_percent_value, percent));
            tvPercent.setTextColor(getColor(R.color.green));
        }
    }

    // ─── Supabase: Bugünkü suyu çek ────────────────────────────────

    private void loadTodayWater() {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                PrefManager pref  = new PrefManager(this);
                String userIdFilter = "eq." + pref.getId();

                // Bugünün tarihini al (YYYY-MM-DD)
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                String dateFilter = "gte." + today + "T00:00:00Z";

                retrofit2.Response<List<WaterLog>> response = api.getTodayWaterLogs(
                        apiKey,
                        authHeader,
                        "application/json",
                        userIdFilter,
                        dateFilter
                ).execute();

                if (response.isSuccessful() && response.body() != null) {
                    int total = 0;
                    for (WaterLog log : response.body()) {
                        total += log.getAmount_ml();
                    }
                    final int finalTotal = total;
                    currentAmount = finalTotal;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        updateUI(finalTotal);
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.error_data_load_with_msg, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ─── Supabase: Su kaydı ekle ────────────────────────────────────

    private void saveWaterLog(int amountMl) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                PrefManager pref = new PrefManager(this);
                WaterLog log = new WaterLog(pref.getId(), amountMl);

                retrofit2.Response<Void> response = api.insertWaterLog(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        log
                ).execute();

                if (!response.isSuccessful()) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(this, getString(R.string.error_record_failed), Toast.LENGTH_SHORT).show();
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.error_connection_with_msg, e.getMessage()), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    public void navHomePage(View view) {
        Navigation.toMain(this);
        finish();
    }

    public void navProfile(View view) {
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if (userId != -1) {
            Navigation.toProfile(this);
        } else {
            Navigation.toRegister(this);
        }
    }
    public void navMealList(View view){
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }
}