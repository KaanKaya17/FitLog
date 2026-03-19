package com.example.fitlog;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MealListActivity extends AppCompatActivity {

    private RecyclerView recyclerMeals;
    private LinearLayout layoutEmpty;
    private TextView tvSubtitle, tvTotalCalories, tvTotalProtein, tvTotalCarbs, tvTotalFat;
    private MealAdapter adapter;
    private List<MealLog> mealList = new ArrayList<>();

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_meal_list);

        initViews();
        setupRecycler();

        findViewById(R.id.fabAddMeal).setOnClickListener(v ->
                Navigation.toAddMeal(this)
        );

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMeals();
    }

    private void initViews() {
        recyclerMeals    = findViewById(R.id.recyclerMeals);
        layoutEmpty      = findViewById(R.id.layoutEmpty);
        tvSubtitle       = findViewById(R.id.tvSubtitle);
        tvTotalCalories  = findViewById(R.id.tvTotalCalories);
        tvTotalProtein   = findViewById(R.id.tvTotalProtein);
        tvTotalCarbs     = findViewById(R.id.tvTotalCarbs);
        tvTotalFat       = findViewById(R.id.tvTotalFat);
    }

    private void setupRecycler() {
        adapter = new MealAdapter(mealList, meal -> {
            // Karta tıklayınca detay sayfasına git
            Intent intent = new Intent(this, MealDetail.class);
            intent.putExtra("log_id",       meal.getLog_id());
            intent.putExtra("food_name",    meal.getFood_name());
            intent.putExtra("serving_size", meal.getServing_size());
            intent.putExtra("calories",     meal.getCalories());
            intent.putExtra("protein",      meal.getProtein());
            intent.putExtra("carbs",        meal.getCarbohydrates());
            intent.putExtra("fat",          meal.getFat());
            intent.putExtra("fiber",        meal.getFiber());
            intent.putExtra("confidence",   meal.getConfidence());
            startActivity(intent);
        });

        recyclerMeals.setLayoutManager(new LinearLayoutManager(this));
        recyclerMeals.setAdapter(adapter);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) { return false; }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                MealLog meal = mealList.get(position);

                new androidx.appcompat.app.AlertDialog.Builder(MealListActivity.this)
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
        PrefManager pref    = new PrefManager(this);
        String userIdFilter = "eq." + pref.getId();
        String today        = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String dateFilter   = "gte." + today + "T00:00:00Z";

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<List<MealLog>> response = api.getTodayMealLogs(
                        apiKey, authHeader, "application/json", userIdFilter, dateFilter
                ).execute();

                if (response.isSuccessful() && response.body() != null) {
                    List<MealLog> meals = response.body();

                    // Toplamları hesapla
                    int totalCal      = 0;
                    float totalPro    = 0, totalCarb = 0, totalFat = 0;
                    for (MealLog m : meals) {
                        totalCal  += m.getCalories();
                        totalPro  += m.getProtein();
                        totalCarb += m.getCarbohydrates();
                        totalFat  += m.getFat();
                    }

                    final int fCal      = totalCal;
                    final float fPro    = totalPro;
                    final float fCarb   = totalCarb;
                    final float fFat    = totalFat;

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        mealList.clear();
                        mealList.addAll(meals);
                        adapter.notifyDataSetChanged();

                        // Boş durum
                        if (meals.isEmpty()) {
                            layoutEmpty.setVisibility(View.VISIBLE);
                            recyclerMeals.setVisibility(View.GONE);
                        } else {
                            layoutEmpty.setVisibility(View.GONE);
                            recyclerMeals.setVisibility(View.VISIBLE);
                        }

                        // Özet güncelle
                        tvSubtitle.setText(getString(R.string.meal_list_subtitle_format, meals.size()));
                        tvTotalCalories.setText(String.valueOf(fCal));
                        tvTotalProtein.setText(getString(R.string.format_gram_float, fPro));
                        tvTotalCarbs.setText(getString(R.string.format_gram_float, fCarb));
                        tvTotalFat.setText(getString(R.string.format_gram_float, fFat));
                    });
                }

            } catch (Exception e) {
                Log.e("MEAL_LIST", "Hata: " + e.getMessage());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    Toast.makeText(this, getString(R.string.error_data_load_failed), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void deleteMeal(MealLog meal, int position) {
        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<Void> response = api.deleteMealLog(
                        apiKey, authHeader, "eq." + meal.getLog_id()
                ).execute();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (response.isSuccessful()) {
                        mealList.remove(position);
                        adapter.notifyItemRemoved(position);
                        Toast.makeText(this, getString(R.string.msg_meal_deleted), Toast.LENGTH_SHORT).show();
                        loadMeals();
                    } else {
                        adapter.notifyItemChanged(position);
                        Toast.makeText(this, getString(R.string.error_delete_failed_format, response.code()), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    adapter.notifyItemChanged(position);
                    Toast.makeText(this, getString(R.string.error_connection), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    public void navHomePage(View view) {
        Navigation.toMain(this);
        finish();
    }

    public void navProfile(View view) {
        PrefManager manager = new PrefManager(this);
        int userId = manager.getId();
        if (userId != -1) {
            Navigation.toProfile(this);
        } else {
            Navigation.toRegister(this);
        }
    }
    public void navMealList(View view){
        // Zaten MealListActivity'deyiz, tekrar açma
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }
}