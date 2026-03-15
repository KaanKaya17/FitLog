package com.example.fitlog;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AddMeal extends AppCompatActivity {

    private EditText etFood;
    private Button btnAnalyze;
    private ProgressBar progressBar;
    private TextView tvResult;
    private GeminiNutritionService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_meal);

        etFood      = findViewById(R.id.etFood);
        btnAnalyze  = findViewById(R.id.btnAnalyze);
        progressBar = findViewById(R.id.progressBar);
        tvResult    = findViewById(R.id.tvResult);
        service = new GeminiNutritionService(this);

        android.util.Log.d("API_KEY_TEST", "Key: " + service.getApiKey());

        btnAnalyze.setOnClickListener(v -> {
            String food = etFood.getText().toString().trim();
            if (food.isEmpty()) {
                tvResult.setText("Lütfen bir yemek girin.");
                return;
            }
            startLoading();
            service.analyzeTextFood(food, new NutritionCallback() {
                @Override
                public void onSuccess(NutritionData data) {
                    runOnUiThread(() -> {
                        stopLoading();
                        tvResult.setText(data.toString());
                    });
                }
                @Override
                public void onError(String errorMessage) {
                    runOnUiThread(() -> {
                        stopLoading();
                        tvResult.setText("Hata: " + errorMessage);
                    });
                }
            });
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void startLoading() {
        btnAnalyze.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        tvResult.setText("Analiz ediliyor...");
    }

    private void stopLoading() {
        btnAnalyze.setEnabled(true);
        progressBar.setVisibility(View.GONE);
    }
}