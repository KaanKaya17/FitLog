package com.example.fitlog;

public class WaterLog {
    private int user_id;
    private int amount_ml;

    public WaterLog() {}

    public WaterLog(int user_id, int amount_ml) {
        this.user_id = user_id;
        this.amount_ml = amount_ml;
    }

    public int getUser_id()          { return user_id; }
    public void setUser_id(int id)   { this.user_id = id; }

    public int getAmount_ml()               { return amount_ml; }
    public void setAmount_ml(int amount_ml) { this.amount_ml = amount_ml; }
}