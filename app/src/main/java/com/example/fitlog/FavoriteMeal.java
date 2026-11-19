package com.example.fitlog;

/** favorite_meals tablosu satırı. */
public class FavoriteMeal {
    private Long id;
    private int user_id;
    private String food_name;
    private String serving_size;
    private int calories;
    private float protein;
    private float carbohydrates;
    private float fat;
    private float fiber;

    public FavoriteMeal() {}

    public FavoriteMeal(int userId, NutritionData d) {
        this.user_id = userId;
        this.food_name = d.foodName;
        this.serving_size = d.servingSize;
        this.calories = d.calories;
        this.protein = d.protein;
        this.carbohydrates = d.carbohydrates;
        this.fat = d.fat;
        this.fiber = d.fiber;
    }

    public Long getId() { return id; }
    public String getFood_name() { return food_name; }

    public NutritionData toNutrition() {
        NutritionData d = new NutritionData();
        d.foodName = food_name; d.servingSize = serving_size; d.calories = calories;
        d.protein = protein; d.carbohydrates = carbohydrates; d.fat = fat; d.fiber = fiber;
        d.confidence = "high";
        return d;
    }
}
