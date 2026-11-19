package com.example.fitlog.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.fitlog.MealInsertDto;
import com.example.fitlog.NutritionData;
import com.example.fitlog.SupabaseApi;
import com.example.fitlog.WaterLog;
import com.example.fitlog.WeightLog;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class FitLogRepositoryTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private MockWebServer server;
    private FitLogRepository repo;
    private final List<String[]> queued = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        OkHttpClient http = new OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build();
        SupabaseApi api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build().create(SupabaseApi.class);
        repo = new FitLogRepository(api, () -> 7, () -> ISTANBUL,
                (kind, json) -> queued.add(new String[]{kind, json}),
                new OpenFoodFactsClient(http, server.url("/")));
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static String decoded(RecordedRequest r) {
        return URLDecoder.decode(r.getPath(), StandardCharsets.UTF_8);
    }

    @Test
    public void getRange_sendsUserAndUtcRangeFilters() throws Exception {
        server.enqueue(new MockResponse().setBody("[{\"food_name\":\"a\",\"calories\":100}]"));
        server.enqueue(new MockResponse().setBody("[{\"amount_ml\":250}]"));

        FitLogRepository.RangeData d = repo.getRange(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 19));
        assertEquals(1, d.meals.size());
        assertEquals(250, d.water.get(0).getAmount_ml());

        String meals = decoded(server.takeRequest());
        assertTrue(meals, meals.startsWith("/rest/v1/meal_log?order=eaten_at.asc"));
        assertTrue(meals.contains("user_id=eq.7"));
        assertTrue(meals.contains("eaten_at=gte.2026-11-12T21:00:00Z"));
        assertTrue(meals.contains("eaten_at=lt.2026-11-19T21:00:00Z"));

        String water = decoded(server.takeRequest());
        assertTrue(water.contains("consumed_at=gte.2026-11-12T21:00:00Z"));
    }

    @Test
    public void addWater_returnsSavedRowForUndo() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("[{\"user_id\":7,\"amount_ml\":300,\"consumed_at\":\"2026-11-19T10:00:00.5+00:00\"}]"));

        FitLogRepository.WriteResult<WaterLog> r = repo.addWater(300);
        assertFalse(r.queued);
        assertEquals("2026-11-19T10:00:00.5+00:00", r.value.getConsumed_at());

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("return=representation", req.getHeader("Prefer"));
        WaterLog sent = new Gson().fromJson(req.getBody().readUtf8(), WaterLog.class);
        assertEquals(7, sent.getUser_id());
        assertEquals(300, sent.getAmount_ml());
    }

    @Test
    public void undoWater_encodesPlusInTimestamp() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        WaterLog log = new WaterLog(7, 300);
        log.setConsumed_at("2026-11-19T10:00:00.5+00:00");

        repo.undoWater(log);

        RecordedRequest req = server.takeRequest();
        assertEquals("DELETE", req.getMethod());
        // '+' ham bırakılırsa PostgREST onu boşluk olarak okur ve hiçbir satırı eşleştiremez.
        assertTrue(req.getPath(), req.getPath().contains("consumed_at=eq.2026-11-19T10%3A00%3A00.5%2B00%3A00")
                || req.getPath().contains("consumed_at=eq.2026-11-19T10:00:00.5%2B00:00"));
        assertTrue(req.getPath().contains("user_id=eq.7"));
    }

    @Test
    public void undoWater_prefersPrimaryKey() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201)
                .setBody("[{\"log_id\":42,\"user_id\":7,\"amount_ml\":200,\"consumed_at\":\"2026-11-19T10:00:00+00:00\"}]"));
        server.enqueue(new MockResponse().setResponseCode(204));

        WaterLog saved = repo.addWater(200).value;
        assertFalse("ekleme isteği log_id göndermemeli (identity ALWAYS)",
                server.takeRequest().getBody().readUtf8().contains("log_id"));

        repo.undoWater(saved);
        RecordedRequest del = server.takeRequest();
        assertEquals("/rest/v1/water_log?log_id=eq.42", del.getPath());
    }

    @Test
    public void addWater_offlineIsQueued() throws Exception {
        server.shutdown(); // bağlantı reddedilir → IOException

        FitLogRepository.WriteResult<WaterLog> r = repo.addWater(200);
        assertTrue(r.queued);
        assertEquals(1, queued.size());
        assertEquals(PendingWriteStore.KIND_WATER, queued.get(0)[0]);
        WaterLog q = new Gson().fromJson(queued.get(0)[1], WaterLog.class);
        assertEquals(200, q.getAmount_ml());
        assertNotNull("çevrimdışı kayıt gerçek tüketim zamanını korumalı", q.getConsumed_at());
    }

    @Test(expected = IllegalArgumentException.class)
    public void addWater_rejectsAbsurdAmounts() throws Exception {
        repo.addWater(5000);
    }

    @Test
    public void addMeal_pastDayUsesLocalNoonAndMealType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201));
        NutritionData d = new NutritionData();
        d.foodName = "Soup"; d.calories = 150;

        assertFalse(repo.addMeal(d, LocalDate.of(2020, 1, 1), "lunch").queued);

        MealInsertDto sent = new Gson().fromJson(server.takeRequest().getBody().readUtf8(), MealInsertDto.class);
        assertEquals("2020-01-01T09:00:00Z", sent.getEaten_at());
        assertEquals("lunch", sent.getMeal_type());
        assertEquals(7, sent.getUser_id());
    }

    @Test
    public void addMeal_serverErrorIsNotQueued() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"message\":\"rls\"}"));
        NutritionData d = new NutritionData();
        d.foodName = "x";
        try {
            repo.addMeal(d, null, null);
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.FORBIDDEN, e.kind);
            assertTrue(queued.isEmpty());
        }
    }

    @Test
    public void addWeight_insertsAndUpdatesProfile() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("[{\"id\":5,\"weight_kg\":78.4}]"));
        server.enqueue(new MockResponse().setResponseCode(204));

        FitLogRepository.WriteResult<WeightLog> r = repo.addWeight(78.4f);
        assertEquals(Long.valueOf(5), r.value.getId());

        assertEquals("/rest/v1/weight_log", server.takeRequest().getPath());
        RecordedRequest patch = server.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertTrue(patch.getPath().contains("user_id=eq.7"));
        assertTrue(patch.getBody().readUtf8().contains("\"weight_kg\":78.4"));
    }

    @Test
    public void addFavorite_conflictMeansAlreadyFavorite() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(409).setBody("{}"));
        NutritionData d = new NutritionData();
        d.foodName = "Oats";
        repo.addFavorite(d); // istisna fırlatmamalı
    }

    @Test
    public void deleteAccount_callsRpc() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(204));
        repo.deleteAccount();
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/rest/v1/rpc/delete_my_account", req.getPath());
    }

    @Test
    public void quotaErrorFromProxyIsTyped() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"error\":{\"message\":\"Daily AI limit reached\",\"code\":\"quota_exceeded\"}}"));
        try {
            repo.generate(new com.google.gson.JsonObject());
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.Kind.QUOTA, e.kind);
            assertEquals("/functions/v1/gemini-proxy", server.takeRequest().getPath());
        }
    }

    @Test(expected = IllegalStateException.class)
    public void notLoggedIn_failsFast() throws Exception {
        FitLogRepository anon = new FitLogRepository(null, () -> -1, () -> ISTANBUL, (k, j) -> {}, null);
        anon.getMeals(LocalDate.now());
    }

    // ── Çevrimdışı okuma önbelleği ─────────────────────────────────────

    @Test
    public void range_fallsBackToCacheWhenOffline() throws Exception {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        ReadCache mem = new ReadCache() {
            @Override public void put(String k, String j) { store.put(k, j); }
            @Override public String get(String k) { return store.get(k); }
        };
        OkHttpClient http = new OkHttpClient.Builder().readTimeout(2, TimeUnit.SECONDS).build();
        SupabaseApi api = new Retrofit.Builder().baseUrl(server.url("/")).client(http)
                .addConverterFactory(GsonConverterFactory.create()).build().create(SupabaseApi.class);
        FitLogRepository cachedRepo = new FitLogRepository(api, () -> 7, () -> ISTANBUL, (k, j) -> {}, null, mem);

        server.enqueue(new MockResponse().setBody("[{\"food_name\":\"a\",\"calories\":420}]"));
        server.enqueue(new MockResponse().setBody("[{\"amount_ml\":500}]"));
        LocalDate d = LocalDate.of(2026, 11, 19);
        FitLogRepository.RangeData online = cachedRepo.getRange(d.minusDays(6), d);
        assertFalse(online.stale);
        assertTrue(store.containsKey("u7:range7:meals"));

        server.shutdown();
        FitLogRepository.RangeData offline = cachedRepo.getRange(d.minusDays(5), d.plusDays(1)); // ertesi gün, aynı uzunluk
        assertTrue(offline.stale);
        assertEquals(420, offline.meals.get(0).getCalories());
        assertEquals(500, offline.water.get(0).getAmount_ml());
    }

    @Test(expected = IOException.class)
    public void range_offlineWithoutCacheStillFails() throws Exception {
        server.shutdown();
        repo.getRange(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 19));
    }

    @Test
    public void cache_isPerUser() throws Exception {
        java.util.Map<String, String> store = new java.util.HashMap<>();
        ReadCache mem = new ReadCache() {
            @Override public void put(String k, String j) { store.put(k, j); }
            @Override public String get(String k) { return store.get(k); }
        };
        store.put("u99:weights:365", "[{\"weight_kg\":50}]"); // başka kullanıcının verisi
        server.shutdown();
        FitLogRepository other = new FitLogRepository(new Retrofit.Builder().baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(SupabaseApi.class),
                () -> 7, () -> ISTANBUL, (k, j) -> {}, null, mem);
        try {
            other.getWeights(365);
            fail("u7, u99'un önbelleğini görmemeli");
        } catch (IOException expected) {
            // beklenen
        }
    }

    // ── Open Food Facts ────────────────────────────────────────────────

    @Test
    public void barcode_sendsUserAgentAndNoCredentials() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"status\":1,\"product\":{\"product_name\":\"Ayran\","
                + "\"nutriments\":{\"energy-kcal_100g\":38}}}"));

        NutritionData d = repo.lookupBarcode("8690504000000");
        assertEquals(38, d.calories);

        RecordedRequest req = server.takeRequest();
        assertTrue(req.getPath().startsWith("/api/v2/product/8690504000000?fields="));
        assertEquals("FitLog-Android/1.0", req.getHeader("User-Agent"));
        assertNull("Supabase token üçüncü tarafa gitmemeli", req.getHeader("Authorization"));
        assertNull(req.getHeader("apikey"));
    }

    @Test
    public void barcode_notFound() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));
        assertNull(repo.lookupBarcode("1234567890123"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void barcode_rejectsNonNumeric() throws Exception {
        repo.lookupBarcode("../../etc");
    }
}
