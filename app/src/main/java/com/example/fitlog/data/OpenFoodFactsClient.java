package com.example.fitlog.data;

import com.example.fitlog.NutritionData;
import com.example.fitlog.domain.FoodMath;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Open Food Facts barkod sorgusu (ücretsiz, anahtarsız).
 *
 * Bilinçli olarak Supabase istemcisinden AYRI bir OkHttp istemcisi kullanır:
 * kullanıcının oturum token'ı ya da Supabase anahtarı üçüncü tarafa gitmez.
 */
public class OpenFoodFactsClient {

    private static final String USER_AGENT = "FitLog-Android/1.0";

    private final OkHttpClient http;
    private final HttpUrl baseUrl;

    public OpenFoodFactsClient() {
        this(new OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .build(),
                HttpUrl.get("https://world.openfoodfacts.org/"));
    }

    public OpenFoodFactsClient(OkHttpClient http, HttpUrl baseUrl) {
        this.http = http;
        this.baseUrl = baseUrl;
    }

    /** @return ürün yoksa ya da besin bilgisi eksikse null */
    public NutritionData lookup(String barcode) throws IOException, ApiException {
        if (barcode == null || !barcode.matches("\\d{6,14}")) throw new IllegalArgumentException("barcode");

        HttpUrl url = baseUrl.newBuilder()
                .addPathSegments("api/v2/product")
                .addPathSegment(barcode)
                .addQueryParameter("fields", "product_name,brands,serving_size,nutriments")
                .build();
        Request req = new Request.Builder().url(url).header("User-Agent", USER_AGENT).build();

        try (Response r = http.newCall(req).execute()) {
            if (r.code() == 404) return null;
            if (!r.isSuccessful()) throw new ApiException(r.code(), "Open Food Facts HTTP " + r.code());
            ResponseBody body = r.body();
            if (body == null) return null;
            JsonObject root = JsonParser.parseString(body.string()).getAsJsonObject();
            return FoodMath.parseOpenFoodFacts(root);
        }
    }
}
