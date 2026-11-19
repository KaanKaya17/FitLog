package com.example.fitlog.data;

import com.example.fitlog.AdminNotification;
import com.example.fitlog.AiMealPlan;
import com.example.fitlog.FavoriteMeal;
import com.example.fitlog.MealInsertDto;
import com.example.fitlog.MealLog;
import com.example.fitlog.NutritionData;
import com.example.fitlog.SupabaseApi;
import com.example.fitlog.User;
import com.example.fitlog.WaterLog;
import com.example.fitlog.WeightLog;
import com.example.fitlog.domain.DateRanges;
import com.example.fitlog.domain.FoodMath;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import retrofit2.Response;

/**
 * Tüm uzak veri erişimi. Metotlar SENKRONDUR (arka plan thread'inde çağrılır);
 * ViewModel'ler bunları {@link AppExecutors} üzerinde çalıştırır.
 *
 * Yeni kayıt ekleme (su, öğün, kilo) ağ hatasında çevrimdışı kuyruğa alınır
 * ve bağlantı gelince {@link SyncWorker} tarafından gönderilir.
 */
public class FitLogRepository {

    public static final int RECENT_MEAL_SCAN = 80;

    private final SupabaseApi api;
    private final IntSupplier userId;
    private final Supplier<ZoneId> zone;
    private final PendingWriteStore offline;
    private final OpenFoodFactsClient foodFacts;
    private final Gson gson = new Gson();

    public FitLogRepository(SupabaseApi api, IntSupplier userId, Supplier<ZoneId> zone,
                            PendingWriteStore offline, OpenFoodFactsClient foodFacts) {
        this.api = api;
        this.userId = userId;
        this.zone = zone;
        this.offline = offline;
        this.foodFacts = foodFacts;
    }

    /** Yazma sonucu: sunucuya kaydedildi ya da çevrimdışı kuyruğa alındı. */
    public static final class WriteResult<T> {
        public final T value;
        public final boolean queued;
        WriteResult(T value, boolean queued) { this.value = value; this.queued = queued; }
    }

    /** Bir tarih aralığının ham verisi. */
    public static final class RangeData {
        public final List<MealLog> meals;
        public final List<WaterLog> water;
        RangeData(List<MealLog> meals, List<WaterLog> water) { this.meals = meals; this.water = water; }
    }

    private String me() {
        int id = userId.getAsInt();
        if (id <= 0) throw new IllegalStateException("not logged in");
        return "eq." + id;
    }

    private static <T> List<T> nonNull(List<T> l) {
        return l != null ? l : Collections.emptyList();
    }

    // ── Profil ─────────────────────────────────────────────────────────

    public User getProfile() throws IOException, ApiException {
        List<User> users = ApiException.bodyOrThrow(api.getUserById(me()).execute());
        return users == null || users.isEmpty() ? null : users.get(0);
    }

    public void updateProfile(Map<String, Object> fields) throws IOException, ApiException {
        ApiException.checkOk(api.updateUser(me(), fields).execute());
    }

    // ── Günlük / aralık verisi ─────────────────────────────────────────

    public RangeData getRange(LocalDate from, LocalDate toInclusive) throws IOException, ApiException {
        List<String> range = DateRanges.rangeFilter(from, toInclusive, zone.get());
        List<MealLog> meals = ApiException.bodyOrThrow(api.getMealLogs(me(), range).execute());
        List<WaterLog> water = ApiException.bodyOrThrow(api.getWaterLogs(me(), range).execute());
        return new RangeData(nonNull(meals), nonNull(water));
    }

    public List<MealLog> getMeals(LocalDate day) throws IOException, ApiException {
        return nonNull(ApiException.bodyOrThrow(
                api.getMealLogs(me(), DateRanges.dayFilter(day, zone.get())).execute()));
    }

    public List<WaterLog> getWater(LocalDate day) throws IOException, ApiException {
        return nonNull(ApiException.bodyOrThrow(
                api.getWaterLogs(me(), DateRanges.dayFilter(day, zone.get())).execute()));
    }

    // ── Su ─────────────────────────────────────────────────────────────

    public WriteResult<WaterLog> addWater(int amountMl) throws IOException, ApiException {
        if (amountMl <= 0 || amountMl > 3000) throw new IllegalArgumentException("amount");
        WaterLog log = new WaterLog(userId.getAsInt(), amountMl);
        try {
            List<WaterLog> saved = ApiException.bodyOrThrow(api.insertWaterLog(log).execute());
            return new WriteResult<>(saved == null || saved.isEmpty() ? log : saved.get(0), false);
        } catch (IOException e) {
            // Çevrimdışı: tüketim zamanını şimdi olarak sabitle ve kuyruğa al.
            log.setConsumed_at(DateRanges.nowUtc());
            offline.enqueue(PendingWriteStore.KIND_WATER, gson.toJson(log));
            return new WriteResult<>(log, true);
        }
    }

    /** Tek bir su kaydını geri alır (zaman damgasıyla eşleştirilir). */
    public void undoWater(WaterLog log) throws IOException, ApiException {
        if (log.getConsumed_at() == null) throw new IllegalArgumentException("consumed_at");
        ApiException.checkOk(api.deleteWaterLogs(me(),
                Collections.singletonList("eq." + log.getConsumed_at())).execute());
    }

    /** O yerel günün tüm su kayıtlarını siler. */
    public void resetWater(LocalDate day) throws IOException, ApiException {
        ApiException.checkOk(api.deleteWaterLogs(me(), DateRanges.dayFilter(day, zone.get())).execute());
    }

