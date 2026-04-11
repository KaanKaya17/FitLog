package com.example.fitlog;


import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealListFragment extends Fragment {

    private RecyclerView recyclerMeals;
    private LinearLayout layoutEmpty;
    private TextView tvSubtitle, tvTotalCalories, tvTotalProtein, tvTotalCarbs, tvTotalFat;
    private MealAdapter adapter;
    private List<MealLog> mealList = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupRecycler();

        view.findViewById(R.id.fabAddMeal).setOnClickListener(v ->
                androidx.navigation.Navigation.findNavController(v).navigate(R.id.addMealFragment)
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        loadMeals();
    }

    private void initViews(View view) {
        recyclerMeals   = view.findViewById(R.id.recyclerMeals);
        layoutEmpty     = view.findViewById(R.id.layoutEmpty);
        tvSubtitle      = view.findViewById(R.id.tvSubtitle);
        tvTotalCalories = view.findViewById(R.id.tvTotalCalories);
        tvTotalProtein  = view.findViewById(R.id.tvTotalProtein);
        tvTotalCarbs    = view.findViewById(R.id.tvTotalCarbs);
        tvTotalFat      = view.findViewById(R.id.tvTotalFat);
    }

    private void setupRecycler() {
        adapter = new MealAdapter(mealList, meal -> {
            Bundle args = new Bundle();
            args.putInt("log_id",       meal.getLog_id());
            args.putString("food_name",    meal.getFood_name());
            args.putString("serving_size", meal.getServing_size());
            args.putInt("calories",     meal.getCalories());
            args.putFloat("protein",      meal.getProtein());
            args.putFloat("carbs",        meal.getCarbohydrates());
            args.putFloat("fat",          meal.getFat());
            args.putFloat("fiber",        meal.getFiber());
            args.putString("confidence",   meal.getConfidence());
            androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.mealDetailFragment, args);
        });

        recyclerMeals.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerMeals.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                MealLog meal = mealList.get(position);

                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle(getString(R.string.dialog_delete_title))
                        .setMessage(getString(R.string.dialog_delete_confirm_format, meal.getFood_name()))
                        .setPositiveButton(getString(R.string.action_confirm), (dialog, which) -> deleteMeal(meal, position))
                        .setNegativeButton(getString(R.string.action_cancel), (dialog, which) -> adapter.notifyItemChanged(position))
                        .setCancelable(false)
                        .show();
            }
        }).attachToRecyclerView(recyclerMeals);
    }

    private void loadMeals() {
        if (getContext() == null) return;
        PrefManager pref    = new PrefManager(getContext());
        String userIdFilter = "eq." + pref.getId();
        String today        = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String dateFilter   = "gte." + today + "T00:00:00Z";

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<List<MealLog>> response = api.getTodayMealLogs(apiKey, authHeader, "application/json", userIdFilter, dateFilter).execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<MealLog> meals = response.body();
                    int totalCal = 0; float totalPro = 0, totalCarb = 0, totalFat = 0;
                    for (MealLog m : meals) {
                        totalCal += m.getCalories(); totalPro += m.getProtein();
                        totalCarb += m.getCarbohydrates(); totalFat += m.getFat();
                    }
                    final int fCal = totalCal; final float fPro = totalPro, fCarb = totalCarb, fFat = totalFat;

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (!isAdded()) return;
                            mealList.clear(); mealList.addAll(meals); adapter.notifyDataSetChanged();
                            layoutEmpty.setVisibility(meals.isEmpty() ? View.VISIBLE : View.GONE);
                            recyclerMeals.setVisibility(meals.isEmpty() ? View.GONE : View.VISIBLE);
                            tvSubtitle.setText(getString(R.string.meal_list_subtitle_format, meals.size()));
                            tvTotalCalories.setText(String.valueOf(fCal));
                            tvTotalProtein.setText(getString(R.string.format_gram_float, fPro));
                            tvTotalCarbs.setText(getString(R.string.format_gram_float, fCarb));
                            tvTotalFat.setText(getString(R.string.format_gram_float, fFat));
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("MEAL_LIST", "Hata: " + e.getMessage());
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (isAdded() && getContext() != null)
                            Toast.makeText(getContext(), getString(R.string.error_data_load_failed), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }

    private void deleteMeal(MealLog meal, int position) {
        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<Void> response = api.deleteMealLog(apiKey, authHeader, "eq." + meal.getLog_id()).execute();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            mealList.remove(position); adapter.notifyItemRemoved(position);
                            Toast.makeText(getContext(), getString(R.string.msg_meal_deleted), Toast.LENGTH_SHORT).show();
                            loadMeals();
                        } else {
                            adapter.notifyItemChanged(position);
                            Toast.makeText(getContext(), getString(R.string.error_delete_failed_format, response.code()), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (!isAdded()) return;
                        adapter.notifyItemChanged(position);
                        Toast.makeText(getContext(), getString(R.string.error_connection), Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }).start();
    }
}
