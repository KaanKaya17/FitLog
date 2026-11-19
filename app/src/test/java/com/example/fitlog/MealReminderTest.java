package com.example.fitlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.fitlog.domain.MealType;

import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;

public class MealReminderTest {

    private static final ZoneId IST = ZoneId.of("Europe/Istanbul");

    @Test
    public void remindsOnlyWhenMealTypeMissing() {
        assertTrue(MealReminderReceiver.shouldRemind(MealType.BREAKFAST, Collections.emptyList()));
        assertTrue(MealReminderReceiver.shouldRemind(MealType.LUNCH, Arrays.asList("breakfast", "snack", null)));
        assertFalse(MealReminderReceiver.shouldRemind(MealType.LUNCH, Arrays.asList("breakfast", "LUNCH")));
    }

    @Test
    public void nextTrigger_laterTodayOrTomorrow() {
        ZonedDateTime morning = ZonedDateTime.of(2026, 11, 19, 8, 30, 0, 0, IST);
        assertEquals(ZonedDateTime.of(2026, 11, 19, 10, 0, 0, 0, IST), MealReminderReceiver.nextTrigger(morning, 10));

        ZonedDateTime evening = ZonedDateTime.of(2026, 11, 19, 21, 0, 0, 0, IST);
        assertEquals(ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, IST), MealReminderReceiver.nextTrigger(evening, 10));

        // Tam saatinde: bir sonraki gün (aynı an iki kez tetiklenmesin).
        ZonedDateTime exact = ZonedDateTime.of(2026, 11, 19, 14, 0, 0, 0, IST);
        assertEquals(ZonedDateTime.of(2026, 11, 20, 14, 0, 0, 0, IST), MealReminderReceiver.nextTrigger(exact, 14));
    }

    @Test
    public void scheduleCoversMainMeals() {
        assertEquals(MealReminderReceiver.MEALS.length, MealReminderReceiver.HOURS.length);
        assertEquals(MealType.BREAKFAST, MealReminderReceiver.MEALS[0]);
    }
}
