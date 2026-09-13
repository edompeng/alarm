package com.edom.alarm.core.scheduler;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.edom.alarm.AlarmApplication;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReason;

/**
 * Restores derived registrations after credential-unlocked system lifecycle events.
 */
public class BootCompletedReceiver extends BroadcastReceiver {

    private static final String TAG = "SmartAlarm:BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        ReconcileReason reason = mapReason(action);
        if (reason == null) {
            return;
        }
        AlarmApplication application = AlarmApplication.from(context);
        if (reason == ReconcileReason.EXACT_CAPABILITY_GRANTED
                && !application.capabilityEvaluator().evaluate().exactAlarmAvailable) {
            Log.i(TAG, "Ignoring exact-capability broadcast while access remains unavailable");
            return;
        }
        application.reconciler().reconcile(reason);
    }

    static ReconcileReason mapReason(String action) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            return ReconcileReason.BOOT_COMPLETED;
        }
        if (Intent.ACTION_TIME_CHANGED.equals(action)) {
            return ReconcileReason.TIME_CHANGED;
        }
        if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            return ReconcileReason.TIMEZONE_CHANGED;
        }
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return ReconcileReason.PACKAGE_REPLACED;
        }
        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            return ReconcileReason.EXACT_CAPABILITY_GRANTED;
        }
        return null;
    }
}
