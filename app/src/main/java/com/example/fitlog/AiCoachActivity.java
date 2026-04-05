package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.view.LayoutInflater;

import com.google.firebase.analytics.FirebaseAnalytics;

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
    private TextView tvLoading;
    private LinearLayout llPlanContainer;
    private NestedScrollView scrollPlan;
    private FrameLayout bottomBar;
    private Button btnRegenerate;
    private View includeNoInternet;
    private Button btnRetryInternet;
    private String todayDate;
    private PrefManager prefManager;
    private FirebaseAnalytics firebaseAnalytics;

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
        llPlanContainer = findViewById(R.id.llPlanContainer);
        scrollPlan = findViewById(R.id.scrollPlan);
        bottomBar = findViewById(R.id.bottomBar);
        btnRegenerate = findViewById(R.id.btnRegenerate);
        includeNoInternet = findViewById(R.id.includeNoInternet);
        btnRetryInternet = includeNoInternet.findViewById(R.id.btnRetryInternet);

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);

        prefManager = new PrefManager(this);
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        btnRegenerate.setOnClickListener(v -> fetchPlanFromGemini());
        btnRetryInternet.setOnClickListener(v -> checkExistingPlan());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        checkExistingPlan();
    }

    private void showLoading(boolean isLoading) {
        if (includeNoInternet != null) includeNoInternet.setVisibility(View.GONE);
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

    private void showNoInternet() {
        progressBar.setVisibility(View.GONE);
        tvLoading.setVisibility(View.GONE);
        scrollPlan.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        if (includeNoInternet != null) includeNoInternet.setVisibility(View.VISIBLE);
    }

    private void checkExistingPlan() {
        if (!NetworkUtils.isNetworkAvailable(this)) {
            showNoInternet();
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
                            renderPlanToCards(plan.getPlan_content());
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
            showNoInternet();
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

                        renderPlanToCards(generatedText);
                        savePlanToDatabase(generatedText);
                        
                        // Firebase Analytics: AI Coach kullanıldı
                        Bundle aiBundle = new Bundle();
                        aiBundle.putString("goal", goal);
                        firebaseAnalytics.logEvent("ai_coach_used", aiBundle);

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

    private void renderPlanToCards(String markdownText) {
        llPlanContainer.removeAllViews();
        String[] lines = markdownText.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "Nutrition Plan";
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean hasTitle = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("#")) {
                // Save previous card if it has content
                if (hasTitle || currentContent.length() > 0) {
                    addCardToContainer(inflater, currentTitle, currentContent.toString().trim());
                    currentContent.setLength(0);
                }
                currentTitle = trimmedLine.replaceAll("^#+\\s*", "").replaceAll("\\*\\*", "");
                hasTitle = true;
            } else if (!trimmedLine.isEmpty()) {
                currentContent.append(line).append("\n");
            }
        }
        
        // Add last card
        if (hasTitle || currentContent.length() > 0) {
            addCardToContainer(inflater, currentTitle, currentContent.toString().trim());
        }
    }

    private void addCardToContainer(LayoutInflater inflater, String title, String content) {
        if (title.isEmpty() && content.isEmpty()) return;
        View cardView = inflater.inflate(R.layout.item_ai_coach_card, llPlanContainer, false);
        TextView tvTitle = cardView.findViewById(R.id.tvCardTitle);
        TextView tvContent = cardView.findViewById(R.id.tvCardContent);
        
        tvTitle.setText(title);
        // Clean up markdown bold markers for simpler look
        String formattedContent = content.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        formattedContent = formattedContent.replaceAll("\\*(.*?)\\*", "$1");
        tvContent.setText(formattedContent);
        
        llPlanContainer.addView(cardView);
    }

    public void btnBack(View view) {
        finish();
    }
}
