package com.example.fitlog;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

public class LocaleHelper {

    /**
     * Context'i yeni seçilen dille günceller.
     * Activity'lerin attachBaseContext metodlarında kullanılmalıdır.
     * 
     * @param context Mevcut context
     * @return Yeni dille güncellenmiş context
     */
    public static Context setLocale(Context context) {
        PrefManager pref = new PrefManager(context);
        String language = pref.getLanguage(); // Varsayılan "en" gelir

        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);

        // createConfigurationContext, API 17 (Jelly Bean MR1) ve sonrası için en güncel metottur.
        return context.createConfigurationContext(config);
    }
}
