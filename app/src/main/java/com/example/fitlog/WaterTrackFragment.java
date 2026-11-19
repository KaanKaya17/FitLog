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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.WaterViewModel;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

public class WaterTrackFragment extends BaseFragment {

    private FirebaseAnalytics analytics;
    private WaterViewModel vm;
    private int dailyGoal;
    private ProgressBar progressWater;
    private TextView tvCurrentAmount, tvPercent;
    private EditText editTextCustomAmount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_water_track, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(WaterViewModel.class);
        bindCommon(vm);
        analytics = FirebaseAnalytics.getInstance(requireContext());

        progressWater        = view.findViewById(R.id.progressWater);
        tvCurrentAmount      = view.findViewById(R.id.tvCurrentAmount);
        tvPercent            = view.findViewById(R.id.tvPercent);
        editTextCustomAmount = view.findViewById(R.id.editTextCustomAmount);

        dailyGoal = Math.max(1, new PrefManager(requireContext()).getWaterGoal());
        progressWater.setMax(dailyGoal);

        view.findViewById(R.id.btn200ml).setOnClickListener(v -> addWater(200));
        view.findViewById(R.id.btn300ml).setOnClickListener(v -> addWater(300));
        view.findViewById(R.id.btn500ml).setOnClickListener(v -> addWater(500));
        view.findViewById(R.id.btnAddCustom).setOnClickListener(v -> addCustom());
        view.findViewById(R.id.btnReset).setOnClickListener(v -> confirmReset());

        vm.total().observe(getViewLifecycleOwner(), this::updateUI);
        vm.undoable().observe(getViewLifecycleOwner(), e -> {
            WaterLog log = e.consume();
            if (log == null || getView() == null) return;
            Snackbar.make(getView(), getString(R.string.water_added_format, log.getAmount_ml()), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo, v -> vm.undo(log))
                    .show();
        });

        if (loggedIn()) vm.refresh();
    }

    private boolean loggedIn() {
        if (new PrefManager(requireContext()).getId() != -1) return true;
        toast(R.string.error_not_logged_in);
        return false;
    }

    private void addCustom() {
        String input = editTextCustomAmount.getText().toString().trim();
        if (input.isEmpty()) { toast(R.string.water_error_empty_amount); return; }
        int amount;
        try { amount = Integer.parseInt(input); } catch (NumberFormatException e) {
            toast(R.string.water_error_invalid_number); return;
        }
        if (amount <= 0) { toast(R.string.water_error_negative_amount); return; }
        if (amount > 3000) { toast(R.string.water_error_too_much_once); return; }
        editTextCustomAmount.setText("");
        addWater(amount);
    }

    private void addWater(int amountMl) {
        if (!loggedIn()) return;
        if (!vm.add(amountMl)) { toast(R.string.water_error_daily_limit); return; }
        Bundle b = new Bundle();
        b.putInt("amount_ml", amountMl);
        analytics.logEvent("water_logged", b);
    }

    private void confirmReset() {
        if (!loggedIn()) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.water_reset_title)
                .setMessage(R.string.water_reset_message)
                .setPositiveButton(R.string.action_confirm, (d, w) -> vm.resetToday())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void updateUI(int totalMl) {
        ObjectAnimator animator = ObjectAnimator.ofInt(progressWater, "progress",
                progressWater.getProgress(), Math.min(totalMl, dailyGoal));
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.start();

        tvCurrentAmount.setText(String.valueOf(totalMl));
        if (totalMl >= dailyGoal) {
            tvPercent.setText(R.string.water_msg_goal_reached);
            tvPercent.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_blue));
        } else {
            int percent = Math.min((int) ((totalMl / (float) dailyGoal) * 100), 100);
            tvPercent.setText(getString(R.string.home_water_percent_value, percent));
            tvPercent.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_green));
        }
    }
}
