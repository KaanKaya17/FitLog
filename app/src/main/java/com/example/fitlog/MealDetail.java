package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Locale;

public class MealDetail extends AppCompatActivity {

    private int logId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_meal_detail);

        // Intent'ten gelen verileri al
        logId             = getIntent().getIntExtra("log_id", -1);
        String foodName   = getIntent().getStringExtra("food_name");
        String serving    = getIntent().getStringExtra("serving_size");
        int calories      = getIntent().getIntExtra("calories", 0);
        float protein     = getIntent().getFloatExtra("protein", 0);
        float carbs       = getIntent().getFloatExtra("carbs", 0);
        float fat         = getIntent().getFloatExtra("fat", 0);
        float fiber       = getIntent().getFloatExtra("fiber", 0);
        String confidence = getIntent().getStringExtra("confidence");

        // View'lara yerleştir
        ((TextView) findViewById(R.id.tvFoodName)).setText(foodName);
        ((TextView) findViewById(R.id.tvServingSize)).setText(serving);
        ((TextView) findViewById(R.id.tvCalories)).setText(String.valueOf(calories));
        ((TextView) findViewById(R.id.tvProtein)).setText(String.format(Locale.getDefault(), "%.1f g", protein));
        ((TextView) findViewById(R.id.tvCarbs)).setText(String.format(Locale.getDefault(), "%.1f g", carbs));
        ((TextView) findViewById(R.id.tvFat)).setText(String.format(Locale.getDefault(), "%.1f g", fat));
        ((TextView) findViewById(R.id.tvFiber)).setText(String.format(Locale.getDefault(), "%.1f g", fiber));

        // Güven rozeti rengi
        TextView tvConfidence = findViewById(R.id.tvConfidence);
        tvConfidence.setText(confidence);
        if ("high".equalsIgnoreCase(confidence)) {
            tvConfidence.setTextColor(getColor(R.color.green));
        } else if ("medium".equalsIgnoreCase(confidence)) {
            tvConfidence.setTextColor(getColor(android.R.color.holo_orange_dark));
        } else {
            tvConfidence.setTextColor(getColor(android.R.color.holo_red_dark));
        }

        // Geri butonu
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // Sil butonu
        findViewById(R.id.btnDelete).setOnClickListener(v -> deleteMeal());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void deleteMeal() {
        if (logId == -1) return;

        new Thread(() -> {
            try {
                SupabaseApi api   = RetrofitClient.getClient().create(SupabaseApi.class);
                String apiKey     = BuildConfig.SUPABASE_API_KEY;
                String authHeader = "Bearer " + apiKey;

                retrofit2.Response<Void> response = api.deleteMealLog(
                        apiKey,
                        authHeader,
                        "eq." + logId
                ).execute();

                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(this, "Öğün silindi.", Toast.LENGTH_SHORT).show();
                        finish(); // listeye geri dön
                    } else {
                        Toast.makeText(this, "Silme işlemi başarısız.", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("MEAL_DELETE", "Hata: " + e.getMessage());
                runOnUiThread(() ->
                        Toast.makeText(this, "Bağlantı hatası.", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
}