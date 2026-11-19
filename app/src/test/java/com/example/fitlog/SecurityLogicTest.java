package com.example.fitlog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

public class SecurityLogicTest {

    // ── Validator ──────────────────────────────────────────────────────

    @Test
    public void password_requiresLengthUpperLowerDigit() {
        assertTrue(Validator.isValidPassword("Abcdefg1"));
        assertFalse(Validator.isValidPassword("Abcdef1"));      // 7 karakter
        assertFalse(Validator.isValidPassword("abcdefg1"));     // büyük harf yok
        assertFalse(Validator.isValidPassword("ABCDEFG1"));     // küçük harf yok
        assertFalse(Validator.isValidPassword("Abcdefgh"));     // rakam yok
        assertFalse(Validator.isValidPassword(null));
    }

    @Test
    public void password_rejectsOverBcryptLimit() {
        StringBuilder sb = new StringBuilder("Aa1");
        while (sb.length() < 73) sb.append('x');
        assertFalse(Validator.isValidPassword(sb.toString()));
    }

    @Test
    public void userName_rules() {
        assertTrue(Validator.isValidUserName("kaan_17"));
        assertTrue(Validator.isValidUserName("a.b"));
        assertFalse(Validator.isValidUserName("ab"));
        assertFalse(Validator.isValidUserName("with space"));
        assertFalse(Validator.isValidUserName("eq.1&or=(x)"));
        assertFalse(Validator.isValidUserName(null));
    }

    // ── Gemini girdi temizleme ─────────────────────────────────────────

    @Test
    public void sanitize_stripsControlCharsAndTrims() {
        assertEquals("pizza  ignore", GeminiNutritionService.sanitize("  pizza\n\nignore "));
        assertEquals("", GeminiNutritionService.sanitize(null));
    }

    @Test
    public void sanitize_limitsLength() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) sb.append('a');
        assertEquals(GeminiNutritionService.MAX_DESCRIPTION_LENGTH,
                GeminiNutritionService.sanitize(sb.toString()).length());
    }

    @Test
    public void extractText_readsFirstCandidate() {
        JsonObject root = JsonParser.parseString(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"hello\"}]}}]}").getAsJsonObject();
        assertEquals("hello", GeminiNutritionService.extractText(root));
    }
}
