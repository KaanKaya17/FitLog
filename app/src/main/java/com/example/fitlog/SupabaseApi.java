package com.example.fitlog;


import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface SupabaseApi {
    @POST("rest/v1/users")
    Call<Void> insertUser(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearerToken,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer, // "return=minimal" diyerek hız kazanabiliriz
            @Body User user
    );

    @GET("rest/v1/users")
    Call<List<User>> getUserByEmail(
            @Header("apikey") String apiKey,
            @Header("Authorization") String authHeader,
            @Query("email") String emailFilter,
            @Header("Accept") String accept
    );


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
}
