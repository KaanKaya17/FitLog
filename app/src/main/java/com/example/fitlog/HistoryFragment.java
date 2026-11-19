package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitlog.domain.HistoryAggregator;
import com.example.fitlog.ui.BarChartView;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;
import com.example.fitlog.ui.HistoryViewModel;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends BaseFragment {

    private RecyclerView recyclerHistory;
    private ProgressBar progressBarHistory;
    private HistoryAdapter adapter;
    private final List<DayHistory> dayList = new ArrayList<>();
    private HistoryViewModel vm;

    private TextView chipWeek, chipMonth;
    private TextView tvAvgCalories, tvAvgWater, tvLoggedDays, tvStreakCurrent, tvStreakBest, tvWaterStreak;
    private BarChartView chartCalories, chartWater;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(HistoryViewModel.class);

        recyclerHistory    = view.findViewById(R.id.recyclerHistory);
        progressBarHistory = view.findViewById(R.id.progressBarHistory);
        chipWeek = view.findViewById(R.id.chipWeek);
        chipMonth = view.findViewById(R.id.chipMonth);
        tvAvgCalories = view.findViewById(R.id.tvAvgCalories);
        tvAvgWater = view.findViewById(R.id.tvAvgWater);
        tvLoggedDays = view.findViewById(R.id.tvLoggedDays);
        tvStreakCurrent = view.findViewById(R.id.tvStreakCurrent);
        tvStreakBest = view.findViewById(R.id.tvStreakBest);
        tvWaterStreak = view.findViewById(R.id.tvWaterStreak);
        chartCalories = view.findViewById(R.id.chartCalories);
        chartWater = view.findViewById(R.id.chartWater);
        chartWater.setOverGoalWarning(false);

        adapter = new HistoryAdapter(requireContext(), dayList);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerHistory.setAdapter(adapter);

        chipWeek.setOnClickListener(v -> vm.setPeriod(7));
        chipMonth.setOnClickListener(v -> vm.setPeriod(30));

        vm.periodDays().observe(getViewLifecycleOwner(), p -> {
            chipWeek.setSelected(p == 7);
            chipMonth.setSelected(p == 30);
            ((TextView) view.findViewById(R.id.tvHistorySubtitle))
                    .setText(getString(R.string.history_subtitle_format, p));
            render();
        });
        vm.history().observe(getViewLifecycleOwner(), res -> {
            progressBarHistory.setVisibility(res.isLoading() && res.data == null ? View.VISIBLE : View.GONE);
            if (res.isError()) ErrorMessages.toast(requireContext(), res.error);
            render();
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (new PrefManager(requireContext()).getId() != -1) {
            vm.refresh(new PrefManager(requireContext()).getWaterGoal());
        }
    }

    private void render() {
        if (!isAdded() || vm.history().getValue() == null || vm.history().getValue().data == null) return;
        HistoryViewModel.History h = vm.history().getValue().data;
        Integer pVal = vm.periodDays().getValue();
        int period = Math.min(pVal != null ? pVal : 7, h.days.size());
        List<DayHistory> visible = new ArrayList<>(h.days.subList(0, period));

        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateTimeFormatter displayFmt = DateTimeFormatter.ofPattern("d MMMM", locale);
        LocalDate today = LocalDate.now();
        for (DayHistory d : visible) {
            LocalDate date = LocalDate.parse(d.date);
            d.displayDate = date.format(displayFmt);
            if (date.equals(today)) d.dayLabel = getString(R.string.history_today);
            else if (date.equals(today.minusDays(1))) d.dayLabel = getString(R.string.history_yesterday);
            else d.dayLabel = date.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
        }

        // Özet
        HistoryAggregator.Averages avg = HistoryAggregator.averages(visible);
        tvAvgCalories.setText(avg.loggedDays == 0 ? "—" : String.valueOf(avg.calories));
        tvAvgWater.setText(avg.water == 0 ? "—" : getString(R.string.format_ml, String.valueOf(avg.water)));
        tvLoggedDays.setText(avg.loggedDays + " / " + period);
        tvStreakCurrent.setText(getString(R.string.format_days, h.currentStreak));
        tvStreakBest.setText(getString(R.string.format_days, h.bestStreak));
        tvWaterStreak.setText(getString(R.string.format_days, h.waterStreak));

        // Grafikler (soldan sağa eskiden yeniye)
        float[] cal = new float[period];
        float[] water = new float[period];
        String[] labels = new String[period];
        DateTimeFormatter shortFmt = DateTimeFormatter.ofPattern(period <= 7 ? "EEE" : "d", locale);
        for (int i = 0; i < period; i++) {
            DayHistory d = visible.get(period - 1 - i);
            cal[i] = d.totalCalories;
            water[i] = d.totalWater;
            labels[i] = LocalDate.parse(d.date).format(shortFmt);
        }
        PrefManager pref = new PrefManager(requireContext());
        chartCalories.setData(cal, labels, pref.getCalorieGoal());
        chartCalories.setHighlightIndex(period - 1);
        chartWater.setData(water, labels, pref.getWaterGoal());
        chartWater.setHighlightIndex(period - 1);

        dayList.clear();
        dayList.addAll(visible);
        adapter.notifyDataSetChanged();
    }
}
