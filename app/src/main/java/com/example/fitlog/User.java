package com.example.fitlog;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class User {
    // Veritabanındaki sütun isimleriyle birebir aynı olmalı
    private int user_id;
    private String username;
    private String email;
    private String auth_id;
    private String first_name;
    private String last_name;
    private int daily_calorie_goal;
    private int daily_water_goal_ml;
    private boolean is_blocked;
    private String gender;
    private Float height_cm;
    private Float weight_kg;
    private String goal;
    private Integer birth_year;
    private String activity_level;
    private Float target_weight_kg;
    private Integer daily_protein_goal_g;
    private Integer daily_carbs_goal_g;
    private Integer daily_fat_goal_g;

    // 1. Boş Constructor (GSON'ın nesneyi oluşturabilmesi için gereklidir)
    public User() {}

    // Getter ve Setter Metotları
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAuth_id() { return auth_id; }

    public String getFirst_name() { return first_name; }
    public void setFirst_name(String first_name) { this.first_name = first_name; }

    public String getLast_name() { return last_name; }
    public void setLast_name(String last_name) { this.last_name = last_name; }

    public int getId() { return user_id; }
    public void setId(int id) { this.user_id = id; }

    public int getCalorie_goal() { return daily_calorie_goal; }
    public void setCalorie_goal(int calorie_goal) { this.daily_calorie_goal = calorie_goal; }

    public int getWater_goal() { return daily_water_goal_ml; }
    public void setWater_goal(int water_goal) { this.daily_water_goal_ml = water_goal; }

    public boolean isBlocked() { return is_blocked; }
    public void setBlocked(boolean blocked) { this.is_blocked = blocked; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public Float getHeight_cm() { return height_cm; }
    public void setHeight_cm(Float height_cm) { this.height_cm = height_cm; }

    public Float getWeight_kg() { return weight_kg; }
    public void setWeight_kg(Float weight_kg) { this.weight_kg = weight_kg; }

    public String getGoal() { return goal; }
    public Integer getBirth_year() { return birth_year; }
    public String getActivity_level() { return activity_level; }
    public Float getTarget_weight_kg() { return target_weight_kg; }
    public Integer getDaily_protein_goal_g() { return daily_protein_goal_g; }
    public Integer getDaily_carbs_goal_g() { return daily_carbs_goal_g; }
    public Integer getDaily_fat_goal_g() { return daily_fat_goal_g; }
    public void setGoal(String goal) { this.goal = goal; }
}