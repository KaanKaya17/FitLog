package com.example.fitlog.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.fitlog.NutritionData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class OpenFoodFactsParseTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    public void prefersPerServingValues() {
        NutritionData d = FoodMath.parseOpenFoodFacts(json("{\"status\":1,\"product\":{"
                + "\"product_name\":\"Granola\",\"brands\":\"Acme, Acme Foods\",\"serving_size\":\"40 g\","
                + "\"nutriments\":{\"energy-kcal_serving\":180,\"proteins_serving\":4.1,"
                + "\"carbohydrates_serving\":26.55,\"fat_serving\":6,\"fiber_serving\":3,"
                + "\"energy-kcal_100g\":450}}}"));
        assertEquals("Granola (Acme)", d.foodName);
        assertEquals("40 g", d.servingSize);
        assertEquals(180, d.calories);
        assertEquals(4.1f, d.protein, 0.001);
        assertEquals(26.6f, d.carbohydrates, 0.001);
        assertEquals("high", d.confidence);
    }

    @Test
    public void fallsBackTo100gAndKilojoules() {
        NutritionData d = FoodMath.parseOpenFoodFacts(json("{\"status\":1,\"product\":{"
                + "\"product_name\":\"Milk\",\"nutriments\":{\"energy_100g\":272,\"proteins_100g\":3.3}}}"));
        assertEquals("100 g", d.servingSize);
        assertEquals(65, d.calories);       // 272 kJ / 4.184
        assertEquals(3.3f, d.protein, 0.001);
        assertEquals(0f, d.fat, 0.001);
    }

    @Test
    public void brandNotDuplicatedWhenAlreadyInName() {
        NutritionData d = FoodMath.parseOpenFoodFacts(json("{\"status\":1,\"product\":{"
                + "\"product_name\":\"Acme Cola\",\"brands\":\"Acme\",\"nutriments\":{\"energy-kcal_100g\":42}}}"));
        assertEquals("Acme Cola", d.foodName);
    }

    @Test
    public void returnsNullWhenNotFoundOrNoEnergy() {
        assertNull(FoodMath.parseOpenFoodFacts(json("{\"status\":0,\"status_verbose\":\"product not found\"}")));
        assertNull(FoodMath.parseOpenFoodFacts(json("{\"status\":1,\"product\":{\"product_name\":\"X\",\"nutriments\":{}}}")));
        assertNull(FoodMath.parseOpenFoodFacts(null));
    }
}
