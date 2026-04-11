package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HistoryFragment extends Fragment {

    private RecyclerView recyclerHistory;
    private ProgressBar progressBarHistory;
    private HistoryAdapter adapter;
    private List<DayHistory> dayList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerHistory    = view.findViewById(R.id.recyclerHistory);
        progressBarHistory = view.findViewById(R.id.progressBarHistory);

        adapter = new HistoryAdapter(requireContext(), dayList);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistory.setAdapter(adapter);

        loadHistory();
    }

    private void loadHistory() {
        if (getContext() == null) return;
        progressBarHistory.setVisibility(View.VISIBLE);

        PrefManager pref    = new PrefManager(getContext());
        String userIdFilter = "eq." + pref.getId();

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -6);
        String weekFilter = "gte." + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(cal.getTime()) + "T00:00:00Z";

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<List<WaterLog>> waterResp = api.getTodayWaterLogs(
                        apiKey, authHeader, "application/json", userIdFilter, weekFilter
                ).execute();

                retrofit2.Response<List<MealLog>> mealResp = api.getTodayMealLogs(
                        apiKey, authHeader, "application/json", userIdFilter, weekFilter
                ).execute();

                List<DayHistory> days = buildDayList();

                Map<String, DayHistory> dayMap = new HashMap<>();
                for (DayHistory d : days) dayMap.put(d.date, d);

                if (waterResp.isSuccessful() && waterResp.body() != null) {
                    for (WaterLog w : waterResp.body()) {
                        String date = extractDate(w.getConsumed_at());
                        DayHistory day = dayMap.get(date);
                        if (day != null) day.totalWater += w.getAmount_ml();
                    }
                }

                if (mealResp.isSuccessful() && mealResp.body() != null) {
                    for (MealLog m : mealResp.body()) {
                        String date = extractDate(m.getEaten_at());
                        DayHistory day = dayMap.get(date);
                        if (day != null) {
                            day.totalCalories += m.getCalories();
                            day.totalProtein  += m.getProtein();
                            day.totalCarbs    += m.getCarbohydrates();
                            day.totalFat      += m.getFat();
                            day.meals.add(m);
                        }
                    }
                }

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        progressBarHistory.setVisibility(View.GONE);
                        dayList.clear();
                        dayList.addAll(days);
                        adapter.notifyDataSetChanged();
                    });
                }

            } catch (Exception e) {
                Log.e("HISTORY", "Hata: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        progressBarHistory.setVisibility(View.GONE);
                        Toast.makeText(getContext(), getString(R.string.error_data_load_failed), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private List<DayHistory> buildDayList() {
        List<DayHistory> days = new ArrayList<>();
        SimpleDateFormat dateFmt    = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat displayFmt = new SimpleDateFormat("d MMMM", new Locale("tr"));

        Calendar cal = Calendar.getInstance();

        for (int i = 0; i < 7; i++) {
            String date        = dateFmt.format(cal.getTime());
            String displayDate = displayFmt.format(cal.getTime());

            String dayLabel;
            if (i == 0)      dayLabel = getString(R.string.history_today);
            else if (i == 1) dayLabel = getString(R.string.history_yesterday);
            else             dayLabel = new SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.getTime());

            days.add(new DayHistory(date, dayLabel, displayDate));
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        return days;
    }

    private String extractDate(String timestamp) {
        if (timestamp == null || timestamp.length() < 10) return "";
        return timestamp.substring(0, 10);
    }
}
