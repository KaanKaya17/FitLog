package com.example.fitlog;

public class MealInsertDto {
    private int user_id;
    private String food_name;
    private String serving_size;
    private int calories;
    private float protein;
    private float carbohydrates;
    private float fat;
    private float fiber;
    private String confidence;

    public MealInsertDto(int userId, NutritionData data) {
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

    public int getUser_id() { return user_id; }
    public String getFood_name() { return food_name; }
    public String getServing_size() { return serving_size; }
    public int getCalories() { return calories; }
    public float getProtein() { return protein; }
    public float getCarbohydrates() { return carbohydrates; }
    public float getFat() { return fat; }
    public float getFiber() { return fiber; }
    public String getConfidence() { return confidence; }
}
