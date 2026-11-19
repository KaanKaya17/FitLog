package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.HomeViewModel;
import com.google.firebase.messaging.FirebaseMessaging;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;

public class HomeFragment extends BaseFragment {

    private TextView tvUsername, tvGreeting, tvDate, tvStreak;
    private TextView tvCurrentCalories, tvProteinSummary, tvCarbsSummary, tvFatSummary, tvCalorieSummary;
    private ProgressBar progressCalorie, progressWaterHome;
    private TextView tvWaterAmount, tvWaterPercent;
    private TextView tvMealCount, tvTotalCaloriesHome;
    private TextView tvMotivationSub, tvHomeWeight, tvHomeWeightSub;

    private HomeViewModel vm;
    private PrefManager pref;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        pref = new PrefManager(requireContext());
        vm = new ViewModelProvider(this).get(HomeViewModel.class);
        bindCommon(vm);

        initViews(view);
        setupGreeting();
        setupCardClicks(view);

        vm.dashboard().observe(getViewLifecycleOwner(), res -> {
            if (res.data != null) render(res.data);
        });
        vm.notification().observe(getViewLifecycleOwner(), e -> {
            AdminNotification note = e.consume();
            if (note != null) showNotificationDialog(note);
        });

        if (isLoggedIn()) {
            vm.checkNotifications();
            checkFcmToken();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        UserStatusChecker.check(requireActivity());
        if (isLoggedIn()) vm.refresh();
        else renderEmpty();
    }

    private boolean isLoggedIn() {
        return pref.getId() != -1 && AuthManager.hasSession(requireContext());
    }

    private void initViews(View view) {
        tvUsername          = view.findViewById(R.id.tvUsername);
        tvGreeting          = view.findViewById(R.id.tvGreeting);
        tvDate              = view.findViewById(R.id.tvDate);
        tvStreak            = view.findViewById(R.id.tvStreak);
        tvCurrentCalories   = view.findViewById(R.id.tvCurrentCalories);
        tvCalorieSummary    = view.findViewById(R.id.tvCalorieSummary);
        tvProteinSummary    = view.findViewById(R.id.tvProteinSummary);
        tvCarbsSummary      = view.findViewById(R.id.tvCarbsSummary);
        tvFatSummary        = view.findViewById(R.id.tvFatSummary);
        progressCalorie     = view.findViewById(R.id.progressCalorie);
        tvWaterAmount       = view.findViewById(R.id.tvWaterAmount);
        tvWaterPercent      = view.findViewById(R.id.tvWaterPercent);
        progressWaterHome   = view.findViewById(R.id.progressWaterHome);
        tvMealCount         = view.findViewById(R.id.tvMealCount);
        tvTotalCaloriesHome = view.findViewById(R.id.tvTotalCaloriesHome);
        tvMotivationSub     = view.findViewById(R.id.tvMotivationSub);
        tvHomeWeight        = view.findViewById(R.id.tvHomeWeight);
        tvHomeWeightSub     = view.findViewById(R.id.tvHomeWeightSub);
    }

    private void setupGreeting() {
        String firstName = pref.getFirstName();
        tvUsername.setText(firstName == null || firstName.isEmpty() ? getString(R.string.home_default_user) : firstName);

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText(R.string.home_greeting);
        else if (hour < 18) tvGreeting.setText(R.string.home_greeting_nice_day);
        else                tvGreeting.setText(R.string.home_greeting_night);

        tvDate.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", appLocale())));
    }

    private Locale appLocale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private void setupCardClicks(View view) {
        view.findViewById(R.id.cardWater).setOnClickListener(v -> navigate(R.id.waterTrackFragment));
        view.findViewById(R.id.cardMeal).setOnClickListener(v -> navigate(R.id.mealListFragment));
        view.findViewById(R.id.btnNavAddMeal).setOnClickListener(v -> navigate(R.id.addMealFragment));
        view.findViewById(R.id.btnNavAddWater).setOnClickListener(v -> navigate(R.id.waterTrackFragment));
        view.findViewById(R.id.cardAiCoach).setOnClickListener(v -> navigate(R.id.aiCoachFragment));
        view.findViewById(R.id.cardWeight).setOnClickListener(v -> navigate(R.id.weightFragment));
    }

