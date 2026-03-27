package com.example.fitlog;


import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {

    //User Api
    @POST("rest/v1/users")
    Call<Void> insertUser(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearerToken,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer, // "return=minimal" diyerek hız kazanabiliriz
            @Body UserInsertDto user
    );

    @GET("rest/v1/users")
    Call<List<User>> getUserByEmail(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Query("email") String emailFilter,
            @Header("Accept") String accept
    );
    //---

    //Water Track Api
    @POST("rest/v1/water_log")
    Call<Void> insertWaterLog(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Body WaterLog waterLog
    );

    @GET("rest/v1/water_log")
    Call<List<WaterLog>> getTodayWaterLogs(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Accept") String accept,
            @Query("user_id") String userIdFilter,
            @Query("consumed_at") String dateFilter
    );
    //---

    //Meal Log Api
    @POST("rest/v1/meal_log")
    Call<Void> insertMealLog(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Body MealInsertDto mealLog
    );

    @GET("rest/v1/meal_log")
    Call<List<MealLog>> getTodayMealLogs(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Accept") String accept,
            @Query("user_id") String userIdFilter,
            @Query("eaten_at") String dateFilter
    );

    @DELETE("rest/v1/meal_log")
    Call<Void> deleteMealLog(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Query("log_id") String logIdFilter
    );
    //---

    @PATCH("rest/v1/users")
    Call<Void> updateUserGoals(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("user_id") String userIdFilter,
            @Body Map<String, Integer> body
    );

    @PATCH("rest/v1/users")
    Call<Void> updateUserPassword(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("user_id") String userIdFilter,
            @Body Map<String, String> body
    );

    @PATCH("rest/v1/users")
    Call<Void> updateUserProfile(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("user_id") String userIdFilter,
            @Body Map<String, Object> body
    );

    // Kullanıcıyı ID ile getir (blok kontrolü için)
    @GET("rest/v1/users")
    Call<List<User>> getUserById(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Header("Accept") String accept,
            @Query("user_id") String userIdFilter
    );
    //

    // Bildirimleri Getir
    @GET("rest/v1/admin_notifications")
    Call<List<AdminNotification>> getNotifications(
            @Query("user_id") String userIdFilter,    // Örn: "eq.5"
            @Query("is_read") String isReadFilter,    // Örn: "eq.false"
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth
    );

    // Okundu Olarak İşaretle
    @PATCH("rest/v1/admin_notifications")
    Call<Void> markNotificationAsRead(
            @Query("id") String idFilter,             // Örn: "eq.uuid-değeri"
            @Header("apikey") String apiKey,
            @Header("Authorization") String auth,
            @Header("Content-Type") String contentType,
            @Body Map<String, Object> updateBody      // {"is_read": true}
    );

}
