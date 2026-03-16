package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextView tvUsername, tvGreeting, tvDate;
    private TextView tvCurrentCalories, tvProteinSummary, tvCarbsSummary, tvFatSummary;
    private ProgressBar progressCalorie, progressWaterHome;
    private TextView tvWaterAmount, tvWaterPercent;
    private TextView tvMealCount, tvTotalCaloriesHome;
    private TextView tvMotivationSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // ── Mevcut test kodları (dokunma) ──────────────────────────
        //InformationCard.getInstance().init(this);
        //InformationCard.getInstance().show("Kalori Nedir?", "Kalori, besinlerin içerdiği enerji miktarını ölçen birimdir.");

        PrefManager manager = new PrefManager(this);
        Toast.makeText(this, "Id : " + manager.getId(), Toast.LENGTH_LONG).show();
        // ────────────────────────────────────────────────────────────

        initViews();
        setupGreeting();
        setupCardClicks();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Başka sayfadan dönünce verileri yenile
        loadDashboardData();
    }

    // ── View bağlantıları ────────────────────────────────────────────

    private void initViews() {
        tvUsername          = findViewById(R.id.tvUsername);
        tvGreeting          = findViewById(R.id.tvGreeting);
        tvDate              = findViewById(R.id.tvDate);
        tvCurrentCalories   = findViewById(R.id.tvCurrentCalories);
        tvProteinSummary    = findViewById(R.id.tvProteinSummary);
        tvCarbsSummary      = findViewById(R.id.tvCarbsSummary);
        tvFatSummary        = findViewById(R.id.tvFatSummary);
        progressCalorie     = findViewById(R.id.progressCalorie);
        tvWaterAmount       = findViewById(R.id.tvWaterAmount);
        tvWaterPercent      = findViewById(R.id.tvWaterPercent);
        progressWaterHome   = findViewById(R.id.progressWaterHome);
        tvMealCount         = findViewById(R.id.tvMealCount);
        tvTotalCaloriesHome = findViewById(R.id.tvTotalCaloriesHome);
        tvMotivationSub     = findViewById(R.id.tvMotivationSub);
    }

    // ── Selamlama & Tarih ────────────────────────────────────────────

    private void setupGreeting() {
        PrefManager pref = new PrefManager(this);
        String firstName = pref.getFirstName();
        tvUsername.setText(firstName == null || firstName.isEmpty() ? "Kullanıcı" : firstName);

        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText("Günaydın 👋");
        else if (hour < 18) tvGreeting.setText("İyi günler 👋");
        else                tvGreeting.setText("İyi akşamlar 👋");

        String date = new SimpleDateFormat("d MMMM", new Locale("tr")).format(new Date());
        tvDate.setText(date);
    }

    // ── Kart tıklamaları ─────────────────────────────────────────────

    private void setupCardClicks() {
        findViewById(R.id.cardWater).setOnClickListener(v ->
                Navigation.toWaterTrack(this)
        );
        findViewById(R.id.cardMeal).setOnClickListener(v ->
                Navigation.toAddMeal(this)
        );
    }

    // ── Supabase: Dashboard verisi ───────────────────────────────────

    private void loadDashboardData() {
        PrefManager pref = new PrefManager(this);
        int userId = pref.getId();
        if (userId == -1) return;

        String today        = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String userIdFilter = "eq." + userId;
        String dateFilter   = "gte." + today + "T00:00:00Z";

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                // Su verisi
                retrofit2.Response<List<WaterLog>> waterResp = api.getTodayWaterLogs(
                        apiKey, authHeader, "application/json", userIdFilter, dateFilter
                ).execute();

                // Öğün verisi
                retrofit2.Response<List<MealLog>> mealResp = api.getTodayMealLogs(
                        apiKey, authHeader, "application/json", userIdFilter, dateFilter
                ).execute();

                // Toplamları hesapla
                int totalWater     = 0;
                int totalCalories  = 0;
                float totalProtein = 0, totalCarbs = 0, totalFat = 0;
                int mealCount      = 0;

                if (waterResp.isSuccessful() && waterResp.body() != null) {
                    for (WaterLog w : waterResp.body())
                        totalWater += w.getAmount_ml();
                }

                if (mealResp.isSuccessful() && mealResp.body() != null) {
                    mealCount = mealResp.body().size();
                    for (MealLog m : mealResp.body()) {
                        totalCalories += m.getCalories();
                        totalProtein  += m.getProtein();
                        totalCarbs    += m.getCarbohydrates();
                        totalFat      += m.getFat();
                    }
                }

                final int fWater      = totalWater;
                final int fCalories   = totalCalories;
                final float fProtein  = totalProtein;
                final float fCarbs    = totalCarbs;
                final float fFat      = totalFat;
                final int fMealCount  = mealCount;

                runOnUiThread(() -> updateDashboard(fWater, fCalories, fProtein, fCarbs, fFat, fMealCount));

            } catch (Exception e) {
                Log.e("DASHBOARD", "Veri yüklenemedi: " + e.getMessage());
            }
        }).start();
    }

    // ── UI Güncelle ──────────────────────────────────────────────────

    private void updateDashboard(int water, int calories, float protein, float carbs, float fat, int mealCount) {
        PrefManager pref = new PrefManager(this);
        int calorieGoal  = pref.getCalorieGoal();
        int waterGoal    = pref.getWaterGoal();

        // ProgressBar max değerlerini hedeflere göre ayarla
        progressCalorie.setMax(calorieGoal);
        progressWaterHome.setMax(waterGoal);

        // Kalori
        tvCurrentCalories.setText(String.valueOf(calories));
        ((TextView) findViewById(R.id.tvCalorieSummary)).setText("/ " + calorieGoal + " hedef");
        animateProgress(progressCalorie, calories, calorieGoal);

        // Makrolar
        tvProteinSummary.setText(String.format(Locale.getDefault(), "%.1fg", protein));
        tvCarbsSummary.setText(String.format(Locale.getDefault(), "%.1fg", carbs));
        tvFatSummary.setText(String.format(Locale.getDefault(), "%.1fg", fat));

        // Su
        tvWaterAmount.setText(water + " / " + waterGoal + " ml");
        animateProgress(progressWaterHome, water, waterGoal);
        int waterPercent = (int) Math.min((water / (float) waterGoal) * 100, 100);
        tvWaterPercent.setText("%" + waterPercent + " tamamlandı");

        // Öğün
        tvMealCount.setText(mealCount + " öğün");
        tvTotalCaloriesHome.setText(calories + " kcal");

        // Motivasyon
        if (mealCount == 0) {
            tvMotivationSub.setText("Bugün henüz öğün eklemedin.");
        } else if (calories < calorieGoal * 0.5f) {
            tvMotivationSub.setText("İyi başlangıç, devam et! 💪");
        } else if (calories < calorieGoal) {
            tvMotivationSub.setText("Hedefe yaklaşıyorsun!");
        } else {
            tvMotivationSub.setText("Günlük kalori hedefine ulaştın! 🎉");
        }
    }

    // ── Animasyonlu progress bar ─────────────────────────────────────

    private void animateProgress(ProgressBar progressBar, int target, int max) {
        int safeTarget = Math.min(target, max);
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), safeTarget);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

    // ── Mevcut nav metodları (dokunma) ──────────────────────────────

    public void navHomePage(View view) {
        Navigation.toMain(this);
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

    public void closeInformation(View view) {
        InformationCard.getInstance().hide();
    }

    public void navMealList(View view){
        Navigation.toMealList(this);
    }

    public void navStatistic(View view) {
        Navigation.toSettings(this);
    }
}