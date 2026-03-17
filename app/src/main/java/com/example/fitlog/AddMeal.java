package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AddMeal extends AppCompatActivity {

    private EditText etFood;
    private Button btnAnalyze;
    private Button btnSave;
    private ProgressBar progressBar;
    private LinearLayout cardResult;
    private GeminiNutritionService service;

    // Sonuç view'ları
    private TextView tvFoodName, tvServingSize, tvConfidence;
    private TextView tvCalories, tvProtein, tvCarbs, tvFat, tvFiber;

    // Son analiz sonucu (kaydetmek için saklıyoruz)
    private NutritionData lastResult = null;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_meal);

        etFood      = findViewById(R.id.etFood);
        btnAnalyze  = findViewById(R.id.btnAnalyze);
        btnSave     = findViewById(R.id.btnSave);
        progressBar = findViewById(R.id.progressBar);
        cardResult  = findViewById(R.id.cardResult);

        tvFoodName    = findViewById(R.id.tvFoodName);
        tvServingSize = findViewById(R.id.tvServingSize);
        tvConfidence  = findViewById(R.id.tvConfidence);
        tvCalories    = findViewById(R.id.tvCalories);
        tvProtein     = findViewById(R.id.tvProtein);
        tvCarbs       = findViewById(R.id.tvCarbs);
        tvFat         = findViewById(R.id.tvFat);
        tvFiber       = findViewById(R.id.tvFiber);

        service = new GeminiNutritionService(this);

        // Analiz Et
        btnAnalyze.setOnClickListener(v -> {
            String food = etFood.getText().toString().trim();
            if (food.isEmpty()) {
                Toast.makeText(this, "Lütfen bir yemek girin.", Toast.LENGTH_SHORT).show();
                return;
            }
            startLoading();
            service.analyzeTextFood(food, new NutritionCallback() {
                @Override
                public void onSuccess(NutritionData data) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        stopLoading();
                        lastResult = data;
                        showResult(data);
                    });
                }
                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        stopLoading();
                        Toast.makeText(AddMeal.this, "Hata: " + errorMessage, Toast.LENGTH_LONG).show();
                    });
                }
            });
        });

        // Kaydet
        btnSave.setOnClickListener(v -> {
            if (lastResult != null) {
                saveMealToSupabase(lastResult);
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    // ─── UI Güncelleme ──────────────────────────────────────────────

    private void showResult(NutritionData data) {
        tvFoodName.setText(data.foodName);
        tvServingSize.setText(data.servingSize);
        tvCalories.setText(String.valueOf(data.calories));
        tvProtein.setText(data.protein + "g");
        tvCarbs.setText(data.carbohydrates + "g");
        tvFat.setText(data.fat + "g");
        tvFiber.setText(data.fiber + "g");

        // Güven rozeti
        tvConfidence.setText(data.confidence);
        if ("high".equalsIgnoreCase(data.confidence)) {
            tvConfidence.setTextColor(getColor(R.color.green));
        } else if ("medium".equalsIgnoreCase(data.confidence)) {
            tvConfidence.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            tvConfidence.setTextColor(getColor(android.R.color.holo_red_dark));
        }

        // Kartı ve kaydet butonunu göster
        cardResult.setVisibility(View.VISIBLE);
        btnSave.setVisibility(View.VISIBLE);
    }

    private void startLoading() {
        btnAnalyze.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        cardResult.setVisibility(View.GONE);
        btnSave.setVisibility(View.GONE);
        lastResult = null;
    }

    private void stopLoading() {
        btnAnalyze.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }

    // ─── Supabase Kaydet ────────────────────────────────────────────

    private void saveMealToSupabase(NutritionData data) {
        btnSave.setEnabled(false);

        new Thread(() -> {
            try {
                SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                PrefManager pref = new PrefManager(this);
                MealInsertDto mealLog  = new MealInsertDto(pref.getId(), data);

                retrofit2.Response<Void> response = api.insertMealLog(
                        apiKey,
                        authHeader,
                        "application/json",
                        "return=minimal",
                        mealLog
                ).execute();

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnSave.setEnabled(true);
                    if (response.isSuccessful()) {
                        Toast.makeText(this, "✅ Öğün kaydedildi!", Toast.LENGTH_SHORT).show();
                        // Formu temizle
                        etFood.setText("");
                        cardResult.setVisibility(View.GONE);
                        btnSave.setVisibility(View.GONE);
                        lastResult = null;
                    } else {
                        Toast.makeText(this, "Kayıt sırasında hata oluştu.", Toast.LENGTH_SHORT).show();
                        Log.e("SUPABASE_MEAL", "Hata: " + response.code());
                    }
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    btnSave.setEnabled(true);
                    Toast.makeText(this, "Bağlantı hatası: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        Navigation.toMealList(this);
        finish();
    }

    public void navStatistic(View view){
        Navigation.toHistory(this);
        finish();
    }
}