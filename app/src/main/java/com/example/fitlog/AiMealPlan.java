package com.example.fitlog;

public class AiMealPlan {
    private int id;
    private int user_id;
    private String plan_content;
    private String created_date;

    public AiMealPlan(int user_id, String plan_content) {
        this.user_id = user_id;
        this.plan_content = plan_content;
    }

    /** created_date kullanıcının yerel tarihidir (sunucunun UTC günü değil). */
    public AiMealPlan(int user_id, String plan_content, String createdDate) {
        this(user_id, plan_content);
        this.created_date = createdDate;
    }

    public int getId() {
        return id;
    }

    public int getUser_id() {
        return user_id;
    }

    public String getPlan_content() {
        return plan_content;
    }

    public String getCreated_date() {
        return created_date;
    }
}
