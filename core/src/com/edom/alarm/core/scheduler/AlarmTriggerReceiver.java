package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import com.edom.alarm.ui.RingingActivity;

/**
 * BroadcastReceiver for handling precise AlarmManager triggers.
 * Penetrates Doze mode and launches the full-screen awakening activity.
 */
public class AlarmTriggerReceiver extends BroadcastReceiver {

    public static final String ACTION_ALARM_TRIGGER = "com.edom.alarm.ACTION_ALARM_TRIGGER";
    public static final String ACTION_SKIP_TODAY = "com.edom.alarm.ACTION_SKIP_TODAY";
    public static final String ACTION_SNOOZE = "com.edom.alarm.ACTION_SNOOZE";
    public static final String ACTION_DISMISS = "com.edom.alarm.ACTION_DISMISS";
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";

    private static final String CHANNEL_ID_ALARM = "channel_smart_alarm_high_priority";
    private static final String WAKE_LOCK_TAG = "SmartAlarm:TriggerWakeLock";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        long alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1);

        if (ACTION_ALARM_TRIGGER.equals(action)) {
            handleAlarmTrigger(context, alarmId);
        }
    }

    private void handleAlarmTrigger(Context context, long alarmId) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            wakeLock.acquire(10 * 60 * 1000L); // 10 minutes timeout safety
        }

        try {
            ensureNotificationChannel(context);

            Intent ringIntent = new Intent(context, RingingActivity.class);
            ringIntent.putExtra(EXTRA_ALARM_ID, alarmId);
            ringIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                (int) alarmId,
                ringIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
            );

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new Notification.Builder(context, CHANNEL_ID_ALARM);
            } else {
                builder = new Notification.Builder(context);
            }

            builder.setContentTitle("Smart Alarm")
                   .setContentText("Alarm ringing")
                   .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                   .setPriority(Notification.PRIORITY_MAX)
                   .setCategory(Notification.CATEGORY_ALARM)
                   .setFullScreenIntent(fullScreenPendingIntent, true);

            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify((int) alarmId, builder.build());
            }

            context.startActivity(ringIntent);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void ensureNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_ALARM,
                "Smart Alarm Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("High priority wake up alarm notifications");
            channel.setBypassDnd(true);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
