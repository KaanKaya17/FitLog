package com.example.fitlog;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.fitlog.domain.NutritionCalculator;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ProfileViewModel;
import com.example.fitlog.ui.TargetsDialog;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Locale;

public class SettingsFragment extends BaseFragment {

    private static final int MIN_CALORIE = 500;
    private static final int MIN_WATER   = 500;
    static final int[] INTERVALS = {1, 2, 3, 4};
    static final String[] THEMES = {"system", "light", "dark"};

    private SeekBar seekBarCalorie, seekBarWater;
    private TextView tvCalorieGoalValue, tvWaterGoalValue, tvCurrentCalorieGoal, tvCurrentWaterGoal;
    private Switch switchWaterReminder, switchMealReminder;
    private View layoutReminderOptions;
    private PrefManager pref;
    private FirebaseAnalytics analytics;
    private ProfileViewModel vm;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                pref.setWaterReminderEnabled(granted);
                switchWaterReminder.setChecked(granted);
                ReminderScheduler.apply(requireContext());
                updateReminderOptions();
            });

    private final ActivityResultLauncher<String> mealNotificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                pref.setMealReminderEnabled(granted);
                switchMealReminder.setChecked(granted);
                MealReminderReceiver.apply(requireContext());
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        analytics = FirebaseAnalytics.getInstance(requireContext());
        pref = new PrefManager(requireContext());
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);
        bindCommon(vm);

        seekBarCalorie       = view.findViewById(R.id.seekBarCalorie);
        seekBarWater         = view.findViewById(R.id.seekBarWater);
        tvCalorieGoalValue   = view.findViewById(R.id.tvCalorieGoalValue);
        tvWaterGoalValue     = view.findViewById(R.id.tvWaterGoalValue);
        tvCurrentCalorieGoal = view.findViewById(R.id.tvCurrentCalorieGoal);
        tvCurrentWaterGoal   = view.findViewById(R.id.tvCurrentWaterGoal);
        switchWaterReminder  = view.findViewById(R.id.switchWaterReminder);
        layoutReminderOptions = view.findViewById(R.id.layoutReminderOptions);

        setupLanguage(view.findViewById(R.id.spinnerLanguage));
        setupTheme(view.findViewById(R.id.spinnerTheme));
        loadCurrentGoals();
        setupSeekBars();
        setupReminders(view);

        view.findViewById(R.id.btnSaveSettings).setOnClickListener(v -> saveGoals());
        view.findViewById(R.id.btnCalcFromProfile).setOnClickListener(v -> {
            NutritionCalculator.Targets t = vm.suggestTargets();
            if (t == null) { toast(R.string.error_profile_incomplete); return; }
            TargetsDialog.show(requireContext(), t, () -> vm.applyTargets(t), null);
        });
        vm.targetsApplied().observe(getViewLifecycleOwner(), e -> { if (e.consume() != null) loadCurrentGoals(); });
        setupExport(view);

        setupPrivacy(view);

        View about = view.findViewById(R.id.layoutAbout);
        if (about != null) about.setOnClickListener(v -> navigate(R.id.aboutProjectFragment));
    }

    // ── Gizlilik ───────────────────────────────────────────────────────

    private void setupPrivacy(View view) {
        Switch sw = view.findViewById(R.id.switchAnalytics);
        sw.setChecked(ConsentManager.isGranted(requireContext()));
        sw.setOnCheckedChangeListener((b, checked) -> ConsentManager.setGranted(requireContext(), checked));
        view.findViewById(R.id.layoutAnalyticsConsent).setOnClickListener(v -> sw.toggle());
        view.findViewById(R.id.layoutPrivacyPolicy)
                .setOnClickListener(v -> ConsentManager.openPrivacyPolicy(requireContext()));
    }

    // ── Veri dışa aktarma ──────────────────────────────────────────────

    private void setupExport(View view) {
        com.example.fitlog.ui.ExportViewModel exportVm =
                new ViewModelProvider(this).get(com.example.fitlog.ui.ExportViewModel.class);
        View btn = view.findViewById(R.id.btnExportData);
        btn.setOnClickListener(v -> {
            if (pref.getId() == -1) { toast(R.string.error_not_logged_in); return; }
            exportVm.start();
        });
        exportVm.export().observe(getViewLifecycleOwner(), res -> {
            btn.setEnabled(!res.isLoading());
            if (res.isError()) com.example.fitlog.ui.ErrorMessages.toast(requireContext(), res.error);
            if (res.isSuccess() && res.data != null) {
                Intent share = new Intent(Intent.ACTION_SEND)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_STREAM, res.data)
                        .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_subject))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, getString(R.string.settings_export_data)));
                analytics.logEvent("data_exported", null);
            }
        });
    }

    // ── Dil / tema ─────────────────────────────────────────────────────

    private void setupLanguage(Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(), R.array.languages, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection("tr".equals(pref.getLanguage()) ? 1 : 0, false);
        spinner.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            String lang = position == 0 ? "en" : "tr";
            if (lang.equals(pref.getLanguage())) return;
            pref.setLanguage(lang);
            Bundle b = new Bundle();
            b.putString("new_language", lang);
            analytics.logEvent("language_changed", b);
            restartApp();
        }));
    }

    private void setupTheme(Spinner spinner) {
        String[] labels = {getString(R.string.theme_system), getString(R.string.theme_light), getString(R.string.theme_dark)};
        spinner.setAdapter(simpleAdapter(labels));
        int current = 0;
        for (int i = 0; i < THEMES.length; i++) if (THEMES[i].equals(pref.getThemeMode())) current = i;
        spinner.setSelection(current, false);
        spinner.setOnItemSelectedListener(new SimpleItemSelected(position -> {
            if (THEMES[position].equals(pref.getThemeMode())) return;
            pref.setThemeMode(THEMES[position]);
            AppCompatDelegate.setDefaultNightMode(nightModeOf(THEMES[position]));
        }));
    }

    public static int nightModeOf(String mode) {
        if ("light".equals(mode)) return AppCompatDelegate.MODE_NIGHT_NO;
        if ("dark".equals(mode)) return AppCompatDelegate.MODE_NIGHT_YES;
        return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }

    private void restartApp() {
        Intent intent = new Intent(requireActivity(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    // ── Hedefler ───────────────────────────────────────────────────────

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
        seekBarCalorie.setOnSeekBarChangeListener(new SimpleSeek(progress ->
                tvCalorieGoalValue.setText(String.valueOf(Math.round((progress + MIN_CALORIE) / 50f) * 50))));
        seekBarWater.setOnSeekBarChangeListener(new SimpleSeek(progress ->
                tvWaterGoalValue.setText(String.valueOf(Math.round((progress + MIN_WATER) / 100f) * 100))));
    }

    private void saveGoals() {
        int calorieGoal = Integer.parseInt(tvCalorieGoalValue.getText().toString());
        int waterGoal   = Integer.parseInt(tvWaterGoalValue.getText().toString());
        if (pref.getId() == -1) { toast(R.string.error_not_logged_in); return; }
        vm.saveGoals(calorieGoal, waterGoal);
        tvCurrentCalorieGoal.setText(String.valueOf(calorieGoal));
        tvCurrentWaterGoal.setText(String.valueOf(waterGoal));

        Bundle b = new Bundle();
        b.putInt("new_calorie_goal", calorieGoal);
        b.putInt("new_water_goal", waterGoal);
        analytics.logEvent("goals_updated", b);
    }

    // ── Hatırlatıcı ────────────────────────────────────────────────────

    private void setupReminders(View view) {
        NotificationHelper.createNotificationChannel(requireContext());
        switchWaterReminder.setChecked(pref.isWaterReminderEnabled());
        updateReminderOptions();

        switchMealReminder = view.findViewById(R.id.switchMealReminder);
        switchMealReminder.setChecked(pref.isMealReminderEnabled());
        switchMealReminder.setOnCheckedChangeListener((b, checked) -> {
            if (checked && ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                switchMealReminder.setChecked(false);
                mealNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            pref.setMealReminderEnabled(checked);
            MealReminderReceiver.apply(requireContext());
        });

        switchWaterReminder.setOnCheckedChangeListener((b, checked) -> {
            if (checked && ContextCompat.checkSelfPermission(requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                switchWaterReminder.setChecked(false);
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                return;
            }
            pref.setWaterReminderEnabled(checked);
            ReminderScheduler.apply(requireContext());
            updateReminderOptions();
            Bundle e = new Bundle();
            e.putBoolean("water_reminder_enabled", checked);
            analytics.logEvent("settings_reminder_changed", e);
        });

        Spinner interval = view.findViewById(R.id.spinnerReminderInterval);
        String[] intervalLabels = new String[INTERVALS.length];
        int sel = 1;
        for (int i = 0; i < INTERVALS.length; i++) {
            intervalLabels[i] = getString(R.string.reminder_every_hours, INTERVALS[i]);
            if (INTERVALS[i] == pref.getReminderIntervalHours()) sel = i;
        }
        interval.setAdapter(simpleAdapter(intervalLabels));
        interval.setSelection(sel, false);
        interval.setOnItemSelectedListener(new SimpleItemSelected(pos -> {
            pref.setReminderIntervalHours(INTERVALS[pos]);
            ReminderScheduler.apply(requireContext());
        }));

        String[] hours = new String[24];
        for (int h = 0; h < 24; h++) hours[h] = String.format(Locale.ROOT, "%02d:00", h);
        Spinner start = view.findViewById(R.id.spinnerQuietStart);
        Spinner end = view.findViewById(R.id.spinnerQuietEnd);
        start.setAdapter(simpleAdapter(hours));
        end.setAdapter(simpleAdapter(hours));
        start.setSelection(pref.getQuietStartHour(), false);
        end.setSelection(pref.getQuietEndHour(), false);
        AdapterView.OnItemSelectedListener quiet = new SimpleItemSelected(pos ->
                pref.setQuietHours(start.getSelectedItemPosition(), end.getSelectedItemPosition()));
        start.setOnItemSelectedListener(quiet);
        end.setOnItemSelectedListener(quiet);
    }

    private void updateReminderOptions() {
        layoutReminderOptions.setAlpha(pref.isWaterReminderEnabled() ? 1f : 0.4f);
    }

    private ArrayAdapter<String> simpleAdapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    // ── Küçük dinleyici yardımcıları ───────────────────────────────────

    private static final class SimpleItemSelected implements AdapterView.OnItemSelectedListener {
        private final java.util.function.IntConsumer onSelect;
        SimpleItemSelected(java.util.function.IntConsumer onSelect) { this.onSelect = onSelect; }
        @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) { onSelect.accept(position); }
        @Override public void onNothingSelected(AdapterView<?> p) {}
    }

    private static final class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final java.util.function.IntConsumer onChange;
        SimpleSeek(java.util.function.IntConsumer onChange) { this.onChange = onChange; }
        @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) { onChange.accept(progress); }
        @Override public void onStartTrackingTouch(SeekBar s) {}
        @Override public void onStopTrackingTouch(SeekBar s) {}
    }
}
