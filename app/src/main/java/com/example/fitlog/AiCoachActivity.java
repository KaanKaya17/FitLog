package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiCoachActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView tvLoading, tvPlanContent;
    private NestedScrollView scrollPlan;
    private FrameLayout bottomBar;
    private Button btnRegenerate;
    private String todayDate;
    private PrefManager prefManager;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_ai_coach);

        progressBar = findViewById(R.id.progressBar);
        tvLoading = findViewById(R.id.tvLoading);
        tvPlanContent = findViewById(R.id.tvPlanContent);
        scrollPlan = findViewById(R.id.scrollPlan);
        bottomBar = findViewById(R.id.bottomBar);
        btnRegenerate = findViewById(R.id.btnRegenerate);

        prefManager = new PrefManager(this);
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        btnRegenerate.setOnClickListener(v -> fetchPlanFromGemini());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        checkExistingPlan();
    }

    private void showLoading(boolean isLoading) {
        if(isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            tvLoading.setVisibility(View.VISIBLE);
            scrollPlan.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            tvLoading.setVisibility(View.GONE);
            scrollPlan.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void checkExistingPlan() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, getString(R.string.error_connection), Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);
        SupabaseApi authApi = RetrofitClient.getClient().create(SupabaseApi.class);
        String apiKey = BuildConfig.SUPABASE_API_KEY;
        String authHeader = "Bearer " + apiKey;
        String userIdFilter = "eq." + prefManager.getId();
        String dateFilter = "eq." + todayDate;

        authApi.getTodayAiMealPlan(apiKey, authHeader, "application/json", userIdFilter, dateFilter)
                .enqueue(new Callback<List<AiMealPlan>>() {
                    @Override
                    public void onResponse(Call<List<AiMealPlan>> call, Response<List<AiMealPlan>> response) {
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            // Ekrandaki içeriği güncelle
                            AiMealPlan plan = response.body().get(0);
                            tvPlanContent.setText(plan.getPlan_content());
                            showLoading(false);
                        } else {
                            // Yoksa yeni plan oluştur
                            fetchPlanFromGemini();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<AiMealPlan>> call, Throwable t) {
                        fetchPlanFromGemini(); // Hata olsa da API'yi zorla deneyelim
                    }
                });
    }

    private void fetchPlanFromGemini() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            Toast.makeText(this, getString(R.string.error_connection), Toast.LENGTH_SHORT).show();
            return;
        }
        showLoading(true);

        String name = prefManager.getFirstName();
        String gender = prefManager.getGender();
        float weight = prefManager.getWeight();
        float height = prefManager.getHeight();
        String goal = prefManager.getGoal();

        if (gender == null || gender.isEmpty() || weight == 0 || height == 0 || goal == null || goal.isEmpty()) {
            Toast.makeText(this, getString(R.string.ai_error_empty_profile), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Prompt (İstem)
        String prompt = getString(R.string.ai_coach_prompt, name, gender, String.valueOf(height), String.valueOf(weight), goal);

        // JSON formatına çevirme
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject part = new JsonObject();
        JsonObject textObj = new JsonObject();
        textObj.addProperty("text", prompt);
        JsonArray partsArray = new JsonArray();
        partsArray.add(textObj);
        part.add("parts", partsArray);
        contents.add(part);
        requestBody.add("contents", contents);

        GeminiApi geminiApi = GeminiApiClient.getClient().create(GeminiApi.class);
        String geminiApiKey = loadApiKey();

        geminiApi.generateContent(geminiApiKey, requestBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if(response.isSuccessful() && response.body() != null) {
                    try {
                        String generatedText = response.body()
                                .getAsJsonArray("candidates").get(0).getAsJsonObject()
                                .getAsJsonObject("content").getAsJsonArray("parts").get(0)
                                .getAsJsonObject().get("text").getAsString();

                        tvPlanContent.setText(generatedText);
                        savePlanToDatabase(generatedText);

                    } catch (Exception e) {
                        Log.e("GEMINI_ERROR", e.toString());
                        Toast.makeText(AiCoachActivity.this, getString(R.string.ai_error_response), Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    }
                } else {
                    try {
                        String errorString = response.errorBody() != null ? response.errorBody().string() : "Bilinmeyen API Hatası";
                        Log.e("GEMINI_API_ERROR", errorString);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Toast.makeText(AiCoachActivity.this, getString(R.string.ai_error_api), Toast.LENGTH_LONG).show();
                    showLoading(false);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e("GEMINI_NET_ERROR", t.toString());
                Toast.makeText(AiCoachActivity.this, getString(R.string.ai_error_network), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void savePlanToDatabase(String planContent) {
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        AiMealPlan newPlan = new AiMealPlan(prefManager.getId(), planContent);
        String apiKey = BuildConfig.SUPABASE_API_KEY;
        String authHeader = "Bearer " + apiKey;

        api.insertAiMealPlan(apiKey, authHeader, "application/json", "return=minimal", newPlan)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        showLoading(false);
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        showLoading(false);
                    }
                });
    }

    private String loadApiKey() {
        try {
            java.io.InputStream is = getAssets().open("config.properties");
            java.util.Properties props = new java.util.Properties();
            props.load(is);
            return props.getProperty("GEMINI_API_KEY");
        } catch (Exception e) {
            return "";
        }
    }

    public void btnBack(View view) {
        finish();
    }
}
