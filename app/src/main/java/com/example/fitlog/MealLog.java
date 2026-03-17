package com.example.fitlog;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class MealLog {
    private int user_id;
    private transient int log_id;
    private String food_name;
    private String serving_size;
    private int calories;
    private float protein;
    private float carbohydrates;
    private float fat;
    private float fiber;
    private String confidence;

    public MealLog() {}

    public MealLog(int userId, NutritionData data) {
        this.user_id       = userId;
        this.food_name     = data.foodName;
        this.serving_size  = data.servingSize;
        this.calories      = data.calories;
        this.protein       = data.protein;
        this.carbohydrates = data.carbohydrates;
        this.fat           = data.fat;
        this.fiber         = data.fiber;
        this.confidence    = data.confidence;
    }

    // Getter & Setter'lar
    public int getUser_id()                      { return user_id; }
    public void setUser_id(int user_id)          { this.user_id = user_id; }
    public String getFood_name()                 { return food_name; }
    public void setFood_name(String food_name)   { this.food_name = food_name; }
    public String getServing_size()              { return serving_size; }
    public void setServing_size(String s)        { this.serving_size = s; }
    public int getCalories()                     { return calories; }
    public void setCalories(int calories)        { this.calories = calories; }
    public float getProtein()                    { return protein; }
    public void setProtein(float protein)        { this.protein = protein; }
    public float getCarbohydrates()              { return carbohydrates; }
    public void setCarbohydrates(float c)        { this.carbohydrates = c; }
    public float getFat()                        { return fat; }
    public void setFat(float fat)                { this.fat = fat; }
    public float getFiber()                      { return fiber; }
    public void setFiber(float fiber)            { this.fiber = fiber; }
    public String getConfidence()                { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public int getLog_id() { return log_id; }
    public void setLog_id(int log_id) { this.log_id = log_id; }

    private String eaten_at;

    public String getEaten_at() { return eaten_at; }
    public void setEaten_at(String eaten_at) { this.eaten_at = eaten_at; }
}
