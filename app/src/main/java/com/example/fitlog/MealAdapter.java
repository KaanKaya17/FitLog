package com.example.fitlog;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitlog.domain.MealType;

import java.util.ArrayList;
import java.util.List;

/** Öğün türü başlıklarıyla gruplanmış öğün listesi. */
public class MealAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_MEAL = 1;

    public interface OnMealClickListener {
        void onMealClick(MealLog meal);
    }

    /** Başlık satırı: öğün türü + o türün toplam kalorisi. */
    static final class Header {
        final MealType type;
        final int calories;
        Header(MealType type, int calories) { this.type = type; this.calories = calories; }
    }

    private final List<Object> rows = new ArrayList<>();
    private final OnMealClickListener listener;

    public MealAdapter(OnMealClickListener listener) {
        this.listener = listener;
    }

    /** Öğünleri kahvaltı → öğle → akşam → ara öğün → türü belirtilmemiş sırasıyla gruplar. */
    public void submit(List<MealLog> meals) {
        rows.clear();
        rows.addAll(group(meals));
        notifyDataSetChanged();
    }

    static List<Object> group(List<MealLog> meals) {
        List<Object> out = new ArrayList<>();
        MealType[] order = MealType.values();
        for (MealType t : order) addGroup(out, meals, t);
        addGroup(out, meals, null);
        return out;
    }

    private static void addGroup(List<Object> out, List<MealLog> meals, MealType type) {
        List<MealLog> g = new ArrayList<>();
        int cal = 0;
        for (MealLog m : meals) {
            if (MealType.fromKey(m.getMeal_type()) == type) { g.add(m); cal += m.getCalories(); }
        }
        if (g.isEmpty()) return;
        out.add(new Header(type, cal));
        out.addAll(g);
    }

    public MealLog mealAt(int position) {
        Object o = position >= 0 && position < rows.size() ? rows.get(position) : null;
        return o instanceof MealLog ? (MealLog) o : null;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof Header ? TYPE_HEADER : TYPE_MEAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            Context ctx = parent.getContext();
            TextView tv = new TextView(ctx);
            float d = ctx.getResources().getDisplayMetrics().density;
            tv.setPadding(0, (int) (12 * d), 0, (int) (6 * d));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            tv.setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary));
            tv.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerView.ViewHolder(tv) {};
        }
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_meal, parent, false);
        return new MealViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context ctx = holder.itemView.getContext();
        Object row = rows.get(position);
        if (row instanceof Header) {
            Header h = (Header) row;
            ((TextView) holder.itemView).setText(ctx.getString(R.string.meal_group_header,
                    ctx.getString(labelOf(h.type)).toUpperCase(ctx.getResources().getConfiguration().getLocales().get(0)),
                    h.calories));
            return;
        }
        MealLog meal = (MealLog) row;
        MealViewHolder vh = (MealViewHolder) holder;
        vh.tvMealName.setText(meal.getFood_name());
        vh.tvServingSize.setText(meal.getServing_size());
        vh.tvCalories.setText(String.valueOf(meal.getCalories()));
        vh.tvProtein.setText(ctx.getString(R.string.food_format_protein_float, meal.getProtein()));
        vh.tvCarbs.setText(ctx.getString(R.string.food_format_carbs_float, meal.getCarbohydrates()));
        vh.tvFat.setText(ctx.getString(R.string.food_format_fat_float, meal.getFat()));
        vh.itemView.setOnClickListener(v -> listener.onMealClick(meal));
    }

    static int labelOf(MealType t) {
        if (t == null) return R.string.meal_type_other;
        switch (t) {
            case BREAKFAST: return R.string.meal_type_breakfast;
            case LUNCH:     return R.string.meal_type_lunch;
            case DINNER:    return R.string.meal_type_dinner;
            default:        return R.string.meal_type_snack;
        }
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class MealViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMealName, tvServingSize, tvCalories, tvProtein, tvCarbs, tvFat;

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
