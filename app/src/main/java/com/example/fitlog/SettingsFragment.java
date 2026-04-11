package com.example.fitlog;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

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
                } else {
                    pref.setWaterReminderEnabled(false);
                    switchWaterReminder.setChecked(false);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
        pref = new PrefManager(getContext());

        Spinner spinnerLanguage = view.findViewById(R.id.spinnerLanguage);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                getContext(), R.array.languages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(adapter);

        String currentLang = pref.getLanguage();
        spinnerLanguage.setSelection(currentLang.equals("tr") ? 1 : 0);

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            boolean isFirstLoad = true;
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                if (isFirstLoad) { isFirstLoad = false; return; }
                String selectedLang = (position == 0) ? "en" : "tr";
                if (!selectedLang.equals(pref.getLanguage())) {
                    pref.setLanguage(selectedLang);
                    Bundle langBundle = new Bundle();
                    langBundle.putString("new_language", selectedLang);
                    firebaseAnalytics.logEvent("language_changed", langBundle);
                    restartApp();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        initViews(view);
        loadCurrentGoals();
        setupSeekBars();
        setupReminders();

        view.findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveSettings());
        View btnAboutProject = view.findViewById(R.id.layoutAbout);
        if (btnAboutProject != null) {
            btnAboutProject.setOnClickListener(v -> androidx.navigation.Navigation.findNavController(v).navigate(R.id.aboutProjectFragment));
        }
    }

    private void restartApp() {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        getActivity().finish();
    }

    private void initViews(View view) {
        seekBarCalorie       = view.findViewById(R.id.seekBarCalorie);
        seekBarWater         = view.findViewById(R.id.seekBarWater);
        tvCalorieGoalValue   = view.findViewById(R.id.tvCalorieGoalValue);
        tvWaterGoalValue     = view.findViewById(R.id.tvWaterGoalValue);
        tvCurrentCalorieGoal = view.findViewById(R.id.tvCurrentCalorieGoal);
        tvCurrentWaterGoal   = view.findViewById(R.id.tvCurrentWaterGoal);
        switchWaterReminder  = view.findViewById(R.id.switchWaterReminder);
    }

    private void loadCurrentGoals() {
        int calorieGoal = pref.getCalorieGoal();
        int waterGoal   = pref.getWaterGoal();
        seekBarCalorie.setProgress(calorieGoal - MIN_CALORIE);
        seekBarWater.setProgress(waterGoal - MIN_WATER);
        tvCalorieGoalValue.setText(String.valueOf(calorieGoal));
        tvWaterGoalValue.setText(String.valueOf(waterGoal));
        tvCurrentCalorieGoal.setText(String.valueOf(calorieGoal));
        tvCurrentWaterGoal.setText(String.valueOf(waterGoal));
    }

    private void setupSeekBars() {
        seekBarCalorie.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.round((progress + MIN_CALORIE) / 50f) * 50;
                tvCalorieGoalValue.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekBarWater.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = Math.round((progress + MIN_WATER) / 100f) * 100;
                tvWaterGoalValue.setText(String.valueOf(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void setupReminders() {
        if (getContext() == null) return;
        switchWaterReminder.setChecked(pref.isWaterReminderEnabled());
        NotificationHelper.createNotificationChannel(getContext());

        switchWaterReminder.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
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
                Bundle b = new Bundle(); b.putBoolean("water_reminder_enabled", true);
                firebaseAnalytics.logEvent("settings_reminder_changed", b);
            } else {
                pref.setWaterReminderEnabled(false);
                cancelWaterReminder();
                Bundle b = new Bundle(); b.putBoolean("water_reminder_enabled", false);
                firebaseAnalytics.logEvent("settings_reminder_changed", b);
            }
        });
    }

    private void scheduleWaterReminder() {
        if (getContext() == null) return;
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getContext(), WaterReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getContext(), 100, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            long interval = 2 * 60 * 60 * 1000;
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + interval, interval, pendingIntent);
        }
    }

    private void cancelWaterReminder() {
        if (getContext() == null) return;
        AlarmManager alarmManager = (AlarmManager) getContext().getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(getContext(), WaterReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(getContext(), 100, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) alarmManager.cancel(pendingIntent);
    }

    private void saveSettings() {
        int calorieGoal = Integer.parseInt(tvCalorieGoalValue.getText().toString());
        int waterGoal   = Integer.parseInt(tvWaterGoalValue.getText().toString());
        pref.setCalorieGoal(calorieGoal);
        pref.setWaterGoal(waterGoal);
        tvCurrentCalorieGoal.setText(String.valueOf(calorieGoal));
        tvCurrentWaterGoal.setText(String.valueOf(waterGoal));

        Bundle goalsBundle = new Bundle();
        goalsBundle.putInt("new_calorie_goal", calorieGoal);
        goalsBundle.putInt("new_water_goal", waterGoal);
        firebaseAnalytics.logEvent("goals_updated", goalsBundle);

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

                retrofit2.Response<Void> response = api.updateUserGoals(apiKey, authHeader, "application/json", "return=minimal", userFilter, body).execute();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "✅ Hedefler kaydedildi!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getContext(), "Kayıt sırasında hata oluştu.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("SETTINGS", "Bağlantı hatası: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Bağlantı hatası.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
