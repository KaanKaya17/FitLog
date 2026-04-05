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

import com.google.firebase.analytics.FirebaseAnalytics;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Switch;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

public class Settings extends AppCompatActivity {

    private static final int MIN_CALORIE = 500;
    private static final int MIN_WATER   = 500;

    private SeekBar seekBarCalorie, seekBarWater;
    private TextView tvCalorieGoalValue, tvWaterGoalValue;
    private TextView tvCurrentCalorieGoal, tvCurrentWaterGoal;
    private PrefManager pref;
    private Switch switchWaterReminder;
    private FirebaseAnalytics firebaseAnalytics;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    pref.setWaterReminderEnabled(true);
                    switchWaterReminder.setChecked(true);
                    scheduleWaterReminder();
                    Toast.makeText(this, "Bildirimler aktif / Notifications active", Toast.LENGTH_SHORT).show();
                } else {
                    pref.setWaterReminderEnabled(false);
                    switchWaterReminder.setChecked(false);
                    Toast.makeText(this, "İzin Reddedildi / Permission Denied", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

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

        // Şu anki dili seçili getir
        String currentLang = pref.getLanguage();
        if (currentLang.equals("tr")) {
            spinnerLanguage.setSelection(1);
        } else {
            spinnerLanguage.setSelection(0);
        }

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isFirstLoad = true;

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isFirstLoad) {
                    isFirstLoad = false;
                    return;
                }

                String selectedLang = (position == 0) ? "en" : "tr";
                String currentLang = pref.getLanguage();

                if (!selectedLang.equals(currentLang)) {
                    // Dili kaydet ve uygulamayı yeniden başlat
                    pref.setLanguage(selectedLang);
                    
                    // Firebase Analytics: Dil değişimi
                    Bundle langBundle = new Bundle();
                    langBundle.putString("new_language", selectedLang);
                    firebaseAnalytics.logEvent("language_changed", langBundle);
                    
                    restartApp();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        initViews();
        loadCurrentGoals();
        setupSeekBars();
        setupReminders();

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveSettings());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void restartApp() {
        android.content.Intent intent = new android.content.Intent(this, MainActivity.class);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void initViews() {
        seekBarCalorie        = findViewById(R.id.seekBarCalorie);
        seekBarWater          = findViewById(R.id.seekBarWater);
        tvCalorieGoalValue    = findViewById(R.id.tvCalorieGoalValue);
        tvWaterGoalValue      = findViewById(R.id.tvWaterGoalValue);
        tvCurrentCalorieGoal  = findViewById(R.id.tvCurrentCalorieGoal);
        tvCurrentWaterGoal    = findViewById(R.id.tvCurrentWaterGoal);
        switchWaterReminder   = findViewById(R.id.switchWaterReminder);
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

    private void setupReminders() {
        switchWaterReminder.setChecked(pref.isWaterReminderEnabled());
        
        NotificationHelper.createNotificationChannel(this);

        switchWaterReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED) {
                        pref.setWaterReminderEnabled(true);
                        scheduleWaterReminder();
                    } else {
                        switchWaterReminder.setChecked(false);
                        requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                    }
                } else {
                    pref.setWaterReminderEnabled(true);
                    scheduleWaterReminder();
                }
                
                // Analytics: Bildirim açıldı
                Bundle notifBundle = new Bundle();
                notifBundle.putBoolean("water_reminder_enabled", true);
                firebaseAnalytics.logEvent("settings_reminder_changed", notifBundle);
            } else {
                pref.setWaterReminderEnabled(false);
                cancelWaterReminder();
                
                // Analytics: Bildirim kapatıldı
                Bundle notifBundle = new Bundle();
                notifBundle.putBoolean("water_reminder_enabled", false);
                firebaseAnalytics.logEvent("settings_reminder_changed", notifBundle);
            }
        });
    }

    private void scheduleWaterReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, WaterReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            long testInterval = 2 * 60 * 60 * 1000; // 2 saat (2 hours)
            alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + testInterval,
                    testInterval,
                    pendingIntent
            );
        }
    }

    private void cancelWaterReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, WaterReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
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

        // Firebase Analytics: Hedefler Değiştirildi
        Bundle goalsBundle = new Bundle();
        goalsBundle.putInt("new_calorie_goal", calorieGoal);
        goalsBundle.putInt("new_water_goal", waterGoal);
        firebaseAnalytics.logEvent("goals_updated", goalsBundle);

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
        finish();
    }

    public void navMealList(View view){
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }

    public void btnAboutProject(View view){
        Navigation.toAboutProject(this);
    }
}