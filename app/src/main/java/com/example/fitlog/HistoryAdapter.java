package com.example.fitlog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private final List<DayHistory> days;
    private final Context context;

    public HistoryAdapter(Context context, List<DayHistory> days) {
        this.context = context;
        this.days    = days;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_day, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        DayHistory day = days.get(position);

        holder.tvDayLabel.setText(day.dayLabel);
        holder.tvDate.setText(day.displayDate);
        holder.tvDayCalories.setText(String.valueOf(day.totalCalories));
        holder.tvDayWater.setText(day.totalWater + " ml");
        holder.tvDayProtein.setText(String.format(Locale.getDefault(), "%.1fg", day.totalProtein));
        holder.tvDayCarbs.setText(String.format(Locale.getDefault(), "%.1fg", day.totalCarbs));
        holder.tvDayFat.setText(String.format(Locale.getDefault(), "%.1fg", day.totalFat));

        // Öğün listesini doldur
        holder.layoutMeals.removeAllViews();
        if (day.meals.isEmpty()) {
            TextView emptyText = new TextView(context);
            emptyText.setText("Bu gün öğün eklenmedi.");
            emptyText.setTextSize(13f);
            emptyText.setTextColor(0xFF9E9E9E);
            holder.layoutMeals.addView(emptyText);
        } else {
            for (MealLog meal : day.meals) {
                View mealRow = LayoutInflater.from(context)
                        .inflate(R.layout.item_history_meal_row, holder.layoutMeals, false);

                ((TextView) mealRow.findViewById(R.id.tvMealRowName)).setText(meal.getFood_name());
                ((TextView) mealRow.findViewById(R.id.tvMealRowCalories)).setText(meal.getCalories() + " kcal");

                holder.layoutMeals.addView(mealRow);
            }
        }

        // Expand / Collapse toggle
        boolean isExpanded = holder.layoutDetail.getVisibility() == View.VISIBLE;
        holder.layoutHeader.setOnClickListener(v -> {
            if (holder.layoutDetail.getVisibility() == View.VISIBLE) {
                holder.layoutDetail.setVisibility(View.GONE);
                holder.tvExpandArrow.setText("›");
            } else {
                holder.layoutDetail.setVisibility(View.VISIBLE);
                holder.tvExpandArrow.setText("⌄");
            }
        });
    }

    @Override
    public int getItemCount() { return days.size(); }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvDayLabel, tvDate, tvDayCalories;
        TextView tvDayWater, tvDayProtein, tvDayCarbs, tvDayFat;
        TextView tvExpandArrow;
        LinearLayout layoutHeader, layoutDetail, layoutMeals;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDayLabel     = itemView.findViewById(R.id.tvDayLabel);
            tvDate         = itemView.findViewById(R.id.tvDate);
            tvDayCalories  = itemView.findViewById(R.id.tvDayCalories);
            tvDayWater     = itemView.findViewById(R.id.tvDayWater);
            tvDayProtein   = itemView.findViewById(R.id.tvDayProtein);
            tvDayCarbs     = itemView.findViewById(R.id.tvDayCarbs);
            tvDayFat       = itemView.findViewById(R.id.tvDayFat);
            tvExpandArrow  = itemView.findViewById(R.id.tvExpandArrow);
            layoutHeader   = itemView.findViewById(R.id.layoutHeader);
            layoutDetail   = itemView.findViewById(R.id.layoutDetail);
            layoutMeals    = itemView.findViewById(R.id.layoutMeals);
        }
    }
}