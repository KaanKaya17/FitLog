package com.example.fitlog;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitlog.ui.BaseFragment;
import com.example.fitlog.ui.ErrorMessages;
import com.example.fitlog.ui.MealListViewModel;

import java.util.List;

public class MealListFragment extends BaseFragment {

    private RecyclerView recyclerMeals;
    private LinearLayout layoutEmpty;
    private TextView tvSubtitle, tvTotalCalories, tvTotalProtein, tvTotalCarbs, tvTotalFat;
    private MealAdapter adapter;
    private MealListViewModel vm;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_meal_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm = new ViewModelProvider(this).get(MealListViewModel.class);
        bindCommon(vm);

        recyclerMeals   = view.findViewById(R.id.recyclerMeals);
        layoutEmpty     = view.findViewById(R.id.layoutEmpty);
        tvSubtitle      = view.findViewById(R.id.tvSubtitle);
        tvTotalCalories = view.findViewById(R.id.tvTotalCalories);
        tvTotalProtein  = view.findViewById(R.id.tvTotalProtein);
        tvTotalCarbs    = view.findViewById(R.id.tvTotalCarbs);
        tvTotalFat      = view.findViewById(R.id.tvTotalFat);

        setupRecycler();
        view.findViewById(R.id.fabAddMeal).setOnClickListener(v -> navigate(R.id.addMealFragment));

        vm.meals().observe(getViewLifecycleOwner(), res -> {
            if (res.isError()) ErrorMessages.toast(requireContext(), res.error);
            if (res.data != null) render(res.data);
        });
        vm.deleted().observe(getViewLifecycleOwner(), e -> { if (e.consume() != null) vm.refresh(); });
        // Silme başarısız olursa satırı geri getir.
        vm.errors().observe(getViewLifecycleOwner(), e -> adapter.notifyDataSetChanged());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (new PrefManager(requireContext()).getId() != -1) vm.refresh();
        else render(java.util.Collections.emptyList());
    }

    private void setupRecycler() {
        adapter = new MealAdapter(meal -> {
            Bundle args = new Bundle();
            args.putInt("log_id", meal.getLog_id());
            args.putString("food_name", meal.getFood_name());
            args.putString("serving_size", meal.getServing_size());
            args.putInt("calories", meal.getCalories());
            args.putFloat("protein", meal.getProtein());
            args.putFloat("carbs", meal.getCarbohydrates());
            args.putFloat("fat", meal.getFat());
            args.putFloat("fiber", meal.getFiber());
            args.putString("confidence", meal.getConfidence());
            NavHostFragment.findNavController(this).navigate(R.id.mealDetailFragment, args);
        });
        recyclerMeals.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerMeals.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return adapter.mealAt(vh.getBindingAdapterPosition()) == null ? 0 : super.getSwipeDirs(rv, vh);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                MealLog meal = adapter.mealAt(position);
                if (meal == null) { adapter.notifyItemChanged(position); return; }
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_delete_title)
                        .setMessage(getString(R.string.dialog_delete_confirm_format, meal.getFood_name()))
                        .setPositiveButton(R.string.action_confirm, (d, w) -> vm.delete(meal.getLog_id()))
                        .setNegativeButton(R.string.action_cancel, (d, w) -> adapter.notifyItemChanged(position))
                        .setCancelable(false)
                        .show();
            }
        }).attachToRecyclerView(recyclerMeals);
    }

    private void render(List<MealLog> meals) {
        int cal = 0;
        float pro = 0, carb = 0, fat = 0;
        for (MealLog m : meals) {
            cal += m.getCalories(); pro += m.getProtein();
            carb += m.getCarbohydrates(); fat += m.getFat();
        }
        adapter.submit(meals);
        layoutEmpty.setVisibility(meals.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerMeals.setVisibility(meals.isEmpty() ? View.GONE : View.VISIBLE);
        tvSubtitle.setText(getString(R.string.meal_list_subtitle_format, meals.size()));
        tvTotalCalories.setText(String.valueOf(cal));
        tvTotalProtein.setText(getString(R.string.format_gram_float, pro));
        tvTotalCarbs.setText(getString(R.string.format_gram_float, carb));
        tvTotalFat.setText(getString(R.string.format_gram_float, fat));
    }
}
