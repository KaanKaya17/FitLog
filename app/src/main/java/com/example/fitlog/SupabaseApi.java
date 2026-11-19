package com.example.fitlog;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * Supabase REST (PostgREST) ve Edge Function uç noktaları.
 *
 * "apikey" ve "Authorization: Bearer <kullanıcı JWT>" başlıkları
 * {@link RetrofitClient} içindeki interceptor tarafından otomatik eklenir.
 * Hangi satırların okunup yazılabileceğini sunucudaki RLS politikaları belirler;
 * buradaki user_id filtreleri yalnızca sorguyu daraltmak içindir.
 */
public interface SupabaseApi {

    String PREFER_MINIMAL = "Prefer: return=minimal";

    // ── Users ──────────────────────────────────────────────────────────
    @GET("rest/v1/users")
    Call<List<User>> getUserByAuthId(@Query("auth_id") String authIdFilter);

    @GET("rest/v1/users")
    Call<List<User>> getUserById(@Query("user_id") String userIdFilter);

    @Headers(PREFER_MINIMAL)
    @PATCH("rest/v1/users")
    Call<Void> updateUser(@Query("user_id") String userIdFilter, @Body Map<String, Object> body);

    // ── Water ──────────────────────────────────────────────────────────
    @Headers(PREFER_MINIMAL)
    @POST("rest/v1/water_log")
    Call<Void> insertWaterLog(@Body WaterLog waterLog);

    @GET("rest/v1/water_log")
    Call<List<WaterLog>> getWaterLogs(
            @Query("user_id") String userIdFilter,
            @Query("consumed_at") String dateFilter
    );

    // ── Meals ──────────────────────────────────────────────────────────
    @Headers(PREFER_MINIMAL)
    @POST("rest/v1/meal_log")
    Call<Void> insertMealLog(@Body MealInsertDto mealLog);

    @GET("rest/v1/meal_log")
    Call<List<MealLog>> getMealLogs(
            @Query("user_id") String userIdFilter,
            @Query("eaten_at") String dateFilter
    );

    @DELETE("rest/v1/meal_log")
    Call<Void> deleteMealLog(@Query("log_id") String logIdFilter);

    // ── Admin notifications ────────────────────────────────────────────
    @GET("rest/v1/admin_notifications")
    Call<List<AdminNotification>> getNotifications(
            @Query("user_id") String userIdFilter,
            @Query("is_read") String isReadFilter
    );

    @Headers(PREFER_MINIMAL)
    @PATCH("rest/v1/admin_notifications")
    Call<Void> markNotificationAsRead(@Query("id") String idFilter, @Body Map<String, Object> body);

    // ── AI meal plans ──────────────────────────────────────────────────
    @Headers(PREFER_MINIMAL)
    @POST("rest/v1/ai_meal_plans")
    Call<Void> insertAiMealPlan(@Body AiMealPlan aiMealPlan);

    @GET("rest/v1/ai_meal_plans")
    Call<List<AiMealPlan>> getAiMealPlans(
            @Query("user_id") String userIdFilter,
            @Query("created_date") String dateFilter
    );

    // ── Gemini (Edge Function proxy; API anahtarı sunucuda) ────────────
    @POST("functions/v1/gemini-proxy")
    Call<JsonObject> generateContent(@Body JsonObject body);
}
