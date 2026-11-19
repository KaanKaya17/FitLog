package com.example.fitlog;

/** weight_log tablosu satırı. */
public class WeightLog {
    private Long id;
    private int user_id;
    private float weight_kg;
    private String logged_at;

    public WeightLog() {}

    public WeightLog(int userId, float weightKg, String loggedAt) {
        this.user_id = userId;
        this.weight_kg = weightKg;
        this.logged_at = loggedAt;
    }

    public Long getId() { return id; }
    public int getUser_id() { return user_id; }
    public float getWeight_kg() { return weight_kg; }
    public String getLogged_at() { return logged_at; }
}
