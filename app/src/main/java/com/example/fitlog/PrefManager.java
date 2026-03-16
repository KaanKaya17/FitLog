package com.example.fitlog;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_ID        = "user_id";
    private static final String KEY_USERNAME   = "user_username";
    private static final String KEY_EMAIL      = "user_email";
    private static final String KEY_FIRST_NAME = "user_first_name";
    private static final String KEY_LAST_NAME  = "user_last_name";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    // Tek seferde tüm kullanıcıyı kaydet
    public void saveUser(User user) {
        editor.putInt(KEY_ID, user.getId());
        editor.putString(KEY_USERNAME, user.getUsername());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.putString(KEY_FIRST_NAME, user.getFirst_name());
        editor.putString(KEY_LAST_NAME, user.getLast_name());
        editor.apply();
    }

    // Getterlar
    public int getId()            { return sharedPreferences.getInt(KEY_ID, -1); }
    public String getUsername()   { return sharedPreferences.getString(KEY_USERNAME, ""); }
    public String getEmail()      { return sharedPreferences.getString(KEY_EMAIL, ""); }
    public String getFirstName()  { return sharedPreferences.getString(KEY_FIRST_NAME, ""); }
    public String getLastName()   { return sharedPreferences.getString(KEY_LAST_NAME, ""); }

    // Logout'ta hepsini temizle
    public void clearUser() {
        editor.clear();
        editor.apply();
    }
}
