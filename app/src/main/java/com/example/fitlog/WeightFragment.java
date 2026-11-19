package com.example.fitlog;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitlog.domain.DateRanges;
import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.LineChartView;
import com.example.fitlog.ui.WeightViewModel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Kilo takibi: özet, grafik, geçmiş ve ekleme. */
public class WeightFragment extends BaseFragment {

    private static final String STATE_RANGE = "range_days";

    private WeightViewModel vm;
    private TextView tvCurrent, tvChange, tvTarget, tvChartEmpty;
    private TextView chip30, chip90, chipAll;
    private ProgressBar progressTarget, progressLoading;
    private LineChartView chart;
    private final WeightAdapter adapter = new WeightAdapter();
    private List<WeightLog> all = new ArrayList<>();
    private int rangeDays = 30; // 0 = tümü

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_weight, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(WeightViewModel.class);
        bindCommon(vm);
        if (savedInstanceState != null) rangeDays = savedInstanceState.getInt(STATE_RANGE, 30);

        tvCurrent = view.findViewById(R.id.tvCurrentWeight);
        tvChange = view.findViewById(R.id.tvWeightChange);
        tvTarget = view.findViewById(R.id.tvWeightTarget);
        tvChartEmpty = view.findViewById(R.id.tvChartEmpty);
        progressTarget = view.findViewById(R.id.progressTarget);
        progressLoading = view.findViewById(R.id.progressWeight);
        chart = view.findViewById(R.id.chartWeight);
        chip30 = view.findViewById(R.id.chip30);
        chip90 = view.findViewById(R.id.chip90);
        chipAll = view.findViewById(R.id.chipAll);

        RecyclerView rv = view.findViewById(R.id.recyclerWeights);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        view.findViewById(R.id.btnBack).setOnClickListener(v -> navigateUp());
        view.findViewById(R.id.btnAddWeight).setOnClickListener(v -> showAddDialog());
        chip30.setOnClickListener(v -> setRange(30));
        chip90.setOnClickListener(v -> setRange(90));
        chipAll.setOnClickListener(v -> setRange(0));
        updateChips();

