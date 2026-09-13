package com.edom.alarm.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Helper for OEM specific background activity, high power consumption,
 * and autostart whitelist permissions (especially for Vivo / iQOO OriginOS).
 */
public class OemPermissionHelper {

    /**
     * Checks if current device is manufactured by Vivo or iQOO.
     */
    public static boolean isVivoOrIqoo() {
        String man = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";
        return man.contains("vivo") || man.contains("iqoo") || brand.contains("vivo") || brand.contains("iqoo");
    }

    /**
     * Attempts to navigate to OriginOS Background High Power Consumption or Autostart settings.
     * Falls back to application details settings if OEM intents are not available.
     */
    public static void openOriginOsBackgroundSettings(Context context) {
        Intent[] intents = new Intent[] {
            // OriginOS / FuntouchOS Background High Power Consumption (后台高耗电)
            new Intent().setComponent(new ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
            // iQOO Secure Whitelist
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            // Vivo Permission Manager Purview Tab
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")),
            // iQOO Autostart
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            // Standard Battery Optimization Settings
            new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // Application Details fallback
            new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()))
        };

        boolean launched = false;
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                    launched = true;
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (!launched) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
            } catch (Exception e) {
                Toast.makeText(context, "Could not open settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Prompts standard battery optimization exemption dialog.
     */
    @SuppressLint("BatteryLife")
    public static void requestIgnoreBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(context.getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } catch (Exception ignored) {
            }
        }
    }
}
