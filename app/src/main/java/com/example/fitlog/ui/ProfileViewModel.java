package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.PrefManager;
import com.example.fitlog.R;
import com.example.fitlog.domain.NutritionCalculator;

import java.time.Year;
import java.util.HashMap;
import java.util.Map;

/** Profil kaydetme ve hedef hesaplama. */
public class ProfileViewModel extends BaseViewModel {

    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> targetsApplied = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application app) { super(app); }

    public LiveData<Event<Boolean>> saved() { return saved; }
    public LiveData<Event<Boolean>> targetsApplied() { return targetsApplied; }

    /** Form verisi (doğrulanmış). */
    public static final class ProfileForm {
        public String firstName, lastName, gender, goal, activity;
        public float heightCm, weightKg;
        public int birthYear;      // 0 = boş
        public float targetWeight; // 0 = boş
    }

    public void save(ProfileForm f) {
        Map<String, Object> m = new HashMap<>();
        m.put("first_name", f.firstName);
        m.put("last_name", f.lastName);
        m.put("gender", f.gender);
        m.put("goal", f.goal);
        m.put("height_cm", f.heightCm);
        m.put("weight_kg", f.weightKg);
        m.put("activity_level", f.activity);
        m.put("birth_year", f.birthYear > 0 ? f.birthYear : null);
        m.put("target_weight_kg", f.targetWeight > 0 ? f.targetWeight : null);

        run(() -> { repo.updateProfile(m); return null; }, ignored -> {
            PrefManager p = new PrefManager(getApplication());
            p.setFirstName(f.firstName); p.setLastName(f.lastName);
            p.setGender(f.gender); p.setGoal(f.goal);
            p.setHeight(f.heightCm); p.setWeight(f.weightKg);
            p.setActivityLevel(f.activity);
            p.setBirthYear(f.birthYear);
            p.setTargetWeight(f.targetWeight);
            saved.setValue(new Event<>(true));
        });
    }

    /** Kayıtlı profilden hedef hesaplar; profil eksikse null. */
    public NutritionCalculator.Targets suggestTargets() {
        PrefManager p = new PrefManager(getApplication());
        return suggest(p.getGender(), p.getHeight(), p.getWeight(), p.getBirthYear(),
                p.getActivityLevel(), p.getGoal(), Year.now().getValue());
    }

    static NutritionCalculator.Targets suggest(String gender, float height, float weight, int birthYear,
                                               String activity, String goal, int currentYear) {
        if (gender == null || gender.isEmpty() || height <= 0 || weight <= 0 || birthYear <= 0
                || goal == null || goal.isEmpty()) return null;
        try {
            return NutritionCalculator.calculate(NutritionCalculator.isMale(gender), weight, height,
                    NutritionCalculator.ageFromBirthYear(birthYear, currentYear),
                    NutritionCalculator.Activity.fromKey(activity),
                    NutritionCalculator.Goal.fromKey(goal));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void saveGoals(int calories, int waterMl) {
        Map<String, Object> m = new HashMap<>();
        m.put("daily_calorie_goal", calories);
        m.put("daily_water_goal_ml", waterMl);
        run(() -> { repo.updateProfile(m); return null; }, ignored -> {
            PrefManager p = new PrefManager(getApplication());
            p.setCalorieGoal(calories);
            p.setWaterGoal(waterMl);
            postMessage(R.string.goals_saved);
        });
    }

    public void applyTargets(NutritionCalculator.Targets t) {
        Map<String, Object> m = new HashMap<>();
        m.put("daily_calorie_goal", t.calories);
        m.put("daily_water_goal_ml", t.waterMl);
        m.put("daily_protein_goal_g", t.proteinG);
        m.put("daily_carbs_goal_g", t.carbsG);
        m.put("daily_fat_goal_g", t.fatG);
        run(() -> { repo.updateProfile(m); return null; }, ignored -> {
            PrefManager p = new PrefManager(getApplication());
            p.setCalorieGoal(t.calories);
            p.setWaterGoal(t.waterMl);
            p.setMacroGoals(t.proteinG, t.carbsG, t.fatG);
            postMessage(R.string.targets_applied);
            targetsApplied.setValue(new Event<>(true));
        });
    }
}