        vm.entries().observe(getViewLifecycleOwner(), res -> {
            progressLoading.setVisibility(res.isLoading() && res.data == null ? View.VISIBLE : View.GONE);
            if (res.isError()) com.example.fitlog.ui.ErrorMessages.toast(requireContext(), res.error);
            if (res.data != null) {
                all = res.data;
                render();
            }
        });
        if (savedInstanceState == null || vm.entries().getValue() == null) vm.refresh();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_RANGE, rangeDays);
    }

    private void setRange(int days) {
        rangeDays = days;
        updateChips();
        render();
    }

    private void updateChips() {
        chip30.setSelected(rangeDays == 30);
        chip90.setSelected(rangeDays == 90);
        chipAll.setSelected(rangeDays == 0);
    }

    private void render() {
        if (!isAdded()) return;
        PrefManager pref = new PrefManager(requireContext());
        float target = pref.getTargetWeight();
        adapter.submit(all);

        if (all.isEmpty()) {
            tvCurrent.setText("—");
            tvChange.setVisibility(View.GONE);
            progressTarget.setVisibility(View.GONE);
            tvTarget.setText(target > 0 ? getString(R.string.weight_target_only, target) : getString(R.string.weight_no_target));
            chart.setVisibility(View.INVISIBLE);
            tvChartEmpty.setVisibility(View.VISIBLE);
            return;
        }

        WeightLog latest = all.get(0);
        tvCurrent.setText(String.format(Locale.getDefault(), "%.1f", latest.getWeight_kg()));

        // Grafik için aralığa düşen kayıtlar, eskiden yeniye
        Instant cutoff = rangeDays > 0 ? Instant.now().minus(rangeDays, ChronoUnit.DAYS) : null;
        List<WeightLog> inRange = new ArrayList<>();
        for (WeightLog w : all) {
            Instant t = DateRanges.toInstant(w.getLogged_at());
            if (cutoff == null || (t != null && t.isAfter(cutoff))) inRange.add(w);
        }
        Collections.reverse(inRange);

        if (inRange.size() >= 2) {
            float delta = inRange.get(inRange.size() - 1).getWeight_kg() - inRange.get(0).getWeight_kg();
            tvChange.setVisibility(View.VISIBLE);
            tvChange.setText(String.format(Locale.getDefault(), "%+.1f kg", delta));
        } else {
            tvChange.setVisibility(View.GONE);
        }

        if (target > 0) {
            float start = all.get(all.size() - 1).getWeight_kg();
            float remaining = latest.getWeight_kg() - target;
            tvTarget.setText(Math.abs(remaining) < 0.1f
                    ? getString(R.string.home_weight_target_reached)
                    : getString(R.string.home_weight_target_format, target, Math.abs(remaining)));
            float total = Math.abs(start - target);
            int pct = total < 0.1f ? 100 : (int) Math.max(0, Math.min(100, (1 - Math.abs(remaining) / total) * 100));
            progressTarget.setVisibility(View.VISIBLE);
            progressTarget.setProgress(pct);
        } else {
            tvTarget.setText(R.string.weight_no_target);
            progressTarget.setVisibility(View.GONE);
        }

        if (inRange.isEmpty()) {
            chart.setVisibility(View.INVISIBLE);
            tvChartEmpty.setVisibility(View.VISIBLE);
        } else {
            ZoneId zone = ZoneId.systemDefault();
            DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM", appLocale());
            float[] values = new float[inRange.size()];
            String[] labels = new String[inRange.size()];
            for (int i = 0; i < inRange.size(); i++) {
                values[i] = inRange.get(i).getWeight_kg();
                java.time.LocalDate d = DateRanges.toLocalDate(inRange.get(i).getLogged_at(), zone);
                labels[i] = d != null ? d.format(f) : "";
            }
            chart.setData(values, labels, target);
            chart.setVisibility(View.VISIBLE);
            tvChartEmpty.setVisibility(View.GONE);
        }
    }

    private Locale appLocale() {
        return getResources().getConfiguration().getLocales().get(0);
    }

    private void showAddDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("75.5");
        if (!all.isEmpty()) input.setText(String.format(Locale.ROOT, "%.1f", all.get(0).getWeight_kg()));
        input.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(requireContext());
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.weight_add)
                .setView(box)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    try {
                        float kg = Float.parseFloat(input.getText().toString().trim().replace(',', '.'));
                        if (kg < 20 || kg > 400) { toast(R.string.error_invalid_weight); return; }
                        vm.add(kg);
                    } catch (NumberFormatException e) {
                        toast(R.string.error_invalid_weight);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
        input.requestFocus();
    }

    private void confirmDelete(WeightLog w) {
        if (w.getId() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.weight_delete_confirm, w.getWeight_kg()))
                .setPositiveButton(R.string.action_confirm, (d, x) -> vm.delete(w.getId()))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private class WeightAdapter extends RecyclerView.Adapter<WeightAdapter.VH> {
        private final List<WeightLog> items = new ArrayList<>();

        void submit(List<WeightLog> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_weight, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            WeightLog w = items.get(position);
            h.value.setText(String.format(Locale.getDefault(), "%.1f kg", w.getWeight_kg()));
            java.time.LocalDate d = DateRanges.toLocalDate(w.getLogged_at(), ZoneId.systemDefault());
            h.date.setText(d != null ? d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", appLocale())) : "");
            if (position + 1 < items.size()) {
                float delta = w.getWeight_kg() - items.get(position + 1).getWeight_kg();
                h.delta.setText(String.format(Locale.getDefault(), "%+.1f", delta));
                h.delta.setTextColor(ContextCompat.getColor(requireContext(),
                        Math.abs(delta) < 0.05f ? R.color.text_secondary
                                : (delta < 0 ? R.color.brand_green : R.color.accent_orange)));
            } else {
                h.delta.setText("");
            }
            h.delete.setOnClickListener(v -> confirmDelete(w));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView value, date, delta, delete;
            VH(View v) {
                super(v);
                value = v.findViewById(R.id.tvWeightValue);
                date = v.findViewById(R.id.tvWeightDate);
                delta = v.findViewById(R.id.tvWeightDelta);
                delete = v.findViewById(R.id.btnDeleteWeight);
            }
        }
    }
}
