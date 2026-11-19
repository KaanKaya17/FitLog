package com.example.fitlog.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.fitlog.FavoriteMeal;
import com.example.fitlog.MealLog;
import com.example.fitlog.NutritionData;
import com.example.fitlog.domain.NutritionCalculator;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** ViewModel'lerin Android'den bağımsız, saf yardımcı mantığı. */
public class ViewModelLogicTest {

    @Test
    public void quickItems_favoritesFirstWithoutDuplicates() {
        NutritionData fav = new NutritionData();
        fav.foodName = "Oatmeal"; fav.calories = 300;
        MealLog recent1 = new MealLog(); recent1.setFood_name("oatmeal");
        MealLog recent2 = new MealLog(); recent2.setFood_name("Banana");

        List<AddMealViewModel.QuickItem> items = AddMealViewModel.merge(
                Collections.singletonList(new FavoriteMeal(1, fav)), Arrays.asList(recent1, recent2));

        assertEquals(2, items.size());
        assertTrue(items.get(0).favorite);
        assertEquals("Oatmeal", items.get(0).data.foodName);
        assertEquals("Banana", items.get(1).data.foodName);
    }

    @Test
    public void suggestTargets_requiresCompleteProfile() {
        assertNull(ProfileViewModel.suggest("", 180, 80, 1995, "moderate", "lose", 2026));
        assertNull(ProfileViewModel.suggest("Male", 180, 80, 0, "moderate", "lose", 2026));
        assertNull(ProfileViewModel.suggest("Male", 180, 80, 2020, "moderate", "lose", 2026)); // 6 yaş
        NutritionCalculator.Targets t = ProfileViewModel.suggest("Male", 180, 80, 1996, "moderate", "maintain", 2026);
        assertNotNull(t);
        assertEquals(2760, t.calories);
    }

    @Test
    public void chatRequest_hasSystemInstructionRolesAndTurnLimit() {
        List<AiChatViewModel.Message> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) history.add(new AiChatViewModel.Message(i % 2 == 0, "m" + i));

        JsonObject body = AiChatViewModel.request("SYSTEM", history);
        JsonArray contents = body.getAsJsonArray("contents");
        assertEquals(AiChatViewModel.MAX_TURNS, contents.size());
        assertEquals("m10", contents.get(0).getAsJsonObject().getAsJsonArray("parts").get(0)
                .getAsJsonObject().get("text").getAsString());
        assertEquals("user", contents.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("model", contents.get(1).getAsJsonObject().get("role").getAsString());
        assertEquals("SYSTEM", body.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
                .get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    public void coachRequest_isSingleUserTurn() {
        JsonObject body = AiCoachViewModel.request("hello");
        assertEquals(1, body.getAsJsonArray("contents").size());
        assertEquals("user", body.getAsJsonArray("contents").get(0).getAsJsonObject().get("role").getAsString());
    }

    @Test
    public void coachContext_formatsTemplateWithRootLocale() {
        CoachContext c = new CoachContext(1850, 92.6f, 1700, 5);
        assertEquals("5|1850|93|1700|2000|2500",
                c.format("%1$d|%2$d|%3$.0f|%4$d|%5$d|%6$d", 2000, 2500));
    }

    @Test
    public void waterSum() {
        assertEquals(750, WaterViewModel.sum(Arrays.asList(
                new com.example.fitlog.WaterLog(1, 250), new com.example.fitlog.WaterLog(1, 500))));
    }
}