    // ── Öğün ───────────────────────────────────────────────────────────

    /**
     * @param day     null ise şimdi; geçmiş bir gün ise o günün öğle saati
     * @param mealType {@link com.example.fitlog.domain.MealType#key}
     */
    public WriteResult<Void> addMeal(NutritionData data, LocalDate day, String mealType)
            throws IOException, ApiException {
        LocalDate today = LocalDate.now(zone.get());
        String eatenAt = (day == null || day.equals(today))
                ? DateRanges.nowUtc() : DateRanges.localNoonUtc(day, zone.get());
        MealInsertDto dto = new MealInsertDto(userId.getAsInt(), data, eatenAt, mealType);
        try {
            ApiException.checkOk(api.insertMealLog(dto).execute());
            return new WriteResult<>(null, false);
        } catch (IOException e) {
            offline.enqueue(PendingWriteStore.KIND_MEAL, gson.toJson(dto));
            return new WriteResult<>(null, true);
        }
    }

    public void deleteMeal(int logId) throws IOException, ApiException {
        ApiException.checkOk(api.deleteMealLog("eq." + logId).execute());
    }

    /** Son yenen farklı yemekler (hızlı ekleme için). */
    public List<MealLog> recentDistinctMeals(int limit) throws IOException, ApiException {
        List<MealLog> recent = nonNull(ApiException.bodyOrThrow(
                api.getRecentMeals(me(), RECENT_MEAL_SCAN).execute()));
        return FoodMath.distinctRecent(recent, limit);
    }

    // ── Favoriler ──────────────────────────────────────────────────────

    public List<FavoriteMeal> getFavorites() throws IOException, ApiException {
        return nonNull(ApiException.bodyOrThrow(api.getFavorites(me()).execute()));
    }

    /** Zaten favoriyse (409) sessizce başarılı sayılır. */
    public void addFavorite(NutritionData data) throws IOException, ApiException {
        Response<List<FavoriteMeal>> r = api.insertFavorite(new FavoriteMeal(userId.getAsInt(), data)).execute();
        if (!r.isSuccessful() && r.code() != 409) throw ApiException.from(r);
    }

    public void deleteFavorite(long id) throws IOException, ApiException {
        ApiException.checkOk(api.deleteFavorite("eq." + id).execute());
    }

    // ── Kilo ───────────────────────────────────────────────────────────

    public List<WeightLog> getWeights(int limit) throws IOException, ApiException {
        return nonNull(ApiException.bodyOrThrow(api.getWeightLogs(me(), limit).execute()));
    }

    /** Kilo kaydı ekler ve profildeki güncel kiloyu günceller. */
    public WriteResult<WeightLog> addWeight(float kg) throws IOException, ApiException {
        if (kg < 20 || kg > 400) throw new IllegalArgumentException("weight");
        WeightLog log = new WeightLog(userId.getAsInt(), kg, DateRanges.nowUtc());
        try {
            List<WeightLog> saved = ApiException.bodyOrThrow(api.insertWeightLog(log).execute());
            Map<String, Object> p = new HashMap<>();
            p.put("weight_kg", kg);
            ApiException.checkOk(api.updateUser(me(), p).execute());
            return new WriteResult<>(saved == null || saved.isEmpty() ? log : saved.get(0), false);
        } catch (IOException e) {
            offline.enqueue(PendingWriteStore.KIND_WEIGHT, gson.toJson(log));
            return new WriteResult<>(log, true);
        }
    }

    public void deleteWeight(long id) throws IOException, ApiException {
        ApiException.checkOk(api.deleteWeightLog("eq." + id).execute());
    }

    // ── Bildirimler ────────────────────────────────────────────────────

    public List<AdminNotification> unreadNotifications() throws IOException, ApiException {
        return nonNull(ApiException.bodyOrThrow(api.getNotifications(me(), "eq.false").execute()));
    }

    public void markNotificationRead(String id) throws IOException, ApiException {
        Map<String, Object> body = new HashMap<>();
        body.put("is_read", true);
        ApiException.checkOk(api.markNotificationAsRead("eq." + id, body).execute());
    }

    // ── AI ─────────────────────────────────────────────────────────────

    public AiMealPlan getTodayPlan() throws IOException, ApiException {
        List<AiMealPlan> plans = ApiException.bodyOrThrow(api.getAiMealPlans(me(),
                "eq." + LocalDate.now(zone.get())).execute());
        return plans == null || plans.isEmpty() ? null : plans.get(0);
    }

    public void saveTodayPlan(String content) throws IOException, ApiException {
        ApiException.checkOk(api.insertAiMealPlan(new AiMealPlan(userId.getAsInt(), content,
                LocalDate.now(zone.get()).toString())).execute());
    }

    public JsonObject generate(JsonObject request) throws IOException, ApiException {
        return ApiException.bodyOrThrow(api.generateContent(request).execute());
    }

    // ── Barkod ─────────────────────────────────────────────────────────

    /** @return ürün bulunamazsa null */
    public NutritionData lookupBarcode(String barcode) throws IOException, ApiException {
        return foodFacts.lookup(barcode);
    }

    // ── Hesap ──────────────────────────────────────────────────────────

    public void deleteAccount() throws IOException, ApiException {
        ApiException.checkOk(api.deleteMyAccount(new JsonObject()).execute());
    }
}
