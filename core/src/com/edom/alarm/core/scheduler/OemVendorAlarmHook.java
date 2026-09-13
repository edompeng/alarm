package com.edom.alarm.core.scheduler;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * OEM Hardware Wakeup & RTC Interfacing for Samsung S25 Ultra (One UI)
 * and iQOO Z9 Turbo+ (OriginOS).
 */
public class OemVendorAlarmHook {

    private static final String TAG = "SmartAlarm:OemHook";

    public static void registerOemPowerOffAlarm(Context context, long triggerEpochMs) {
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        String brand = Build.BRAND != null ? Build.BRAND.toLowerCase() : "";

        if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            // Samsung S25 Ultra One UI Power-off alarm broadcast
            Intent samsungIntent = new Intent("com.samsung.sec.android.clockpackage.alarm.ALARM_STARTED");
            samsungIntent.putExtra("trigger_time", triggerEpochMs);
            context.sendBroadcast(samsungIntent);
            Log.d(TAG, "Dispatched Samsung S25 Ultra One UI RTC Wakeup intent");
        } else if (manufacturer.contains("vivo") || brand.contains("iqoo") || manufacturer.contains("iqoo")) {
            // iQOO Z9 Turbo+ OriginOS Power-off alarm broadcast
            Intent iqooIntent = new Intent("com.vivo.daemonService.alarm.POWER_OFF_WAKE");
            iqooIntent.putExtra("trigger_time", triggerEpochMs);
            context.sendBroadcast(iqooIntent);
            Log.d(TAG, "Dispatched iQOO Z9 Turbo+ OriginOS RTC Wakeup intent");
        }
    }
}
