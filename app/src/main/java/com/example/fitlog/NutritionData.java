package com.example.fitlog;
public class NutritionData {
    public String foodName;
    public String servingSize;
    public int calories;
    public float protein;
    public float carbohydrates;
    public float fat;
    public float fiber;
    public String confidence;

    @Override
    public String toString() {
        return "Yemek: " + foodName + "\n" +
                "Porsiyon: " + servingSize + "\n\n" +
                "Kalori: " + calories + " kcal\n" +
                "Protein: " + protein + " g\n" +
                "Karbonhidrat: " + carbohydrates + " g\n" +
                "Yağ: " + fat + " g\n" +
                "Lif: " + fiber + " g\n\n" +
                "Güven seviyesi: " + confidence;
    }
}

