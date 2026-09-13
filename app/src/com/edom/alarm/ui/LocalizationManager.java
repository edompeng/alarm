package com.edom.alarm.ui;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

/**
 * Manages in-app dynamic language switching across Android 11 through 15.
 * Supports "system" (Follow System), "zh" (Simplified Chinese), and "en" (English).
 */
public class LocalizationManager {

    public static final String PREFS_NAME = "smart_alarm_prefs";
    public static final String KEY_APP_LANGUAGE = "app_language";

    public static final String LANG_SYSTEM = "system";
    public static final String LANG_ZH = "zh";
    public static final String LANG_EN = "en";

    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_APP_LANGUAGE, LANG_SYSTEM);
    }

    public static void setLanguage(Context context, String langCode) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_APP_LANGUAGE, langCode).apply();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleManager lm = context.getSystemService(LocaleManager.class);
            if (lm != null) {
                if (LANG_ZH.equals(langCode)) {
                    lm.setApplicationLocales(new LocaleList(Locale.SIMPLIFIED_CHINESE));
                } else if (LANG_EN.equals(langCode)) {
                    lm.setApplicationLocales(new LocaleList(Locale.ENGLISH));
                } else {
                    lm.setApplicationLocales(LocaleList.getEmptyLocaleList());
                }
            }
        }
    }

    public static Locale getTargetLocale(Context context) {
        String lang = getLanguage(context);
        if (LANG_ZH.equals(lang)) {
            return Locale.SIMPLIFIED_CHINESE;
        } else if (LANG_EN.equals(lang)) {
            return Locale.ENGLISH;
        } else {
            return Locale.getDefault();
        }
    }

    public static Context wrapContext(Context context) {
        String lang = getLanguage(context);
        Locale targetLocale;
        if (LANG_ZH.equals(lang)) {
            targetLocale = Locale.SIMPLIFIED_CHINESE;
        } else if (LANG_EN.equals(lang)) {
            targetLocale = Locale.ENGLISH;
        } else {
            return context;
        }

        Locale.setDefault(targetLocale);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(targetLocale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(targetLocale));
        }
        return context.createConfigurationContext(config);
    }

    public static void updateActivityLocale(Activity activity) {
        String lang = getLanguage(activity);
        Locale targetLocale;
        if (LANG_ZH.equals(lang)) {
            targetLocale = Locale.SIMPLIFIED_CHINESE;
        } else if (LANG_EN.equals(lang)) {
            targetLocale = Locale.ENGLISH;
        } else {
            targetLocale = Resources.getSystem().getConfiguration().locale;
            if (targetLocale == null) {
                targetLocale = Locale.getDefault();
            }
        }

        Locale.setDefault(targetLocale);
        Resources res = activity.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(targetLocale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(targetLocale));
        }
        res.updateConfiguration(config, res.getDisplayMetrics());
    }
}
