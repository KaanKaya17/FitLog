package com.example.fitlog;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AiCoachFragment extends Fragment {

    private LottieAnimationView lottieAnimationView;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai_coach, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getContext() == null) return;

        lottieAnimationView = view.findViewById(R.id.lottieAnimationView);
        tvLoading       = view.findViewById(R.id.tvLoading);
        llPlanContainer = view.findViewById(R.id.llPlanContainer);
        scrollPlan      = view.findViewById(R.id.scrollPlan);
        bottomBar       = view.findViewById(R.id.bottomBar);
        btnRegenerate   = view.findViewById(R.id.btnRegenerate);
        includeNoInternet = view.findViewById(R.id.includeNoInternet);
        btnRetryInternet  = includeNoInternet.findViewById(R.id.btnRetryInternet);

        firebaseAnalytics = FirebaseAnalytics.getInstance(getContext());
        prefManager = new PrefManager(getContext());
        todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        btnRegenerate.setOnClickListener(v -> fetchPlanFromGemini());
        btnRetryInternet.setOnClickListener(v -> checkExistingPlan());

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v ->
                androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp());

        checkExistingPlan();
    }

    private void showLoading(boolean isLoading) {
        if (includeNoInternet != null) includeNoInternet.setVisibility(View.GONE);
        if (isLoading) {
            lottieAnimationView.setVisibility(View.VISIBLE);
            tvLoading.setVisibility(View.VISIBLE);
            scrollPlan.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
        } else {
            lottieAnimationView.setVisibility(View.GONE);
            tvLoading.setVisibility(View.GONE);
            scrollPlan.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
        }
    }

    private void showNoInternet() {
        lottieAnimationView.setVisibility(View.GONE);
        tvLoading.setVisibility(View.GONE);
        scrollPlan.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        if (includeNoInternet != null) includeNoInternet.setVisibility(View.VISIBLE);
    }

    private void checkExistingPlan() {
        if (getContext() == null) return;
        if (!NetworkUtils.isNetworkAvailable(getContext())) { showNoInternet(); return; }
        showLoading(true);

        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);

        api.getAiMealPlans("eq." + prefManager.getId(), "eq." + todayDate)
                .enqueue(new Callback<List<AiMealPlan>>() {
                    @Override
                    public void onResponse(Call<List<AiMealPlan>> call, Response<List<AiMealPlan>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                            renderPlanToCards(response.body().get(0).getPlan_content());
                            showLoading(false);
                        } else {
                            fetchPlanFromGemini();
                        }
                    }
                    @Override
                    public void onFailure(Call<List<AiMealPlan>> call, Throwable t) {
                        if (isAdded()) fetchPlanFromGemini();
                    }
                });
    }

    private void fetchPlanFromGemini() {
        if (getContext() == null) return;
        if (!NetworkUtils.isNetworkAvailable(getContext())) { showNoInternet(); return; }
        showLoading(true);

        String name = prefManager.getFirstName();
        String gender = prefManager.getGender();
        float weight = prefManager.getWeight();
        float height = prefManager.getHeight();
        String goal = prefManager.getGoal();

        if (gender == null || gender.isEmpty() || weight == 0 || height == 0 || goal == null || goal.isEmpty()) {
            Toast.makeText(getContext(), getString(R.string.ai_error_empty_profile), Toast.LENGTH_LONG).show();
            androidx.navigation.fragment.NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        String prompt = getString(R.string.ai_coach_prompt, name, gender, String.valueOf(height), String.valueOf(weight), goal);

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

        // Gemini isteği, API anahtarını sunucuda tutan Edge Function üzerinden gider.
        SupabaseApi geminiApi = RetrofitClient.getClient().create(SupabaseApi.class);

        geminiApi.generateContent(requestBody).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String text = GeminiNutritionService.extractText(response.body());
                        renderPlanToCards(text);
                        savePlanToDatabase(text);

                        Bundle aiBundle = new Bundle();
                        aiBundle.putString("goal", goal);
                        firebaseAnalytics.logEvent("ai_coach_used", aiBundle);
                    } catch (Exception e) {
                        Log.w("AI_COACH", "Gemini yanıtı çözümlenemedi", e);
                        Toast.makeText(getContext(), getString(R.string.ai_error_response), Toast.LENGTH_SHORT).show();
                        showLoading(false);
                    }
                } else {
                    Log.w("AI_COACH", "Gemini proxy hatası: " + GeminiNutritionService.errorMessage(response));
                    Toast.makeText(getContext(), getString(R.string.ai_error_api), Toast.LENGTH_LONG).show();
                    showLoading(false);
                }
            }
            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), getString(R.string.ai_error_network), Toast.LENGTH_SHORT).show();
                showLoading(false);
            }
        });
    }

    private void savePlanToDatabase(String planContent) {
        SupabaseApi api = RetrofitClient.getClient().create(SupabaseApi.class);
        AiMealPlan newPlan = new AiMealPlan(prefManager.getId(), planContent);

        api.insertAiMealPlan(newPlan)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> call, Response<Void> r) { if (isAdded()) showLoading(false); }
                    @Override public void onFailure(Call<Void> call, Throwable t) { if (isAdded()) showLoading(false); }
                });
    }

    private void renderPlanToCards(String markdownText) {
        llPlanContainer.removeAllViews();
        String[] lines = markdownText.split("\n");
        StringBuilder currentContent = new StringBuilder();
        String currentTitle = "Nutrition Plan";
        LayoutInflater inflater = LayoutInflater.from(getContext());
        boolean hasTitle = false;

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("#")) {
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
        String formatted = content.replaceAll("\\*\\*(.*?)\\*\\*", "$1").replaceAll("\\*(.*?)\\*", "$1");
        tvContent.setText(formatted);
        llPlanContainer.addView(cardView);
    }
}
