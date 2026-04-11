package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;

public class MealDetailFragment extends Fragment {

    private int logId = -1;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get data from arguments
        Bundle args = getArguments();
        if (args == null) return;

        logId               = args.getInt("log_id", -1);
        String foodName     = args.getString("food_name", "");
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
            tvConfidence.setTextColor(requireContext().getColor(R.color.green));
        } else if ("medium".equalsIgnoreCase(confidence)) {
            tvConfidence.setTextColor(requireContext().getColor(android.R.color.holo_orange_dark));
        } else {
            tvConfidence.setTextColor(requireContext().getColor(android.R.color.holo_red_dark));
        }

        view.findViewById(R.id.btnBack).setOnClickListener(v -> requireActivity().onBackPressed());
        view.findViewById(R.id.btnDelete).setOnClickListener(v -> deleteMeal());
    }

    private void deleteMeal() {
        if (logId == -1) return;

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<Void> response = api.deleteMealLog(apiKey, authHeader, "eq." + logId).execute();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            Toast.makeText(getContext(), "Öğün silindi.", Toast.LENGTH_SHORT).show();
                            requireActivity().onBackPressed();
                        } else {
                            Toast.makeText(getContext(), "Silme işlemi başarısız.", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e("MEAL_DELETE", "Hata: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded()) Toast.makeText(getContext(), "Bağlantı hatası.", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
