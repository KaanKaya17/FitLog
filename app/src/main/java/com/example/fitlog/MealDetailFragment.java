package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public class MealDetailFragment extends com.example.fitlog.ui.BaseFragment {

    private int logId = -1;
    private String foodName = "";
    private com.example.fitlog.ui.MealListViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        vm = new androidx.lifecycle.ViewModelProvider(this).get(com.example.fitlog.ui.MealListViewModel.class);
        bindCommon(vm);
        vm.deleted().observe(getViewLifecycleOwner(), e -> { if (e.consume() != null) navigateUp(); });

        Bundle args = getArguments();
        if (args == null) return;

        logId               = args.getInt("log_id", -1);
        foodName            = args.getString("food_name", "");
        String serving      = args.getString("serving_size", "");
        int calories        = args.getInt("calories", 0);
        float protein       = args.getFloat("protein", 0);
        float carbs         = args.getFloat("carbs", 0);
        float fat           = args.getFloat("fat", 0);
        float fiber         = args.getFloat("fiber", 0);
        String confidence   = args.getString("confidence", "");

        ((TextView) view.findViewById(R.id.tvFoodName)).setText(foodName);
        ((TextView) view.findViewById(R.id.tvServingSize)).setText(serving);
        ((TextView) view.findViewById(R.id.tvCalories)).setText(String.valueOf(calories));
        ((TextView) view.findViewById(R.id.tvProtein)).setText(String.format(Locale.getDefault(), "%.1f g", protein));
        ((TextView) view.findViewById(R.id.tvCarbs)).setText(String.format(Locale.getDefault(), "%.1f g", carbs));
        ((TextView) view.findViewById(R.id.tvFat)).setText(String.format(Locale.getDefault(), "%.1f g", fat));
        ((TextView) view.findViewById(R.id.tvFiber)).setText(String.format(Locale.getDefault(), "%.1f g", fiber));

        TextView tvConfidence = view.findViewById(R.id.tvConfidence);
        tvConfidence.setText(confidence);
        if ("high".equalsIgnoreCase(confidence)) {
            tvConfidence.setTextColor(requireContext().getColor(R.color.brand_green));
        } else if ("medium".equalsIgnoreCase(confidence)) {
            tvConfidence.setTextColor(requireContext().getColor(R.color.accent_orange));
        } else {
            tvConfidence.setTextColor(requireContext().getColor(R.color.error));
        }

        view.findViewById(R.id.btnBack).setOnClickListener(v -> androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp());
        view.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteMeal());
    }

    private void deleteMeal() {
        if (logId == -1) return;
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_confirm_format, foodName))
                .setPositiveButton(R.string.action_confirm, (d, w) -> vm.delete(logId))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
