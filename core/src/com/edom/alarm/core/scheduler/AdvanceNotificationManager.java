package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import com.edom.alarm.R;

/**
 * Manages advance notification cards shown N minutes before an alarm triggers.
 * Provides the "Dismiss / 快捷关闭" button to suppress only today's occurrence.
 */
public class AdvanceNotificationManager {

    public static final String CHANNEL_ID_ADVANCE = "channel_smart_alarm_advance";
    public static final int NOTIFICATION_ID_BASE = 20000;

    public static void showAdvanceNotification(
            Context context,
            long alarmId,
            String occurrenceId,
            long triggerAtMs,
            int minutesUntilAlarm,
            String alarmTimeStr) {
        ensureChannel(context);

        Intent skipIntent = new Intent(context, AlarmTriggerReceiver.class);
        skipIntent.setAction(AlarmTriggerReceiver.ACTION_SKIP_TODAY);
        skipIntent.putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId);
        skipIntent.putExtra(AlarmTriggerReceiver.EXTRA_OCCURRENCE_ID, occurrenceId);
        skipIntent.putExtra(AlarmTriggerReceiver.EXTRA_TRIGGER_AT_MS, triggerAtMs);
        skipIntent.setData(new Uri.Builder()
                .scheme("alarm")
                .authority("skip")
                .appendPath(Long.toString(alarmId))
                .appendPath(occurrenceId)
                .build());
        skipIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent skipPendingIntent = PendingIntent.getBroadcast(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarmId),
                skipIntent,
                flags);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(context, CHANNEL_ID_ADVANCE);
        } else {
            builder = new Notification.Builder(context);
        }

        String title = context.getString(R.string.upcoming_alarm) + ": " + alarmTimeStr;
        String content = String.format(
                context.getString(R.string.alarm_rings_in), minutesUntilAlarm);
        String actionLabel = context.getString(R.string.quick_dismiss);

        builder.setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setOngoing(false)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        actionLabel,
                        skipPendingIntent).build());

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_BASE + (int) alarmId, builder.build());
        }
    }

    public static void cancelAdvanceNotification(Context context, long alarmId) {
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID_BASE + (int) alarmId);
        }
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_ADVANCE,
                    "Upcoming Alarm Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Gentle advance notifications with Quick Dismiss option");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }
}
