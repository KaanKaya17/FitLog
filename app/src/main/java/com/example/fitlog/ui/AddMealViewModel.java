package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.FavoriteMeal;
import com.example.fitlog.GeminiNutritionService;
import com.example.fitlog.MealLog;
import com.example.fitlog.NutritionData;
import com.example.fitlog.R;
import com.example.fitlog.domain.FoodMath;
import com.example.fitlog.domain.MealType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Öğün ekleme. Sonuç bir "taban" değer (AI/barkod/favori) ve porsiyon
 * çarpanından oluşur; ekranda gösterilen = taban × çarpan.
 */
public class AddMealViewModel extends BaseViewModel {

    /** Hızlı ekleme öğesi. */
    public static final class QuickItem {
        public final NutritionData data;
        public final boolean favorite;
        public final Long favoriteId;

        QuickItem(NutritionData data, boolean favorite, Long favoriteId) {
            this.data = data; this.favorite = favorite; this.favoriteId = favoriteId;
        }
    }

    static final int RECENT_LIMIT = 12;

    private final MutableLiveData<Boolean> analyzing = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> saving = new MutableLiveData<>(false);
    private final MutableLiveData<NutritionData> result = new MutableLiveData<>();
    private final MutableLiveData<Double> portion = new MutableLiveData<>(1.0);
    private final MutableLiveData<MealType> mealType =
            new MutableLiveData<>(MealType.forHour(LocalTime.now().getHour()));
    private final MutableLiveData<List<QuickItem>> quickItems = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Event<Boolean>> savedEvent = new MutableLiveData<>();

    private NutritionData base;
    private final List<FavoriteMeal> favorites = new ArrayList<>();

    public AddMealViewModel(@NonNull Application app) { super(app); }

    public LiveData<Boolean> analyzing() { return analyzing; }
    public LiveData<Boolean> saving() { return saving; }
    public LiveData<NutritionData> result() { return result; }
    public LiveData<Double> portion() { return portion; }
    public LiveData<MealType> mealType() { return mealType; }
    public LiveData<List<QuickItem>> quickItems() { return quickItems; }
    public LiveData<Event<Boolean>> saved() { return savedEvent; }

    public void setMealType(MealType t) { mealType.setValue(t); }

    // ── Hızlı ekleme ───────────────────────────────────────────────────

    public void loadQuickItems() {
        runSilently(() -> {
            List<FavoriteMeal> favs;
            try { favs = repo.getFavorites(); } catch (Exception e) { favs = new ArrayList<>(); }
            List<MealLog> recent = repo.recentDistinctMeals(RECENT_LIMIT);
            return new Object[]{favs, recent};
        }, arr -> {
            @SuppressWarnings("unchecked") List<FavoriteMeal> favs = (List<FavoriteMeal>) arr[0];
            @SuppressWarnings("unchecked") List<MealLog> recent = (List<MealLog>) arr[1];
            favorites.clear();
            favorites.addAll(favs);
            quickItems.setValue(merge(favs, recent));
        });
    }

    /** Favoriler önce; aynı adlı son yenenler tekrar gösterilmez. */
    static List<QuickItem> merge(List<FavoriteMeal> favs, List<MealLog> recent) {
        List<QuickItem> out = new ArrayList<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (FavoriteMeal f : favs) {
            out.add(new QuickItem(f.toNutrition(), true, f.getId()));
            names.add(f.getFood_name().trim().toLowerCase(Locale.ROOT));
        }
        for (MealLog m : recent) {
            if (names.add(m.getFood_name().trim().toLowerCase(Locale.ROOT))) {
                out.add(new QuickItem(FoodMath.fromMeal(m), false, null));
            }
        }
        return out;
    }

    public void useQuickItem(QuickItem item) {
        setBase(item.data);
    }

    // ── Analiz ─────────────────────────────────────────────────────────

    public void analyzeText(String description) {
        analyze(() -> GeminiNutritionService.parse(repo.generate(GeminiNutritionService.textRequest(description))));
    }

    public void analyzeImage(String base64Jpeg) {
        analyze(() -> GeminiNutritionService.parse(repo.generate(GeminiNutritionService.imageRequest(base64Jpeg))));
    }

    public void lookupBarcode(String barcode) {
        analyzing.setValue(true);
        runSilentlyOrError(() -> repo.lookupBarcode(barcode), data -> {
            analyzing.setValue(false);
            if (data == null) postMessage(R.string.barcode_not_found);
            else setBase(data);
        });
    }

    private void analyze(Task<NutritionData> task) {
        analyzing.setValue(true);
        runSilentlyOrError(task, data -> {
            analyzing.setValue(false);
            if (data.calories == 0 && "low".equalsIgnoreCase(data.confidence)) {
                postMessage(R.string.add_meal_no_food_detected);
                return;
            }
            setBase(data);
        });
    }

    /** run() ile aynı; hata durumunda analiz göstergesini de kapatır. */
    private <T> void runSilentlyOrError(Task<T> task, java.util.function.Consumer<T> onSuccess) {
        com.example.fitlog.data.AppExecutors.io().execute(() -> {
            try {
                T r = task.run();
                com.example.fitlog.data.AppExecutors.main(() -> onSuccess.accept(r));
            } catch (Exception e) {
                analyzing.postValue(false);
                postError(e);
            }
        });
    }

    private void setBase(NutritionData data) {
        base = data;
        portion.setValue(1.0);
        result.setValue(data);
    }

    // ── Düzenleme ──────────────────────────────────────────────────────

    public void setPortion(double factor) {
        if (base == null) return;
        portion.setValue(factor);
        result.setValue(FoodMath.scale(base, factor));
    }

    /** Elle düzenlenen değerler yeni taban olur. */
    public void applyEdit(NutritionData edited) {
        edited.confidence = "high";
        setBase(edited);
    }

    public void clear() {
        base = null;
        result.setValue(null);
        portion.setValue(1.0);
    }

    public boolean isFavorite(NutritionData d) {
        if (d == null || d.foodName == null) return false;
        for (FavoriteMeal f : favorites) {
            if (f.getFood_name().equalsIgnoreCase(d.foodName.trim())) return true;
        }
        return false;
    }

    public void toggleFavorite() {
        NutritionData d = result.getValue();
        if (d == null) return;
        FavoriteMeal existing = null;
        for (FavoriteMeal f : favorites) if (f.getFood_name().equalsIgnoreCase(d.foodName.trim())) existing = f;
        final FavoriteMeal fx = existing;
        run(() -> {
            if (fx != null && fx.getId() != null) repo.deleteFavorite(fx.getId());
            else repo.addFavorite(d);
            return null;
        }, ignored -> {
            postMessage(fx != null ? R.string.favorite_removed : R.string.favorite_added);
            loadQuickItems();
        });
    }

    // ── Kaydetme ───────────────────────────────────────────────────────

    public void save(LocalDate day) {
        NutritionData d = result.getValue();
        MealType t = mealType.getValue();
        if (d == null) return;
        saving.setValue(true);
        com.example.fitlog.data.AppExecutors.io().execute(() -> {
            try {
                boolean queued = repo.addMeal(d, day, t != null ? t.key : null).queued;
                com.example.fitlog.data.AppExecutors.main(() -> {
                    saving.setValue(false);
                    postMessage(queued ? R.string.msg_saved_offline : R.string.meal_saved);
                    clear();
                    savedEvent.setValue(new Event<>(true));
                    loadQuickItems();
                });
            } catch (Exception e) {
                saving.postValue(false);
                postError(e);
            }
        });
    }
}
