package com.example.fitlog;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefManager {
    private static final String PREF_NAME = "AppPrefs";
    private static final String KEY_IID = "user_iid";
    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void setIid(String iid) {
        editor.putString(KEY_IID, iid);
        editor.apply();
    }

    public String getIid() {
        return sharedPreferences.getString(KEY_IID, null);
    }
}
