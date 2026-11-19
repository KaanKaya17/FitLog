package com.example.fitlog.domain;

import com.example.fitlog.MealLog;
import com.example.fitlog.NutritionData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Besin verisi dönüşümleri (porsiyon ölçekleme, Open Food Facts, son yenenler). */
public final class FoodMath {

    private FoodMath() {}

    // ── Porsiyon ───────────────────────────────────────────────────────

    /** Değerleri çarpanla ölçekler (ör. 0.5 = yarım porsiyon). Yeni nesne döner. */
    public static NutritionData scale(NutritionData src, double factor) {
        if (factor <= 0) throw new IllegalArgumentException("factor");
        NutritionData d = copy(src);
        d.calories      = (int) Math.round(src.calories * factor);
        d.protein       = round1(src.protein * factor);
        d.carbohydrates = round1(src.carbohydrates * factor);
        d.fat           = round1(src.fat * factor);
        d.fiber         = round1(src.fiber * factor);
        if (factor != 1.0 && src.servingSize != null) {
            d.servingSize = formatFactor(factor) + " × " + src.servingSize;
        }
        return d;
    }

    public static NutritionData copy(NutritionData s) {
        NutritionData d = new NutritionData();
        d.foodName = s.foodName; d.servingSize = s.servingSize; d.calories = s.calories;
        d.protein = s.protein; d.carbohydrates = s.carbohydrates; d.fat = s.fat;
        d.fiber = s.fiber; d.confidence = s.confidence;
        return d;
    }

    public static NutritionData fromMeal(MealLog m) {
        NutritionData d = new NutritionData();
        d.foodName = m.getFood_name(); d.servingSize = m.getServing_size(); d.calories = m.getCalories();
        d.protein = m.getProtein(); d.carbohydrates = m.getCarbohydrates(); d.fat = m.getFat();
        d.fiber = m.getFiber(); d.confidence = m.getConfidence();
        return d;
    }

    /** Makrolardan hesaplanan kalori ile girilen kalori çok farklı mı? (kullanıcı uyarısı için) */
    public static boolean isInconsistent(NutritionData d) {
        double fromMacros = d.protein * 4 + d.carbohydrates * 4 + d.fat * 9;
        if (d.calories == 0) return fromMacros > 50;
        return Math.abs(fromMacros - d.calories) / d.calories > 0.35;
    }

    static String formatFactor(double f) {
        if (f == Math.rint(f)) return String.valueOf((int) f);
        return String.format(Locale.ROOT, "%.1f", f);
    }

    private static float round1(double v) {
        return Math.round(v * 10) / 10f;
    }

    // ── Open Food Facts ────────────────────────────────────────────────

    /**
     * Open Food Facts v2 ürün yanıtını çözümler. Porsiyon değeri varsa porsiyon,
     * yoksa 100 g başına değer kullanılır.
     * @return ürün bulunamazsa ya da kalori bilgisi yoksa null
     */
    public static NutritionData parseOpenFoodFacts(JsonObject root) {
        if (root == null || !root.has("product") || num(root, "status", 0) != 1) return null;
        JsonObject p = root.getAsJsonObject("product");
        JsonObject n = p.has("nutriments") && p.get("nutriments").isJsonObject()
                ? p.getAsJsonObject("nutriments") : new JsonObject();

        boolean perServing = n.has("energy-kcal_serving") && str(p, "serving_size") != null;
        String suffix = perServing ? "_serving" : "_100g";

        double kcal = num(n, "energy-kcal" + suffix, -1);
        if (kcal < 0) {
            double kj = num(n, "energy" + suffix, -1);
            if (kj < 0) return null;
            kcal = kj / 4.184;
        }

        NutritionData d = new NutritionData();
        String name = str(p, "product_name");
        String brand = str(p, "brands");
        if (name == null) name = brand != null ? brand : "?";
        else if (brand != null && !name.toLowerCase(Locale.ROOT).contains(brand.toLowerCase(Locale.ROOT).split(",")[0])) {
            name = name + " (" + brand.split(",")[0].trim() + ")";
        }
        d.foodName = name;
        d.servingSize = perServing ? str(p, "serving_size") : "100 g";
        d.calories = (int) Math.round(kcal);
        d.protein = round1(num(n, "proteins" + suffix, 0));
        d.carbohydrates = round1(num(n, "carbohydrates" + suffix, 0));
        d.fat = round1(num(n, "fat" + suffix, 0));
        d.fiber = round1(num(n, "fiber" + suffix, 0));
        d.confidence = "high";
        return d;
    }

    private static double num(JsonObject o, String key, double def) {
        try {
            JsonElement e = o.get(key);
            if (e == null || e.isJsonNull()) return def;
            return e.getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        String s = e.getAsString().trim();
        return s.isEmpty() ? null : s;
    }

    // ── Son yenenler ───────────────────────────────────────────────────

    /** Kayıtları (en yeni önce verilmiş) yemek adına göre tekilleştirir. */
    public static List<MealLog> distinctRecent(List<MealLog> newestFirst, int limit) {
        List<MealLog> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (MealLog m : newestFirst) {
            if (m.getFood_name() == null) continue;
            String key = m.getFood_name().trim().toLowerCase(Locale.ROOT);
            if (key.isEmpty() || !seen.add(key)) continue;
            out.add(m);
            if (out.size() >= limit) break;
        }
        return out;
    }
}
