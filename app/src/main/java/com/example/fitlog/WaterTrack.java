package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class WaterTrack extends AppCompatActivity {

    private static final int DAILY_GOAL_ML = 2000;

    ProgressBar progressWater;
    TextView tvCurrentAmount;
    TextView tvPercent;
    EditText editTextCustomAmount;

    int currentAmount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water_track);

        progressWater       = findViewById(R.id.progressWater);
        tvCurrentAmount     = findViewById(R.id.tvCurrentAmount);
        tvPercent           = findViewById(R.id.tvPercent);
        editTextCustomAmount = findViewById(R.id.editTextCustomAmount);

        // Bugünkü toplam suyu Supabase'den çek
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
            Toast.makeText(this, "Miktar giriniz.", Toast.LENGTH_SHORT).show();
            return;
        }
        int amount = Integer.parseInt(input);
        if (amount <= 0) {
            Toast.makeText(this, "Geçerli bir miktar giriniz.", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "Gün sıfırlandı.", Toast.LENGTH_SHORT).show();
    }

    // ─── Su ekleme ──────────────────────────────────────────────────

    private void addWater(int amountMl) {
        currentAmount += amountMl;
        updateUI(currentAmount);
        saveWaterLog(amountMl);
    }

    // ─── Animasyonlu UI güncelleme ──────────────────────────────────

    private void updateUI(int totalMl) {
        // Sayı animasyonu (progress bar)
        ObjectAnimator animator = ObjectAnimator.ofInt(progressWater, "progress", progressWater.getProgress(), totalMl);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        // Metin güncelle
        tvCurrentAmount.setText(String.valueOf(totalMl));

        int percent = (int) ((totalMl / (float) DAILY_GOAL_ML) * 100);
        percent = Math.min(percent, 100); // 100'ü geçmesin

        tvPercent.setText("%" + percent + " tamamlandı");

        // Hedefe ulaşıldıysa rengi değiştir
        if (totalMl >= DAILY_GOAL_ML) {
            tvPercent.setText("🎉 Günlük hedefe ulaştın!");
            tvPercent.setTextColor(getColor(android.R.color.holo_blue_dark));
        } else {
            tvPercent.setTextColor(getColor(R.color.green)); // colors.xml'deki #4CAF50
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
                    runOnUiThread(() -> updateUI(finalTotal));
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Veri yüklenemedi: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
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
                    runOnUiThread(() ->
                            Toast.makeText(this, "Kayıt sırasında hata oluştu.", Toast.LENGTH_SHORT).show()
                    );
                }

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Bağlantı hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}