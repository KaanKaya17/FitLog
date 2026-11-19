package com.example.fitlog;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Supabase için tek Retrofit istemcisi.
 *
 * Her isteğe otomatik olarak:
 *   apikey: <anon key>
 *   Authorization: Bearer <kullanıcının access token'ı>
 * eklenir. 401 alınırsa token bir kez yenilenip istek tekrarlanır.
 *
 * Anon key herkese açık bir anahtardır; asıl yetkilendirme kullanıcı JWT'si
 * ve sunucudaki RLS politikalarıyla yapılır.
 */
public final class RetrofitClient {

    private static final String BASE_URL = BuildConfig.SUPABASE_URL + "/";
    private static final String HEADER_RETRIED = "X-FitLog-Retried";

    private static Context appContext;
    private static Retrofit retrofit;
    private static Retrofit authRetrofit;

    private RetrofitClient() {}

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /** REST + Edge Function istemcisi (kullanıcı JWT'si ile). */
    public static synchronized Retrofit getClient() {
        if (retrofit == null) {
            if (appContext == null) throw new IllegalStateException("RetrofitClient.init() çağrılmadı");

            OkHttpClient client = baseHttpClient()
                    .addInterceptor(chain -> {
                        String token = AuthManager.getValidAccessToken(appContext);
                        Request req = chain.request().newBuilder()
                                .header("apikey", BuildConfig.SUPABASE_API_KEY)
                                .header("Authorization", "Bearer " +
                                        (token != null ? token : BuildConfig.SUPABASE_API_KEY))
                                .build();
                        return chain.proceed(req);
                    })
                    .authenticator((route, response) -> {
                        // Token sunucu tarafında geçersizse bir kez yenileyip tekrar dene.
                        if (response.request().header(HEADER_RETRIED) != null) return null;
                        String sent = response.request().header("Authorization");
                        String stale = sent != null ? sent.replaceFirst("^Bearer ", "") : null;
                        String fresh = AuthManager.refreshSession(appContext, stale);
                        if (fresh == null) return null;
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + fresh)
                                .header(HEADER_RETRIED, "1")
                                .build();
                    })
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    /** Auth uç noktaları için istemci (yalnızca apikey; token yenileme döngüsüne girmez). */
    static synchronized Retrofit getAuthClient() {
        if (authRetrofit == null) {
            OkHttpClient client = baseHttpClient()
                    .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                            .header("apikey", BuildConfig.SUPABASE_API_KEY)
                            .build()))
                    .build();

            authRetrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return authRetrofit;
    }

    private static OkHttpClient.Builder baseHttpClient() {
        // Gemini analizleri (özellikle fotoğraf) uzun sürebilir.
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS);
    }
}
