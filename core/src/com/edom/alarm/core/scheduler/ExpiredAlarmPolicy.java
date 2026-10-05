package com.edom.alarm.core.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import com.edom.alarm.ui.SettingsDialog;

/**
 * Single reader for the "delete expired alarms" preference so the ringing service,
 * trigger receiver, reconciler and dashboard all agree on the same policy.
 */
public final class ExpiredAlarmPolicy {

    private ExpiredAlarmPolicy() {}

    /**
     * True when an alarm that can never ring again (a finished one-time alarm or an
     * alarm without any remaining ringing date) should be removed instead of being
     * kept in the disabled state.
     */
    public static boolean shouldDeleteExpired(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                SharedPreferencesAlarmScheduleStore.PREFERENCES_NAME, Context.MODE_PRIVATE);
        return preferences.getBoolean(SettingsDialog.KEY_DELETE_EXPIRED_ALARMS,
                SettingsDialog.DEFAULT_DELETE_EXPIRED_ALARMS);
    }
}