    private void renderEmpty() {
        DayHistory empty = new DayHistory(LocalDate.now().toString(), "", "");
        render(new HomeViewModel.Dashboard(empty, 0, null));
    }

    private void render(HomeViewModel.Dashboard d) {
        if (!isAdded()) return;
        DayHistory t = d.today;
        int calorieGoal = Math.max(1, pref.getCalorieGoal());
        int waterGoal   = Math.max(1, pref.getWaterGoal());

        progressCalorie.setMax(calorieGoal);
        progressWaterHome.setMax(waterGoal);

        tvCurrentCalories.setText(String.valueOf(t.totalCalories));
        tvCalorieSummary.setText(getString(R.string.home_calorie_goal_format, calorieGoal));
        animateProgress(progressCalorie, t.totalCalories, calorieGoal);

        tvProteinSummary.setText(macro(t.totalProtein, pref.getProteinGoal()));
        tvCarbsSummary.setText(macro(t.totalCarbs, pref.getCarbsGoal()));
        tvFatSummary.setText(macro(t.totalFat, pref.getFatGoal()));

        tvWaterAmount.setText(getString(R.string.home_water_progress_format, t.totalWater, waterGoal));
        animateProgress(progressWaterHome, t.totalWater, waterGoal);
        int waterPercent = (int) Math.min((t.totalWater / (float) waterGoal) * 100, 100);
        tvWaterPercent.setText(getString(R.string.home_water_percent_value, waterPercent));

        int mealCount = t.meals.size();
        tvMealCount.setText(getString(R.string.home_meal_count_value, mealCount));
        tvTotalCaloriesHome.setText(getString(R.string.home_total_calories_value, t.totalCalories));

        if (mealCount == 0)                              tvMotivationSub.setText(R.string.moto_no_meals);
        else if (t.totalCalories < calorieGoal * 0.5f)   tvMotivationSub.setText(R.string.moto_good_start);
        else if (t.totalCalories < calorieGoal)          tvMotivationSub.setText(R.string.moto_getting_close);
        else                                             tvMotivationSub.setText(R.string.moto_goal_reached);

        if (d.mealStreak >= 2) {
            tvStreak.setVisibility(View.VISIBLE);
            tvStreak.setText(getString(R.string.home_streak_format, d.mealStreak));
        } else {
            tvStreak.setVisibility(View.GONE);
        }

        Float weight = d.latestWeight != null ? d.latestWeight : (pref.getWeight() > 0 ? pref.getWeight() : null);
        if (weight == null) {
            tvHomeWeight.setText(R.string.weight_none);
            tvHomeWeightSub.setText(R.string.home_weight_sub);
        } else {
            tvHomeWeight.setText(getString(R.string.home_weight_format, weight));
            float target = pref.getTargetWeight();
            if (target > 0) {
                float diff = Math.abs(weight - target);
                tvHomeWeightSub.setText(diff < 0.1f ? getString(R.string.home_weight_target_reached)
                        : getString(R.string.home_weight_target_format, target, diff));
            } else {
                tvHomeWeightSub.setText(R.string.home_weight_sub);
            }
        }
    }

    private String macro(float value, int goal) {
        if (goal > 0) return getString(R.string.home_macro_with_goal, value, goal);
        return String.format(Locale.getDefault(), "%.1fg", value);
    }

    private void animateProgress(ProgressBar progressBar, int target, int max) {
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress",
                progressBar.getProgress(), Math.min(target, max));
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

    private void checkFcmToken() {
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || !isAdded()) return;
            String token = task.getResult();
            if (token != null && !token.equals(pref.getFcmToken())) {
                pref.setFcmToken(token);
                vm.uploadFcmToken(token);
            }
        });
    }

    private void showNotificationDialog(AdminNotification note) {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(note.getTitle())
                .setMessage(note.getMessage())
                .setPositiveButton(R.string.action_ok, (dialog, which) -> vm.markRead(note.getId()))
                .setCancelable(false)
                .show();
    }
}
