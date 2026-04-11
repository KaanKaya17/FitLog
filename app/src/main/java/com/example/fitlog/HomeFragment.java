package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private TextView tvUsername, tvGreeting, tvDate;
    private TextView tvCurrentCalories, tvProteinSummary, tvCarbsSummary, tvFatSummary;
    private ProgressBar progressCalorie, progressWaterHome;
    private TextView tvWaterAmount, tvWaterPercent;
    private TextView tvMealCount, tvTotalCaloriesHome;
    private TextView tvMotivationSub;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupGreeting();
        setupCardClicks(view);
        
        checkAdminNotifications();
        checkFcmToken();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            UserStatusChecker.check(requireActivity());
        }
        loadDashboardData();
    }

    private void initViews(View view) {
        tvUsername          = view.findViewById(R.id.tvUsername);
        tvGreeting          = view.findViewById(R.id.tvGreeting);
        tvDate              = view.findViewById(R.id.tvDate);
        tvCurrentCalories   = view.findViewById(R.id.tvCurrentCalories);
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
    }

    private void setupGreeting() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());
        String firstName = pref.getFirstName();
        tvUsername.setText(firstName == null || firstName.isEmpty() ? String.valueOf(R.string.home_default_user) : firstName);

        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        if (hour < 12)      tvGreeting.setText(R.string.home_greeting);
        else if (hour < 18) tvGreeting.setText(R.string.home_greeting_nice_day);
        else                tvGreeting.setText(R.string.home_greeting_night);

        String date = new SimpleDateFormat("d MMMM", new Locale("tr")).format(new Date());
        tvDate.setText(date);
    }

    private void setupCardClicks(View view) {
        view.findViewById(R.id.cardWater).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.waterTrackFragment));
        view.findViewById(R.id.cardMeal).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.addMealFragment));
        view.findViewById(R.id.btnNavAddMeal).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.addMealFragment));
        view.findViewById(R.id.btnNavAddWater).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.waterTrackFragment));
        view.findViewById(R.id.cardAiCoach).setOnClickListener(v ->
                Navigation.findNavController(v).navigate(R.id.aiCoachFragment));
    }

    private void loadDashboardData() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());
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

                retrofit2.Response<List<WaterLog>> waterResp = api.getTodayWaterLogs(
                        apiKey, authHeader, "application/json", userIdFilter, dateFilter
                ).execute();

                retrofit2.Response<List<MealLog>> mealResp = api.getTodayMealLogs(
                        apiKey, authHeader, "application/json", userIdFilter, dateFilter
                ).execute();

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

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        updateDashboard(fWater, fCalories, fProtein, fCarbs, fFat, fMealCount);
                    });
                }

            } catch (Exception e) {
                Log.e("DASHBOARD", "Veri yüklenemedi: " + e.getMessage());
            }
        }).start();
    }

    private void updateDashboard(int water, int calories, float protein, float carbs, float fat, int mealCount) {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());
        int calorieGoal  = pref.getCalorieGoal();
        int waterGoal    = pref.getWaterGoal();

        progressCalorie.setMax(calorieGoal);
        progressWaterHome.setMax(waterGoal);

        tvCurrentCalories.setText(String.valueOf(calories));
        if (getView() != null) {
            ((TextView) getView().findViewById(R.id.tvCalorieSummary)).setText(getString(R.string.home_calorie_goal_format, calorieGoal));
        }
        animateProgress(progressCalorie, calories, calorieGoal);

        tvProteinSummary.setText(String.format(Locale.getDefault(), "%.1fg", protein));
        tvCarbsSummary.setText(String.format(Locale.getDefault(), "%.1fg", carbs));
        tvFatSummary.setText(String.format(Locale.getDefault(), "%.1fg", fat));

        tvWaterAmount.setText(getString(R.string.home_water_progress_format, water, waterGoal));
        animateProgress(progressWaterHome, water, waterGoal);
        int waterPercent = (int) Math.min((water / (float) waterGoal) * 100, 100);
        tvWaterPercent.setText(getString(R.string.home_water_percent_value, waterPercent));

        tvMealCount.setText(getString(R.string.home_meal_count_value, mealCount));
        tvTotalCaloriesHome.setText(getString(R.string.home_total_calories_value, calories));

        if (mealCount == 0) {
            tvMotivationSub.setText(R.string.moto_no_meals);
        } else if (calories < calorieGoal * 0.5f) {
            tvMotivationSub.setText(R.string.moto_good_start);
        } else if (calories < calorieGoal) {
            tvMotivationSub.setText(R.string.moto_getting_close);
        } else {
            tvMotivationSub.setText(R.string.moto_goal_reached);
        }
    }

    private void animateProgress(ProgressBar progressBar, int target, int max) {
        int safeTarget = Math.min(target, max);
        ObjectAnimator animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.getProgress(), safeTarget);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();
    }

    private void checkFcmToken() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());
        int userId = pref.getId();
        if (userId == -1) return;

        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.w("FCM", "Fetching FCM registration token failed", task.getException());
                    return;
                }
                String token = task.getResult();
                String savedToken = pref.getFcmToken();
                if (token != null && !token.equals(savedToken)) {
                    pref.setFcmToken(token);
                    updateTokenInSupabase(userId, token);
                }
            });
    }

    private void updateTokenInSupabase(int userId, String token) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                Map<String, Object> update = new HashMap<>();
                update.put("fcm_token", token);

                api.updateUserProfile(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        "eq." + userId,
                        update
                ).execute();
            } catch (Exception e) {
                Log.e("FCM", "Token güncelleme hatası: " + e.getMessage());
            }
        }).start();
    }

    private void checkAdminNotifications() {
        if (getContext() == null) return;
        PrefManager pref = new PrefManager(getContext());
        int userId = pref.getId();
        if (userId == -1) return;

        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                retrofit2.Response<List<AdminNotification>> response = api.getNotifications(
                        "eq." + userId,
                        "eq.false",
                        BuildConfig.SUPABASE_API_KEY,
                        "Bearer " + BuildConfig.SUPABASE_API_KEY
                ).execute();

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    AdminNotification note = response.body().get(0);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> showNotificationDialog(note));
                    }
                }
            } catch (Exception e) {
                Log.e("NOTIF", "Hata: " + e.getMessage());
            }
        }).start();
    }

    private void showNotificationDialog(AdminNotification note) {
        if (getContext() == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(getContext())
                .setTitle(note.getTitle())
                .setMessage(note.getMessage())
                .setPositiveButton(getString(R.string.action_ok), (dialog, which) -> {
                    markAsRead(note.getId());
                })
                .setCancelable(false)
                .show();
    }

    private void markAsRead(String noteId) {
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                Map<String, Object> update = new HashMap<>();
                update.put("is_read", true);

                api.markNotificationAsRead(
                        "eq." + noteId,
                        BuildConfig.SUPABASE_API_KEY,
                        "Bearer " + BuildConfig.SUPABASE_API_KEY,
                        "application/json",
                        update
                ).execute();
            } catch (Exception e) {}
        }).start();
    }
}
