package com.example.fitlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class PrefManager {
    private static final String TAG = "PrefManager";
    private static final String PREF_NAME = "AppPrefs";
    private static final String ENCRYPTED_PREF_NAME = "AppPrefsEncrypted";
    private static final String KEY_MIGRATION_DONE = "migration_done";

    private static final String KEY_ID        = "user_id";
    private static final String KEY_USERNAME   = "user_username";
    private static final String KEY_EMAIL      = "user_email";
    private static final String KEY_FIRST_NAME = "user_first_name";
    private static final String KEY_LAST_NAME  = "user_last_name";
    private static final String KEY_CALORIE_GOAL = "calorie_goal";
    private static final String KEY_WATER_GOAL   = "water_goal";
    private static final String KEY_LANGUAGE     = "app_language";
    private static final String KEY_WATER_REMINDER = "water_reminder_enabled";
    private static final String KEY_GENDER     = "user_gender";
    private static final String KEY_HEIGHT     = "user_height";
    private static final String KEY_WEIGHT     = "user_weight";
    private static final String KEY_GOAL       = "user_goal";
    private static final String KEY_FCM_TOKEN  = "fcm_token";

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            sharedPreferences = EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            editor = sharedPreferences.edit();

            // Eski şifrelenmemiş verilerden göç ettir
            migrateFromOldPrefs(context);

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "EncryptedSharedPreferences oluşturulamadı, normal prefs'e fallback.", e);
            // Fallback: Şifreli prefs oluşturulamazsa normal prefs kullan
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            editor = sharedPreferences.edit();
        }
    }

    /**
     * Eski şifrelenmemiş SharedPreferences'tan verileri şifreli olana taşır.
     * Taşıma işlemi sadece bir kez yapılır.
     */
    private void migrateFromOldPrefs(Context context) {
        if (sharedPreferences.getBoolean(KEY_MIGRATION_DONE, false)) {
            return; // Zaten göç ettirilmiş
        }

        SharedPreferences oldPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        // Eski prefs'te veri var mı kontrol et
        if (oldPrefs.contains(KEY_ID)) {
            Log.d(TAG, "Eski SharedPreferences'tan şifreli prefs'e göç ettiriliyor...");

            editor.putInt(KEY_ID, oldPrefs.getInt(KEY_ID, -1));
            editor.putString(KEY_USERNAME, oldPrefs.getString(KEY_USERNAME, ""));
            editor.putString(KEY_EMAIL, oldPrefs.getString(KEY_EMAIL, ""));
            editor.putString(KEY_FIRST_NAME, oldPrefs.getString(KEY_FIRST_NAME, ""));
            editor.putString(KEY_LAST_NAME, oldPrefs.getString(KEY_LAST_NAME, ""));
            editor.putInt(KEY_CALORIE_GOAL, oldPrefs.getInt(KEY_CALORIE_GOAL, 2000));
            editor.putInt(KEY_WATER_GOAL, oldPrefs.getInt(KEY_WATER_GOAL, 2000));
            editor.putString(KEY_LANGUAGE, oldPrefs.getString(KEY_LANGUAGE, "en"));
            editor.putBoolean(KEY_WATER_REMINDER, oldPrefs.getBoolean(KEY_WATER_REMINDER, false));
            editor.putString(KEY_GENDER, oldPrefs.getString(KEY_GENDER, ""));
            editor.putFloat(KEY_HEIGHT, oldPrefs.getFloat(KEY_HEIGHT, 0f));
            editor.putFloat(KEY_WEIGHT, oldPrefs.getFloat(KEY_WEIGHT, 0f));
            editor.putString(KEY_GOAL, oldPrefs.getString(KEY_GOAL, ""));
            editor.putString(KEY_FCM_TOKEN, oldPrefs.getString(KEY_FCM_TOKEN, null));

            // Eski prefs'i temizle (hassas verileri kaldır)
            oldPrefs.edit().clear().apply();
            Log.d(TAG, "Eski SharedPreferences temizlendi.");
        }

        // Göç tamamlandı olarak işaretle
        editor.putBoolean(KEY_MIGRATION_DONE, true);
        editor.apply();
    }

    // Tek seferde tüm kullanıcıyı kaydet
    public void saveUser(User user) {
        editor.putInt(KEY_ID, user.getId());
        editor.putString(KEY_USERNAME, user.getUsername());
        editor.putString(KEY_EMAIL, user.getEmail());
        editor.putString(KEY_FIRST_NAME, user.getFirst_name());
        editor.putString(KEY_LAST_NAME, user.getLast_name());
        editor.putInt(KEY_CALORIE_GOAL, user.getCalorie_goal());
        editor.putInt(KEY_WATER_GOAL, user.getWater_goal());
        editor.putString(KEY_GENDER, user.getGender() != null ? user.getGender() : "");
        editor.putFloat(KEY_HEIGHT, user.getHeight_cm() != null ? user.getHeight_cm() : 0f);
        editor.putFloat(KEY_WEIGHT, user.getWeight_kg() != null ? user.getWeight_kg() : 0f);
        editor.putString(KEY_GOAL, user.getGoal() != null ? user.getGoal() : "");
        editor.apply();
    }

    // Getterlar
    public int getId()            { return sharedPreferences.getInt(KEY_ID, -1); }
    public String getUsername()   { return sharedPreferences.getString(KEY_USERNAME, ""); }
    public String getEmail()      { return sharedPreferences.getString(KEY_EMAIL, ""); }
    public String getFirstName()  { return sharedPreferences.getString(KEY_FIRST_NAME, ""); }
    public String getLastName()   { return sharedPreferences.getString(KEY_LAST_NAME, ""); }

    public void setFirstName(String fName) { editor.putString(KEY_FIRST_NAME, fName).apply(); }
    public void setLastName(String lName) { editor.putString(KEY_LAST_NAME, lName).apply(); }

    public void setGender(String gender) { editor.putString(KEY_GENDER, gender).apply(); }
    public String getGender() { return sharedPreferences.getString(KEY_GENDER, ""); }

    public void setHeight(float height) { editor.putFloat(KEY_HEIGHT, height).apply(); }
    public float getHeight() { return sharedPreferences.getFloat(KEY_HEIGHT, 0f); }

    public void setWeight(float weight) { editor.putFloat(KEY_WEIGHT, weight).apply(); }
    public float getWeight() { return sharedPreferences.getFloat(KEY_WEIGHT, 0f); }

    public void setGoal(String goal) { editor.putString(KEY_GOAL, goal).apply(); }
    public String getGoal() { return sharedPreferences.getString(KEY_GOAL, ""); }

    // Logout'ta hepsini temizle
    public void clearUser() {
        // Dil ve hatırlatıcı ayarlarını koru
        String savedLang = getLanguage();
        boolean savedReminder = isWaterReminderEnabled();

        editor.clear();
        editor.putString(KEY_LANGUAGE, savedLang);
        editor.putBoolean(KEY_WATER_REMINDER, savedReminder);
        editor.putBoolean(KEY_MIGRATION_DONE, true);
        editor.apply();
    }

    public void setCalorieGoal(int goal) { editor.putInt(KEY_CALORIE_GOAL, goal); editor.apply(); }
    public int getCalorieGoal()          { return sharedPreferences.getInt(KEY_CALORIE_GOAL, 2000); }

    public void setWaterGoal(int goal)   { editor.putInt(KEY_WATER_GOAL, goal); editor.apply(); }
    public int getWaterGoal()            { return sharedPreferences.getInt(KEY_WATER_GOAL, 2000); }

    public void setLanguage(String lang) { editor.putString(KEY_LANGUAGE, lang); editor.apply(); }
    public String getLanguage()          { return sharedPreferences.getString(KEY_LANGUAGE, "en"); } // Varsayılan: İngilizce

    public void setWaterReminderEnabled(boolean enabled) { editor.putBoolean(KEY_WATER_REMINDER, enabled); editor.apply(); }
    public boolean isWaterReminderEnabled() { return sharedPreferences.getBoolean(KEY_WATER_REMINDER, false); }

    public void setFcmToken(String token) { editor.putString(KEY_FCM_TOKEN, token).apply(); }
    public String getFcmToken() { return sharedPreferences.getString(KEY_FCM_TOKEN, null); }
}
