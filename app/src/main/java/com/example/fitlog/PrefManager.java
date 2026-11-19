package com.example.fitlog;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Kullanıcı bilgileri ve oturum token'ları için şifreli depolama.
 *
 * - SharedPreferences örneği uygulama boyunca tek sefer oluşturulur (MasterKey
 *   üretimi pahalıdır).
 * - Şifreli depo bozulursa önce silinip yeniden oluşturulur.
 * - Yine de oluşturulamazsa şifresiz depoya düşülür; ancak bu durumda oturum
 *   token'ları DİSKE YAZILMAZ, yalnızca bellekte tutulur.
 */
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
    private static final String KEY_BIRTH_YEAR = "user_birth_year";
    private static final String KEY_ACTIVITY   = "user_activity_level";
    private static final String KEY_TARGET_WEIGHT = "user_target_weight";
    private static final String KEY_PROTEIN_GOAL = "protein_goal";
    private static final String KEY_CARBS_GOAL   = "carbs_goal";
    private static final String KEY_FAT_GOAL     = "fat_goal";
    private static final String KEY_REMINDER_INTERVAL_H = "reminder_interval_hours";
    private static final String KEY_QUIET_START = "reminder_quiet_start";
    private static final String KEY_QUIET_END   = "reminder_quiet_end";
    private static final String KEY_THEME_MODE  = "theme_mode";
    private static final String KEY_PKCE_VERIFIER = "oauth_pkce_verifier";

    private static final String KEY_ACCESS_TOKEN  = "auth_access_token";
    private static final String KEY_REFRESH_TOKEN = "auth_refresh_token";
    private static final String KEY_TOKEN_EXPIRES = "auth_expires_at";
    private static final String KEY_AUTH_ID       = "auth_user_id";

    private static SharedPreferences sharedInstance;
    private static boolean isEncrypted;

    // Şifreli depo kullanılamazsa token'lar yalnızca burada tutulur.
    private static volatile String memAccessToken;
    private static volatile String memRefreshToken;
    private static volatile long memExpiresAt;

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public PrefManager(Context context) {
        sharedPreferences = obtain(context.getApplicationContext());
        editor = sharedPreferences.edit();
    }

    private static synchronized SharedPreferences obtain(Context context) {
        if (sharedInstance != null) return sharedInstance;

        SharedPreferences prefs = createEncrypted(context);
        if (prefs == null) {
            // Keystore anahtarı değişmiş/bozulmuş olabilir: depoyu silip tekrar dene.
            Log.w(TAG, "Şifreli depo açılamadı, sıfırlanıp yeniden deneniyor.");
            context.deleteSharedPreferences(ENCRYPTED_PREF_NAME);
            prefs = createEncrypted(context);
        }

        if (prefs != null) {
            isEncrypted = true;
            migrateFromOldPrefs(context, prefs);
        } else {
            Log.e(TAG, "Şifreli depo kullanılamıyor; oturum token'ları diske yazılmayacak.");
            isEncrypted = false;
            prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        sharedInstance = prefs;
        return prefs;
    }

    private static SharedPreferences createEncrypted(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "EncryptedSharedPreferences oluşturulamadı", e);
            return null;
        }
    }

    /** Eski şifrelenmemiş SharedPreferences'taki ayarları taşır (bir kez). */
    private static void migrateFromOldPrefs(Context context, SharedPreferences target) {
        if (target.getBoolean(KEY_MIGRATION_DONE, false)) return;

        SharedPreferences oldPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = target.edit();
        // Kullanıcı kimliği taşınmaz: yeni Supabase Auth oturumu gerektiği için
        // eski kullanıcılar bir kez yeniden giriş yapar.
        e.putInt(KEY_CALORIE_GOAL, oldPrefs.getInt(KEY_CALORIE_GOAL, 2000));
        e.putInt(KEY_WATER_GOAL, oldPrefs.getInt(KEY_WATER_GOAL, 2000));
        e.putString(KEY_LANGUAGE, oldPrefs.getString(KEY_LANGUAGE, "en"));
        e.putBoolean(KEY_WATER_REMINDER, oldPrefs.getBoolean(KEY_WATER_REMINDER, false));
        e.putBoolean(KEY_MIGRATION_DONE, true);
        e.apply();

        oldPrefs.edit().clear().apply();
    }

    // ── Kullanıcı ──────────────────────────────────────────────────────

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
        editor.putInt(KEY_BIRTH_YEAR, user.getBirth_year() != null ? user.getBirth_year() : 0);
        editor.putString(KEY_ACTIVITY, user.getActivity_level() != null ? user.getActivity_level() : "");
        editor.putFloat(KEY_TARGET_WEIGHT, user.getTarget_weight_kg() != null ? user.getTarget_weight_kg() : 0f);
        editor.putInt(KEY_PROTEIN_GOAL, user.getDaily_protein_goal_g() != null ? user.getDaily_protein_goal_g() : 0);
        editor.putInt(KEY_CARBS_GOAL, user.getDaily_carbs_goal_g() != null ? user.getDaily_carbs_goal_g() : 0);
        editor.putInt(KEY_FAT_GOAL, user.getDaily_fat_goal_g() != null ? user.getDaily_fat_goal_g() : 0);
        editor.apply();
    }

    public int getBirthYear() { return sharedPreferences.getInt(KEY_BIRTH_YEAR, 0); }
    public void setBirthYear(int y) { editor.putInt(KEY_BIRTH_YEAR, y).apply(); }

    public String getActivityLevel() { return sharedPreferences.getString(KEY_ACTIVITY, ""); }
    public void setActivityLevel(String a) { editor.putString(KEY_ACTIVITY, a).apply(); }

    public float getTargetWeight() { return sharedPreferences.getFloat(KEY_TARGET_WEIGHT, 0f); }
    public void setTargetWeight(float w) { editor.putFloat(KEY_TARGET_WEIGHT, w).apply(); }

    public int getProteinGoal() { return sharedPreferences.getInt(KEY_PROTEIN_GOAL, 0); }
    public int getCarbsGoal() { return sharedPreferences.getInt(KEY_CARBS_GOAL, 0); }
    public int getFatGoal() { return sharedPreferences.getInt(KEY_FAT_GOAL, 0); }
    public void setMacroGoals(int protein, int carbs, int fat) {
        editor.putInt(KEY_PROTEIN_GOAL, protein).putInt(KEY_CARBS_GOAL, carbs).putInt(KEY_FAT_GOAL, fat).apply();
    }

    // ── Hatırlatıcı / tema (çıkışta korunur) ──────────────────────────
    public int getReminderIntervalHours() { return sharedPreferences.getInt(KEY_REMINDER_INTERVAL_H, 2); }
    public void setReminderIntervalHours(int h) { editor.putInt(KEY_REMINDER_INTERVAL_H, h).apply(); }
    public int getQuietStartHour() { return sharedPreferences.getInt(KEY_QUIET_START, 23); }
    public int getQuietEndHour() { return sharedPreferences.getInt(KEY_QUIET_END, 8); }
    public void setQuietHours(int start, int end) {
        editor.putInt(KEY_QUIET_START, start).putInt(KEY_QUIET_END, end).apply();
    }

    /** "system", "light" veya "dark". */
    public String getThemeMode() { return sharedPreferences.getString(KEY_THEME_MODE, "system"); }
    public void setThemeMode(String mode) { editor.putString(KEY_THEME_MODE, mode).commit(); }

    // ── OAuth PKCE (tarayıcıya gidip dönerken geçici) ──────────────────
    public void setPkceVerifier(String v) { editor.putString(KEY_PKCE_VERIFIER, v).commit(); }
    public String takePkceVerifier() {
        String v = sharedPreferences.getString(KEY_PKCE_VERIFIER, null);
        editor.remove(KEY_PKCE_VERIFIER).commit();
        return v;
    }

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

    /** Çıkışta kullanıcı ve oturum bilgilerini siler; dil ve hatırlatıcı ayarı korunur. */
    public void clearUser() {
        String savedLang = getLanguage();
        boolean savedReminder = isWaterReminderEnabled();
        int savedInterval = getReminderIntervalHours();
        int savedQuietStart = getQuietStartHour();
        int savedQuietEnd = getQuietEndHour();
        String savedTheme = getThemeMode();

        memAccessToken = null;
        memRefreshToken = null;
        memExpiresAt = 0;

        editor.clear();
        editor.putString(KEY_LANGUAGE, savedLang);
        editor.putBoolean(KEY_WATER_REMINDER, savedReminder);
        editor.putInt(KEY_REMINDER_INTERVAL_H, savedInterval);
        editor.putInt(KEY_QUIET_START, savedQuietStart);
        editor.putInt(KEY_QUIET_END, savedQuietEnd);
        editor.putString(KEY_THEME_MODE, savedTheme);
        editor.putBoolean(KEY_MIGRATION_DONE, true);
        editor.commit(); // senkron: hemen ardından gelen okuma eski oturumu görmesin
    }

    public void setCalorieGoal(int goal) { editor.putInt(KEY_CALORIE_GOAL, goal).apply(); }
    public int getCalorieGoal()          { return sharedPreferences.getInt(KEY_CALORIE_GOAL, 2000); }

    public void setWaterGoal(int goal)   { editor.putInt(KEY_WATER_GOAL, goal).apply(); }
    public int getWaterGoal()            { return sharedPreferences.getInt(KEY_WATER_GOAL, 2000); }

    public void setLanguage(String lang) { editor.putString(KEY_LANGUAGE, lang).apply(); }
    public String getLanguage()          { return sharedPreferences.getString(KEY_LANGUAGE, "en"); }

    public void setWaterReminderEnabled(boolean enabled) { editor.putBoolean(KEY_WATER_REMINDER, enabled).apply(); }
    public boolean isWaterReminderEnabled() { return sharedPreferences.getBoolean(KEY_WATER_REMINDER, false); }

    public void setFcmToken(String token) { editor.putString(KEY_FCM_TOKEN, token).apply(); }
    public String getFcmToken() { return sharedPreferences.getString(KEY_FCM_TOKEN, null); }

    // ── Oturum (Supabase Auth) ─────────────────────────────────────────

    public void saveSession(String accessToken, String refreshToken, long expiresAt, String authId) {
        if (isEncrypted) {
            editor.putString(KEY_ACCESS_TOKEN, accessToken);
            editor.putString(KEY_REFRESH_TOKEN, refreshToken);
            editor.putLong(KEY_TOKEN_EXPIRES, expiresAt);
        } else {
            memAccessToken = accessToken;
            memRefreshToken = refreshToken;
            memExpiresAt = expiresAt;
        }
        if (authId != null) editor.putString(KEY_AUTH_ID, authId);
        editor.commit();
    }

    public String getAccessToken() {
        return isEncrypted ? sharedPreferences.getString(KEY_ACCESS_TOKEN, null) : memAccessToken;
    }

    public String getRefreshToken() {
        return isEncrypted ? sharedPreferences.getString(KEY_REFRESH_TOKEN, null) : memRefreshToken;
    }

    public long getTokenExpiresAt() {
        return isEncrypted ? sharedPreferences.getLong(KEY_TOKEN_EXPIRES, 0) : memExpiresAt;
    }

    public String getAuthId() { return sharedPreferences.getString(KEY_AUTH_ID, null); }
}
