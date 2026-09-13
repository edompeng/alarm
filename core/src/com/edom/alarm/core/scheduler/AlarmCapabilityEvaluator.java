package com.edom.alarm.core.scheduler;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;

/** Reads current platform delivery capabilities without persisting derived permission state. */
public final class AlarmCapabilityEvaluator {
    private final Context context;

    public AlarmCapabilityEvaluator(Context context) {
        this.context = context.getApplicationContext();
    }

    public CapabilitySnapshot evaluate() {
        return new CapabilitySnapshot(
                canScheduleExactAlarms(),
                canPostNotifications(),
                canUseFullScreenIntent(),
                System.currentTimeMillis());
    }

    public Intent highestImpactSettingsIntent(CapabilitySnapshot capabilities) {
        if (!capabilities.exactAlarmAvailable && Build.VERSION.SDK_INT >= 31) {
            return packageIntent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        }
        if (!capabilities.notificationsAvailable && Build.VERSION.SDK_INT >= 33) {
            return packageIntent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        }
        if (!capabilities.fullScreenAvailable && Build.VERSION.SDK_INT >= 34) {
            return packageIntent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
        }
        return packageIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
    }

    private boolean canPostNotifications() {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean enabled = manager != null && manager.areNotificationsEnabled();
        if (Build.VERSION.SDK_INT < 33) {
            return enabled;
        }
        return enabled
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        return manager != null && manager.canUseFullScreenIntent();
    }

    private Intent packageIntent(String action) {
        Intent intent = new Intent(action);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
