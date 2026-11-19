package com.example.fitlog.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.GeminiNutritionService;
import com.example.fitlog.data.AppExecutors;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AI koçla sohbet. Geçmiş yalnızca bellekte (ViewModel) tutulur; sunucuya
 * kaydedilmez. Her istekte son {@link #MAX_TURNS} mesaj gönderilir.
 */
public class AiChatViewModel extends BaseViewModel {

    public static final int MAX_MESSAGE_LENGTH = 500;
    static final int MAX_TURNS = 20;

    /** Sohbet mesajı. */
    public static final class Message {
        public final boolean fromUser;
        public final String text;
        public Message(boolean fromUser, String text) { this.fromUser = fromUser; this.text = text; }
    }

    /** Sistem talimatı için ekrandan gelen yerelleştirilmiş metinler. */
    public static final class Setup {
        public final String systemPrompt;   // profil dahil, doldurulmuş
        public final String weeklyTemplate;
        public final int calorieGoal, waterGoal;

        public Setup(String systemPrompt, String weeklyTemplate, int calorieGoal, int waterGoal) {
            this.systemPrompt = systemPrompt; this.weeklyTemplate = weeklyTemplate;
            this.calorieGoal = calorieGoal; this.waterGoal = waterGoal;
        }
    }

    private final List<Message> history = new ArrayList<>();
    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>(Collections.emptyList());
    private final MutableLiveData<Boolean> typing = new MutableLiveData<>(false);
    private String systemInstruction;

    public AiChatViewModel(@NonNull Application app) { super(app); }

    public LiveData<List<Message>> chat() { return messages; }
    public LiveData<Boolean> typing() { return typing; }

    public void send(String raw, Setup setup) {
        String text = GeminiNutritionService.sanitizeMultiline(raw, MAX_MESSAGE_LENGTH);
        if (text.isEmpty() || Boolean.TRUE.equals(typing.getValue())) return;
        history.add(new Message(true, text));
        messages.setValue(new ArrayList<>(history));
        typing.setValue(true);

        List<Message> snapshot = new ArrayList<>(history);
        AppExecutors.io().execute(() -> {
            try {
                if (systemInstruction == null) systemInstruction = buildSystem(setup);
                String reply = GeminiNutritionService.extractText(repo.generate(request(systemInstruction, snapshot)));
                AppExecutors.main(() -> {
                    history.add(new Message(false, reply.trim()));
                    messages.setValue(new ArrayList<>(history));
                    typing.setValue(false);
                });
            } catch (Exception e) {
                AppExecutors.main(() -> {
                    // Gönderilemeyen kullanıcı mesajını geri çek ki tekrar denenebilsin.
                    if (!history.isEmpty() && history.get(history.size() - 1).fromUser) history.remove(history.size() - 1);
                    messages.setValue(new ArrayList<>(history));
                    typing.setValue(false);
                });
                postError(e);
            }
        });
    }

    private String buildSystem(Setup s) {
        String weekly = "";
        try {
            weekly = CoachContext.load(repo, LocalDate.now(), ZoneId.systemDefault())
                    .format(s.weeklyTemplate, s.calorieGoal, s.waterGoal);
        } catch (Exception ignored) {}
        return s.systemPrompt + "\n\n" + weekly;
    }

    static JsonObject request(String system, List<Message> history) {
        JsonArray contents = new JsonArray();
        int start = Math.max(0, history.size() - MAX_TURNS);
        for (int i = start; i < history.size(); i++) {
            Message m = history.get(i);
            JsonObject part = new JsonObject();
            part.addProperty("text", m.text);
            JsonArray parts = new JsonArray();
            parts.add(part);
            JsonObject c = new JsonObject();
            c.addProperty("role", m.fromUser ? "user" : "model");
            c.add("parts", parts);
            contents.add(c);
        }
        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", system);
        JsonArray sysParts = new JsonArray();
        sysParts.add(sysPart);
        JsonObject sys = new JsonObject();
        sys.add("parts", sysParts);

        JsonObject body = new JsonObject();
        body.add("systemInstruction", sys);
        body.add("contents", contents);
        JsonObject cfg = new JsonObject();
        cfg.addProperty("temperature", 0.7);
        body.add("generationConfig", cfg);
        return body;
    }
}
