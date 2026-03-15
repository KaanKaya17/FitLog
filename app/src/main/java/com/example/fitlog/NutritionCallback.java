package com.example.fitlog;

public interface NutritionCallback {
    void onSuccess(NutritionData data);
    void onError(String errorMessage);
}

