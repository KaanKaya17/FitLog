package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.AiMealPlan;
import com.example.fitlog.GeminiNutritionService;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.ZoneId;

/** Günlük AI beslenme planı (gün başına bir kez üretilir ve saklanır). */
public class AiCoachViewModel extends BaseViewModel {

    /** Plan üretimi için ekrandan gelen yerelleştirilmiş metinler. */
    public static final class Prompt {
        public final String profilePrompt;   // R.string.ai_coach_prompt (doldurulmuş)
        public final String weeklyTemplate;  // R.string.ai_coach_weekly_context (şablon)
        public final int calorieGoal, waterGoal;

        public Prompt(String profilePrompt, String weeklyTemplate, int calorieGoal, int waterGoal) {
            this.profilePrompt = profilePrompt; this.weeklyTemplate = weeklyTemplate;
            this.calorieGoal = calorieGoal; this.waterGoal = waterGoal;
        }
    }

    private final MutableLiveData<Resource<String>> plan = new MutableLiveData<>();

    public AiCoachViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<String>> plan() { return plan; }

    /** Bugünün kayıtlı planını getirir; yoksa yenisini üretir. */
    public void loadOrGenerate(Prompt p) {
        load(plan, () -> {
            AiMealPlan existing = repo.getTodayPlan();
            if (existing != null && existing.getPlan_content() != null) return existing.getPlan_content();
            return generate(p);
        });
    }

    public void regenerate(Prompt p) {
        load(plan, () -> generate(p));
    }

    private String generate(Prompt p) throws Exception {
        String weekly = "";
        try {
            weekly = CoachContext.load(repo, LocalDate.now(), ZoneId.systemDefault())
                    .format(p.weeklyTemplate, p.calorieGoal, p.waterGoal);
        } catch (Exception ignored) {
            // Haftalık veri alınamazsa yalnızca profil bilgisiyle devam et.
        }
        JsonObject req = request(p.profilePrompt + "\n\n" + weekly);
        String text = GeminiNutritionService.extractText(repo.generate(req));
        try { repo.saveTodayPlan(text); } catch (Exception ignored) {}
        return text;
    }

    static JsonObject request(String prompt) {
        JsonObject part = new JsonObject();
        part.addProperty("text", prompt);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject body = new JsonObject();
        body.add("contents", contents);
        JsonObject cfg = new JsonObject();
        cfg.addProperty("temperature", 0.7);
        body.add("generationConfig", cfg);
        return body;
    }
}
