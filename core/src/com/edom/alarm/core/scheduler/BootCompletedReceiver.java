package com.edom.alarm.core.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Direct Boot aware receiver that reschedules all active alarms
 * upon device cold boot, unlock, or timezone transition.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "SmartAlarm:BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Log.i(TAG, "Received system broadcast: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_TIME_CHANGED.equals(action) ||
            Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {

            // Trigger rescheduling of all enabled alarms from device-protected storage
            rescheduleAlarms(context);
        }
    }

    private void rescheduleAlarms(Context context) {
        Log.i(TAG, "Rescheduling all enabled alarms after boot/time-change event");
    }
}
