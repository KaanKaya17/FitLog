package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class History extends AppCompatActivity {

    private RecyclerView recyclerHistory;
    private ProgressBar progressBarHistory;
    private HistoryAdapter adapter;
    private List<DayHistory> dayList = new ArrayList<>();

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);

        recyclerHistory    = findViewById(R.id.recyclerHistory);
        progressBarHistory = findViewById(R.id.progressBarHistory);

        adapter = new HistoryAdapter(this, dayList);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerHistory.setAdapter(adapter);

        loadHistory();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadHistory() {
        progressBarHistory.setVisibility(View.VISIBLE);

        PrefManager pref    = new PrefManager(this);
        String userIdFilter = "eq." + pref.getId();

        // 7 gün önce
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String weekFilter = "gte." + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.getTime()) + "T00:00:00Z";

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                // Su ve öğün verilerini çek
                retrofit2.Response<List<WaterLog>> waterResp = api.getTodayWaterLogs(
                        apiKey, authHeader, "application/json", userIdFilter, weekFilter
                ).execute();

                retrofit2.Response<List<MealLog>> mealResp = api.getTodayMealLogs(
                        apiKey, authHeader, "application/json", userIdFilter, weekFilter
                ).execute();

                // Son 7 günü oluştur
                List<DayHistory> days = buildDayList();

                // Map: tarih → DayHistory
                Map<String, DayHistory> dayMap = new HashMap<>();
                for (DayHistory d : days) dayMap.put(d.date, d);

                // Su verilerini günlere dağıt
                if (waterResp.isSuccessful() && waterResp.body() != null) {
                    for (WaterLog w : waterResp.body()) {
                        String date = extractDate(w.getConsumed_at());
                        DayHistory day = dayMap.get(date);
                        if (day != null) day.totalWater += w.getAmount_ml();
                    }
                }

                // Öğün verilerini günlere dağıt
                if (mealResp.isSuccessful() && mealResp.body() != null) {
                    for (MealLog m : mealResp.body()) {
                        String date = extractDate(m.getEaten_at());
                        DayHistory day = dayMap.get(date);
                        if (day != null) {
                            day.totalCalories += m.getCalories();
                            day.totalProtein  += m.getProtein();
                            day.totalCarbs    += m.getCarbohydrates();
                            day.totalFat      += m.getFat();
                            day.meals.add(m);
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBarHistory.setVisibility(View.GONE);
                    dayList.clear();
                    dayList.addAll(days);
                    adapter.notifyDataSetChanged();
                });

            } catch (Exception e) {
                Log.e("HISTORY", "Hata: " + e.getMessage());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progressBarHistory.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.error_data_load_failed), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // ── Son 7 günü oluştur ───────────────────────────────────────────

    private List<DayHistory> buildDayList() {
        List<DayHistory> days = new ArrayList<>();
        SimpleDateFormat dateFmt    = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFmt = new SimpleDateFormat("d MMMM", new Locale("tr"));
       // SimpleDateFormat displayFmt = new SimpleDateFormat("d MMMM", new Locale("tr"));

        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            String date        = dateFmt.format(cal.getTime());
            String displayDate = displayFmt.format(cal.getTime());

            String dayLabel;
            if (i == 0)      dayLabel = getString(R.string.history_today);
            else if (i == 1) dayLabel = getString(R.string.history_yesterday);
            else             dayLabel = new SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.getTime());

            days.add(new DayHistory(date, dayLabel, displayDate));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        return days;
    }

    // ── "2025-03-16T13:45:00+00:00" → "2025-03-16" ──────────────────

    private String extractDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) return "";
        return timestamp.substring(0, 10);
    }

    // ── Navigasyon ───────────────────────────────────────────────────

    public void navHomePage(android.view.View view) {
        Navigation.toMain(this);
        finish();
    }

    public void navProfile(android.view.View view) {
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if (userId != -1) {
            Navigation.toProfile(this);
        } else {
            Navigation.toRegister(this);
        }
        finish();
    }

    public void navMealList(android.view.View view) {
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(android.view.View view) {
        // Zaten History sayfasındayız, tekrar açma
    }
}