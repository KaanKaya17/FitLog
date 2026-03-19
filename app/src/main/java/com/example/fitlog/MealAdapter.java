package com.example.fitlog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class MealAdapter extends RecyclerView.Adapter<MealAdapter.MealViewHolder> {
    private final List<MealLog> meals;
    private final OnMealClickListener listener;

    public interface OnMealClickListener {
        void onMealClick(MealLog meal);
    }

    public MealAdapter(List<MealLog> meals, OnMealClickListener listener) {
        this.meals    = meals;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MealViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_meal, parent, false);
        return new MealViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MealViewHolder holder, int position) {
        MealLog meal = meals.get(position);

        holder.tvMealName.setText(meal.getFood_name());
        holder.tvServingSize.setText(meal.getServing_size());
        holder.tvCalories.setText(String.valueOf(meal.getCalories()));
        android.content.Context ctx = holder.itemView.getContext();
        holder.tvProtein.setText(ctx.getString(R.string.food_format_protein_float, meal.getProtein()));
        holder.tvCarbs.setText(ctx.getString(R.string.food_format_carbs_float, meal.getCarbohydrates()));
        holder.tvFat.setText(ctx.getString(R.string.food_format_fat_float, meal.getFat()));

        holder.itemView.setOnClickListener(v -> listener.onMealClick(meal));
    }

    @Override
    public int getItemCount() { return meals.size(); }

    static class MealViewHolder extends RecyclerView.ViewHolder {
        TextView tvMealName, tvServingSize, tvCalories, tvProtein, tvCarbs, tvFat;

        MealViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMealName    = itemView.findViewById(R.id.tvMealName);
            tvServingSize = itemView.findViewById(R.id.tvServingSize);
            tvCalories    = itemView.findViewById(R.id.tvCalories);
            tvProtein     = itemView.findViewById(R.id.tvProtein);
            tvCarbs       = itemView.findViewById(R.id.tvCarbs);
            tvFat         = itemView.findViewById(R.id.tvFat);
        }
    }
}
