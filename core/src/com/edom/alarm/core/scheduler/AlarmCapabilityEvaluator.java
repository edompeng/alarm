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
        // 1. Exact Alarm Settings (API 31+)
        if (!capabilities.exactAlarmAvailable && Build.VERSION.SDK_INT >= 31) {
            Intent intent = packageIntent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            if (isIntentResolvable(intent)) {
                return intent;
            }
        }

        // 2. Notification Settings (API 33+) - Requires extras, NOT data URI
        if (!capabilities.notificationsAvailable && Build.VERSION.SDK_INT >= 33) {
            Intent notifIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            notifIntent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            notifIntent.putExtra("android.provider.extra.APP_PACKAGE", context.getPackageName());
            notifIntent.putExtra("app_package", context.getPackageName());
            notifIntent.putExtra("app_uid", context.getApplicationInfo().uid);
            notifIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (isIntentResolvable(notifIntent)) {
                return notifIntent;
            }
        }

        // 3. Full-Screen Intent Settings (API 34+)
        if (!capabilities.fullScreenAvailable && Build.VERSION.SDK_INT >= 34) {
            Intent fullScreenIntent = packageIntent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
            if (isIntentResolvable(fullScreenIntent)) {
                return fullScreenIntent;
            }
        }

        // 4. Application Details Settings fallback
        Intent appDetailsIntent = packageIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        if (isIntentResolvable(appDetailsIntent)) {
            return appDetailsIntent;
        }

        // 5. Global Settings fallback
        return new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private boolean isIntentResolvable(Intent intent) {
        try {
            return context.getPackageManager().resolveActivity(intent, 0) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) {
            return true;
        }
        try {
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            return manager != null && manager.canScheduleExactAlarms();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canPostNotifications() {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            boolean enabled = manager != null && manager.areNotificationsEnabled();
            if (Build.VERSION.SDK_INT < 33) {
                return enabled;
            }
            return enabled
                    && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) {
            return true;
        }
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            return manager != null && manager.canUseFullScreenIntent();
        } catch (Exception e) {
            return true;
        }
    }

    private Intent packageIntent(String action) {
        Intent intent = new Intent(action);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }
}
