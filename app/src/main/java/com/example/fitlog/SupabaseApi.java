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
 * Supabase REST (PostgREST), RPC ve Edge Function uç noktaları.
 *
 * "apikey" ve "Authorization: Bearer <kullanıcı JWT>" başlıkları
 * {@link RetrofitClient} interceptor'ı tarafından eklenir. Hangi satırların
 * okunup yazılabileceğini sunucudaki RLS politikaları belirler; buradaki
 * user_id filtreleri yalnızca sorguyu daraltır.
 *
 * Tarih filtreleri {@link com.example.fitlog.domain.DateRanges} ile üretilir
 * ("gte.<utc>", "lt.<utc>"); aynı kolon için birden fazla filtre List ile verilir.
 */
public interface SupabaseApi {

    String PREFER_MINIMAL = "Prefer: return=minimal";
    String PREFER_REPRESENTATION = "Prefer: return=representation";

    // ── Users ──────────────────────────────────────────────────────────
    @GET("rest/v1/users")
    Call<List<User>> getUserByAuthId(@Query("auth_id") String authIdFilter);

    @GET("rest/v1/users")
    Call<List<User>> getUserById(@Query("user_id") String userIdFilter);

    @Headers(PREFER_MINIMAL)
    @PATCH("rest/v1/users")
    Call<Void> updateUser(@Query("user_id") String userIdFilter, @Body Map<String, Object> body);

    // ── Water ──────────────────────────────────────────────────────────
    @Headers(PREFER_REPRESENTATION)
    @POST("rest/v1/water_log")
    Call<List<WaterLog>> insertWaterLog(@Body WaterLog waterLog);

    @GET("rest/v1/water_log?order=consumed_at.asc")
    Call<List<WaterLog>> getWaterLogs(
            @Query("user_id") String userIdFilter,
            @Query("consumed_at") List<String> rangeFilters
    );

    /** Belirli kaydı (geri al) ya da bir günü (sıfırla) siler. */
    @DELETE("rest/v1/water_log")
    Call<Void> deleteWaterLogs(
            @Query("user_id") String userIdFilter,
            @Query("consumed_at") List<String> consumedAtFilters
    );

    // ── Meals ──────────────────────────────────────────────────────────
    @Headers(PREFER_MINIMAL)
    @POST("rest/v1/meal_log")
    Call<Void> insertMealLog(@Body MealInsertDto mealLog);

    @GET("rest/v1/meal_log?order=eaten_at.asc")
    Call<List<MealLog>> getMealLogs(
            @Query("user_id") String userIdFilter,
            @Query("eaten_at") List<String> rangeFilters
    );

    @GET("rest/v1/meal_log?order=eaten_at.desc")
    Call<List<MealLog>> getRecentMeals(
            @Query("user_id") String userIdFilter,
            @Query("limit") int limit
    );

    @DELETE("rest/v1/meal_log")
    Call<Void> deleteMealLog(@Query("log_id") String logIdFilter);

    // ── Weight ─────────────────────────────────────────────────────────
    @GET("rest/v1/weight_log?order=logged_at.desc")
    Call<List<WeightLog>> getWeightLogs(
            @Query("user_id") String userIdFilter,
            @Query("limit") int limit
    );

    @Headers(PREFER_REPRESENTATION)
    @POST("rest/v1/weight_log")
    Call<List<WeightLog>> insertWeightLog(@Body WeightLog log);

    @DELETE("rest/v1/weight_log")
    Call<Void> deleteWeightLog(@Query("id") String idFilter);

    // ── Favorites ──────────────────────────────────────────────────────
    @GET("rest/v1/favorite_meals?order=created_at.desc")
    Call<List<FavoriteMeal>> getFavorites(@Query("user_id") String userIdFilter);

    @Headers(PREFER_REPRESENTATION)
    @POST("rest/v1/favorite_meals")
    Call<List<FavoriteMeal>> insertFavorite(@Body FavoriteMeal meal);

    @DELETE("rest/v1/favorite_meals")
    Call<Void> deleteFavorite(@Query("id") String idFilter);

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

    @GET("rest/v1/ai_meal_plans?order=id.desc")
    Call<List<AiMealPlan>> getAiMealPlans(
            @Query("user_id") String userIdFilter,
            @Query("created_date") String dateFilter
    );

    // ── RPC ────────────────────────────────────────────────────────────
    @POST("rest/v1/rpc/delete_my_account")
    Call<Void> deleteMyAccount(@Body JsonObject emptyBody);

    // ── Gemini (Edge Function proxy; API anahtarı sunucuda) ────────────
    @POST("functions/v1/gemini-proxy")
    Call<JsonObject> generateContent(@Body JsonObject body);
}
