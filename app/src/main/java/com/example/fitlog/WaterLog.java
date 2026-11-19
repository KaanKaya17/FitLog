package com.example.fitlog;

public class WaterLog {
    /** Sunucu atar; Gson null alanları göndermediği için eklemede boş kalır. */
    private Long log_id;
    private int user_id;
    private int amount_ml;

    private String consumed_at;

    public WaterLog() {}

    public WaterLog(int user_id, int amount_ml) {
        this.user_id = user_id;
        this.amount_ml = amount_ml;
    }

    public Long getLog_id()          { return log_id; }
    public int getUser_id()          { return user_id; }
    public void setUser_id(int id)   { this.user_id = id; }

    public int getAmount_ml()               { return amount_ml; }
    public void setAmount_ml(int amount_ml) { this.amount_ml = amount_ml; }

    public String getConsumed_at() { return consumed_at; }
    public void setConsumed_at(String consumed_at) { this.consumed_at = consumed_at; }
}