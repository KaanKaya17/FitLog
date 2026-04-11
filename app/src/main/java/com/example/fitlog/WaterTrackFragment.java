package com.example.fitlog;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.analytics.FirebaseAnalytics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

public class WaterTrackFragment extends Fragment {

    private FirebaseAnalytics firebaseAnalytics;
    private int dailyGoal;
    private ProgressBar progressWater;
    private TextView tvCurrentAmount;
    private TextView tvPercent;
    private EditText editTextCustomAmount;
    private int currentAmount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_water_track, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());

        progressWater       = view.findViewById(R.id.progressWater);
        tvCurrentAmount     = view.findViewById(R.id.tvCurrentAmount);
        tvPercent           = view.findViewById(R.id.tvPercent);
        editTextCustomAmount = view.findViewById(R.id.editTextCustomAmount);

        PrefManager pref = new PrefManager(getContext());
        dailyGoal = pref.getWaterGoal();
        progressWater.setMax(dailyGoal);

        // Button click listeners using existing XML IDs
        view.findViewById(R.id.btn200ml).setOnClickListener(v -> {
            PrefManager p = new PrefManager(requireContext());
            if (p.getId() != -1) addWater(200);
        });
        view.findViewById(R.id.btn300ml).setOnClickListener(v -> {
            PrefManager p = new PrefManager(requireContext());
            if (p.getId() != -1) addWater(300);
        });
        view.findViewById(R.id.btn500ml).setOnClickListener(v -> {
            PrefManager p = new PrefManager(requireContext());
            if (p.getId() != -1) addWater(500);
        });
        view.findViewById(R.id.btnAddCustom).setOnClickListener(v -> btnAddCustom());
        view.findViewById(R.id.btnReset).setOnClickListener(v -> {
            currentAmount = 0;
            updateUI(0);
            Toast.makeText(getContext(), getString(R.string.water_msg_reset), Toast.LENGTH_SHORT).show();
        });

        loadTodayWater();
    }

    private void btnAddCustom() {
        if (getContext() == null) return;
        String input = editTextCustomAmount.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.water_error_empty_amount), Toast.LENGTH_SHORT).show();
            return;
        }
        int amount;
        try { amount = Integer.parseInt(input); } catch (NumberFormatException e) {
            Toast.makeText(getContext(), getString(R.string.water_error_invalid_number), Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(getContext(), getString(R.string.water_error_negative_amount), Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount > 3000) {
            Toast.makeText(getContext(), getString(R.string.water_error_too_much_once), Toast.LENGTH_SHORT).show();
            return;
        }
        editTextCustomAmount.setText("");
        PrefManager pref = new PrefManager(getContext());
        if (pref.getId() != -1) addWater(amount);
    }

    private void addWater(int amountMl) {
        if (getContext() == null) return;
        if (currentAmount + amountMl > 10000) {
            Toast.makeText(getContext(), getString(R.string.water_error_daily_limit), Toast.LENGTH_SHORT).show();
            return;
        }
        currentAmount += amountMl;
        updateUI(currentAmount);
        saveWaterLog(amountMl);

        Bundle waterBundle = new Bundle();
        waterBundle.putInt("amount_ml", amountMl);
        firebaseAnalytics.logEvent("water_logged", waterBundle);
    }

    private void updateUI(int totalMl) {
        if (getContext() == null) return;
        ObjectAnimator animator = ObjectAnimator.ofInt(progressWater, "progress", progressWater.getProgress(), totalMl);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        tvCurrentAmount.setText(String.valueOf(totalMl));
        int percent = Math.min((int) ((totalMl / (float) dailyGoal) * 100), 100);

        if (totalMl >= dailyGoal) {
            tvPercent.setText(getString(R.string.water_msg_goal_reached));
            tvPercent.setTextColor(requireContext().getColor(android.R.color.holo_blue_dark));
        } else {
            tvPercent.setText(getString(R.string.home_water_percent_value, percent));
            tvPercent.setTextColor(requireContext().getColor(R.color.green));
        }
    }

    private void loadTodayWater() {
        if (getContext() == null) return;
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                PrefManager pref  = new PrefManager(requireContext());
                String userIdFilter = "eq." + pref.getId();
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(new java.util.Date());
                String dateFilter = "gte." + today + "T00:00:00Z";

                retrofit2.Response<List<WaterLog>> response = api.getTodayWaterLogs(apiKey, authHeader, "application/json", userIdFilter, dateFilter).execute();

                if (response.isSuccessful() && response.body() != null) {
                    int total = 0;
                    for (WaterLog log : response.body()) total += log.getAmount_ml();
                    final int finalTotal = total;
                    currentAmount = finalTotal;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> { if (isAdded()) updateUI(finalTotal); });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null)
                            Toast.makeText(getContext(), getString(R.string.error_data_load_with_msg, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void saveWaterLog(int amountMl) {
        if (getContext() == null) return;
        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;
                PrefManager pref = new PrefManager(requireContext());
                WaterLog log = new WaterLog(pref.getId(), amountMl);

                retrofit2.Response<Void> response = api.insertWaterLog(apiKey, authHeader, "application/json", "return=minimal", log).execute();

                if (!response.isSuccessful()) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (isAdded() && getContext() != null)
                                Toast.makeText(getContext(), getString(R.string.error_record_failed), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null)
                            Toast.makeText(getContext(), getString(R.string.error_connection_with_msg, e.getMessage()), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
