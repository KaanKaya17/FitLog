package com.example.fitlog;

import android.content.res.Configuration;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Settings extends AppCompatActivity {

    private static final int MIN_CALORIE = 500;
    private static final int MIN_WATER   = 500;

    private SeekBar seekBarCalorie, seekBarWater;
    private TextView tvCalorieGoalValue, tvWaterGoalValue;
    private TextView tvCurrentCalorieGoal, tvCurrentWaterGoal;
    private PrefManager pref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        pref = new PrefManager(this);

        Spinner spinnerLanguage;
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.languages,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                if(position == 0){
                    setLocale("en");
                }
                else if(position == 1){
                    setLocale("tr");
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        initViews();
        loadCurrentGoals();
        setupSeekBars();

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveSettings());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setLocale(String lang){

        Locale locale = new Locale(lang);
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.setLocale(locale);

        getResources().updateConfiguration(config, getResources().getDisplayMetrics());

        recreate();
    }

    private void initViews() {
        seekBarCalorie        = findViewById(R.id.seekBarCalorie);
        seekBarWater          = findViewById(R.id.seekBarWater);
        tvCalorieGoalValue    = findViewById(R.id.tvCalorieGoalValue);
        tvWaterGoalValue      = findViewById(R.id.tvWaterGoalValue);
        tvCurrentCalorieGoal  = findViewById(R.id.tvCurrentCalorieGoal);
        tvCurrentWaterGoal    = findViewById(R.id.tvCurrentWaterGoal);
    }

    // ── Mevcut hedefleri yükle ───────────────────────────────────────

    private void loadCurrentGoals() {
        int calorieGoal = pref.getCalorieGoal();
        int waterGoal   = pref.getWaterGoal();

        // Slider'ları ayarla (min değerleri çıkararak)
        seekBarCalorie.setProgress(calorieGoal - MIN_CALORIE);
        seekBarWater.setProgress(waterGoal - MIN_WATER);

        // Değer text'lerini güncelle
        tvCalorieGoalValue.setText(String.valueOf(calorieGoal));
        tvWaterGoalValue.setText(String.valueOf(waterGoal));

        // Özet kartını güncelle
        tvCurrentCalorieGoal.setText(String.valueOf(calorieGoal));
        tvCurrentWaterGoal.setText(String.valueOf(waterGoal));
    }

    // ── SeekBar listener'ları ────────────────────────────────────────

    private void setupSeekBars() {
        seekBarCalorie.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + MIN_CALORIE;
                // 50'nin katlarına yuvarla
                value = Math.round(value / 50f) * 50;
                tvCalorieGoalValue.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarWater.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + MIN_WATER;
                // 100'ün katlarına yuvarla
                value = Math.round(value / 100f) * 100;
                tvWaterGoalValue.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ── Kaydet ──────────────────────────────────────────────────────

    private void saveSettings() {
        int calorieGoal = Integer.parseInt(tvCalorieGoalValue.getText().toString());
        int waterGoal   = Integer.parseInt(tvWaterGoalValue.getText().toString());

        // Önce SharedPreferences'a kaydet (anında etki etsin)
        pref.setCalorieGoal(calorieGoal);
        pref.setWaterGoal(waterGoal);

        // Özet kartını güncelle
        tvCurrentCalorieGoal.setText(String.valueOf(calorieGoal));
        tvCurrentWaterGoal.setText(String.valueOf(waterGoal));

        // Supabase'e kaydet
        saveToSupabase(calorieGoal, waterGoal);
    }

    private void saveToSupabase(int calorieGoal, int waterGoal) {
        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                String userFilter = "eq." + pref.getId();

                Map<String, Integer> body = new HashMap<>();
                body.put("daily_calorie_goal", calorieGoal);
                body.put("daily_water_goal_ml", waterGoal);

                retrofit2.Response<Void> response = api.updateUserGoals(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        userFilter,
                        body
                ).execute();

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(this, "✅ Hedefler kaydedildi!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Kayıt sırasında hata oluştu.", Toast.LENGTH_SHORT).show();
                        Log.e("SETTINGS", "Hata: " + response.code());
                    }
                });

            } catch (Exception e) {
                Log.e("SETTINGS", "Bağlantı hatası: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Bağlantı hatası.", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}