package com.example.fitlog;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Properties;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiNutritionService {

    private final String apiKey;
    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                    "gemini-2.5-flash:generateContent?key=";

    public GeminiNutritionService(Context context) {
        this.apiKey = loadApiKey(context);
    }

    public String getApiKey() {
        return apiKey;
    }

    private String loadApiKey(Context context) {
        try {
            InputStream is = context.getAssets().open("config.properties");
            Properties props = new Properties();
            props.load(is);
            return props.getProperty("GEMINI_API_KEY");
        } catch (Exception e) {
            return "";
        }
    }

    public void analyzeTextFood(String foodDescription, NutritionCallback callback) {
        new Thread(() -> {
            try {
                String prompt = "You are a professional nutritionist. Provide the nutritional values for \"" + foodDescription + "\" " +
                        "EXACTLY in this JSON format, do not write any other text or explanation:\n" +
                        "{\n" +
                        "  \"food_name\": \"name of the food\",\n" +
                        "  \"serving_size\": \"serving amount (e.g., 1 portion or 100g)\",\n" +
                        "  \"calories\": 0,\n" +
                        "  \"protein\": 0.0,\n" +
                        "  \"carbohydrates\": 0.0,\n" +
                        "  \"fat\": 0.0,\n" +
                        "  \"fiber\": 0.0,\n" +
                        "  \"confidence\": \"high/medium/low\"\n" +
                        "}";

                JSONObject textPart = new JSONObject();
                textPart.put("text", prompt);

                JSONArray parts = new JSONArray();
                parts.put(textPart);

                JSONObject content = new JSONObject();
                content.put("parts", parts);

                JSONObject genConfig = new JSONObject();
                genConfig.put("response_mime_type", "application/json");

                JSONObject body = new JSONObject();
                body.put("contents", new JSONArray().put(content));
                body.put("generationConfig", genConfig);

                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                RequestBody requestBody = RequestBody.create(
                        body.toString(),
                        MediaType.get("application/json")
                );

                Request request = new Request.Builder()
                        .url(BASE_URL + apiKey)
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.body() == null) throw new Exception("Sunucudan boş yanıt alındı.");
                String responseString = response.body().string();

                android.util.Log.d("GEMINI_RAW", responseString);

                NutritionData data = parseResponse(responseString);
                callback.onSuccess(data);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

    private NutritionData parseResponse(String jsonResponse) throws Exception {
        JSONObject root = new JSONObject(jsonResponse);

        if (root.has("error")) {
            throw new Exception("API Hatası: " +
                    root.getJSONObject("error").getString("message"));
        }

        String text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text");

        JSONObject data = new JSONObject(text);
        NutritionData result = new NutritionData();
        result.foodName      = data.getString("food_name");
        result.servingSize   = data.getString("serving_size");
        result.calories      = data.getInt("calories");
        result.protein       = (float) data.getDouble("protein");
        result.carbohydrates = (float) data.getDouble("carbohydrates");
        result.fat           = (float) data.getDouble("fat");
        result.fiber         = (float) data.getDouble("fiber");
        result.confidence    = data.optString("confidence", "medium");
        return result;
    }
}
