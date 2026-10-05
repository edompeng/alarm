package com.edom.alarm.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.edom.alarm.R;

/**
 * Utility for resolving and formatting the application version name.
 */
public final class AppVersionUtils {

    private AppVersionUtils() {}

    /**
     * Resolves the app version name from PackageManager, falling back to app_version_default.
     */
    public static String getAppVersionName(Context context) {
        if (context == null) {
            return "1.0.0";
        }
        try {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                PackageInfo pInfo = pm.getPackageInfo(context.getPackageName(), 0);
                if (pInfo != null && pInfo.versionName != null && !pInfo.versionName.trim().isEmpty()) {
                    return pInfo.versionName.trim();
                }
            }
        } catch (Exception ignored) {
        }
        try {
            return context.getString(R.string.app_version_default);
        } catch (Exception ignored) {
            return "1.0.0";
        }
    }

    /**
     * Formats the user-facing version string according to current locale (e.g. "版本 v1.0.0-202610051305").
     */
    public static String getFormattedVersion(Context context) {
        String versionName = getAppVersionName(context);
        if (context == null) {
            return "v" + versionName;
        }
        try {
            return context.getString(R.string.about_version, versionName);
        } catch (Exception ignored) {
            return "v" + versionName;
        }
    }
}
