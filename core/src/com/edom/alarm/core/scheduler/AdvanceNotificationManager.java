package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * Manages advance notification cards shown 30-60 minutes before an alarm triggers.
 * Provides the "Skip Today" button without disabling the recurring schedule.
 */
public class AdvanceNotificationManager {

    private static final String CHANNEL_ID_ADVANCE = "channel_smart_alarm_advance";
    private static final int NOTIFICATION_ID_BASE = 20000;

    public static void showAdvanceNotification(Context context, long alarmId, int minutesUntilAlarm, String alarmTimeStr) {
        ensureChannel(context);

        Intent skipIntent = new Intent(context, AlarmTriggerReceiver.class);
        skipIntent.setAction(AlarmTriggerReceiver.ACTION_SKIP_TODAY);
        skipIntent.putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId);

        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
            context,
            (int) alarmId,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID_ADVANCE);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setContentTitle("Upcoming Alarm: " + alarmTimeStr)
               .setContentText("Alarm will ring in " + minutesUntilAlarm + " minutes")
               .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
               .setOngoing(true)
               .addAction(new Notification.Action.Builder(
                   android.R.drawable.ic_menu_close_clear_cancel,
                   "Skip Today (跳过今天)",
                   skipPendingIntent
               ).build());

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_BASE + (int) alarmId, builder.build());
        }
    }

    public static void cancelAdvanceNotification(Context context, long alarmId) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_BASE + (int) alarmId);
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_ADVANCE,
                "Upcoming Alarm Alerts",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Gentle advance notifications with Skip Today option");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
