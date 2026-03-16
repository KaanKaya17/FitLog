package com.example.fitlog;

import java.util.List;

public class DayHistory {
    public String date;        // "2025-03-16"
    public String dayLabel;    // "Bugün", "Dün", "Pazartesi" vb.
    public String displayDate; // "16 Mart"
    public int totalCalories;
    public int totalWater;
    public float totalProtein;
    public float totalCarbs;
    public float totalFat;
    public List<MealLog> meals;

    public DayHistory(String date, String dayLabel, String displayDate) {
        this.date        = date;
        this.dayLabel    = dayLabel;
        this.displayDate = displayDate;
        this.meals       = new java.util.ArrayList<>();
    }
}