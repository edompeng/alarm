package com.edom.alarm.core.scheduler;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import com.edom.alarm.AlarmApplication;
import com.edom.alarm.R;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.ui.RingingActivity;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Canonical AlarmManager entry point; claims an occurrence before service handoff. */
public final class AlarmTriggerReceiver extends BroadcastReceiver {
    public static final String ACTION_ALARM_TRIGGER = AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER;
    public static final String ACTION_ADVANCE_NOTIFICATION =
            "com.edom.alarm.ACTION_ADVANCE_NOTIFICATION";
    public static final String ACTION_SKIP_TODAY = "com.edom.alarm.ACTION_SKIP_TODAY";
    public static final String ACTION_SNOOZE = AlarmRingingService.ACTION_SNOOZE;
    public static final String ACTION_DISMISS = AlarmRingingService.ACTION_DISMISS;
    public static final String EXTRA_ALARM_ID = "extra_alarm_id";
    public static final String EXTRA_OCCURRENCE_ID = "extra_occurrence_id";
    public static final String EXTRA_TRIGGER_AT_MS = "extra_trigger_at_ms";
    public static final String EXTRA_ADVANCE_MINUTES = "extra_advance_minutes";
    public static final String EXTRA_RINGTONE_URI = "extra_ringtone_uri";
    public static final String EXTRA_VIBRATE_ENABLED = "extra_vibrate_enabled";

    private static final String TAG = "SmartAlarm:Trigger";

    /** Injection seam used by deterministic boundary tests and the application composition root. */
    public interface ServiceHandoff {
        void start(StoredAlarm alarm);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        AlarmApplication application = AlarmApplication.from(context);
        long alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L);
        switch (intent.getAction()) {
            case ACTION_ALARM_TRIGGER:
                handleAlarmTrigger(application, intent, alarmId);
                break;
            case ACTION_ADVANCE_NOTIFICATION:
                showAdvance(application, intent, alarmId);
                break;
            case ACTION_SKIP_TODAY:
                skipToday(application, intent, alarmId);
                break;
            case ACTION_SNOOZE:
            case ACTION_DISMISS:
                application.startService(new Intent(application, AlarmRingingService.class)
                        .setAction(intent.getAction())
                        .putExtras(intent));
                break;
            default:
                break;
        }
    }

    private void handleAlarmTrigger(
            AlarmApplication application, Intent intent, long alarmId) {
        String occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID);
        long triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_AT_MS, 0L);
        String scheduledDate = Instant.ofEpochMilli(triggerAtMs)
                .atZone(ZoneId.systemDefault()).toLocalDate().toString();
        ClaimResult claim = application.alarmStore().claimOccurrence(
                alarmId, occurrenceId, triggerAtMs, scheduledDate);
        if (claim != ClaimResult.CLAIMED) {
            Log.i(TAG, "Rejected alarm " + alarmId + " claim=" + claim);
            return;
        }

        StoredAlarm claimed = application.alarmStore().findById(alarmId);
        if (claimed == null) {
            return;
        }
        application.registrationGateway().cancelAdvanceNotification(alarmId);
        AdvanceNotificationManager.cancelAdvanceNotification(application, alarmId);
        transitionClaimedOccurrence(application, claimed);
        try {
            application.serviceHandoff().start(claimed);
        } catch (RuntimeException rejection) {
            Log.e(TAG, "Foreground service handoff rejected", rejection);
            postFallbackNotification(application, claimed);
        }
    }

    private void transitionClaimedOccurrence(
            AlarmApplication application, StoredAlarm claimed) {
        if (claimed.quickNap || claimed.repeatMode == 0) {
            // Keep the disabled record until completion so snooze can re-enable it. Quick Naps
            // are deleted only when the active ringing occurrence is dismissed or times out.
            application.alarmStore().save(claimed.withEnabled(false));
            return;
        }
        scheduleFollowingOccurrence(application, claimed, System.currentTimeMillis());
    }

    private void showAdvance(AlarmApplication application, Intent intent, long alarmId) {
        StoredAlarm alarm = application.alarmStore().findById(alarmId);
        String occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID);
        long triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_AT_MS, 0L);
        int minutes = intent.getIntExtra(EXTRA_ADVANCE_MINUTES, 0);
        if (!isCurrentOccurrence(alarm, occurrenceId, triggerAtMs) || minutes <= 0) {
            return;
        }
        String displayTime = String.format(
                Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute);
        AdvanceNotificationManager.showAdvanceNotification(
                application, alarm.id, alarm.occurrenceId, triggerAtMs, minutes, displayTime);
    }

    private void skipToday(AlarmApplication application, Intent intent, long alarmId) {
        StoredAlarm alarm = application.alarmStore().findById(alarmId);
        String occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID);
        long triggerAtMs = intent.getLongExtra(EXTRA_TRIGGER_AT_MS, 0L);
        if (!isCurrentOccurrence(alarm, occurrenceId, triggerAtMs)) {
            return;
        }
        AdvanceNotificationManager.cancelAdvanceNotification(application, alarmId);
        application.registrationGateway().cancelAdvanceNotification(alarmId);
        if (alarm.repeatMode == 0) {
            application.registrationGateway().cancel(alarmId);
            if (ExpiredAlarmPolicy.shouldDeleteExpired(application)) {
                application.alarmStore().delete(alarmId);
            } else {
                application.alarmStore().save(alarm.withEnabled(false));
            }
            return;
        }

        Set<String> skipped = new LinkedHashSet<>(alarm.skippedDates);
        skipped.add(Instant.ofEpochMilli(triggerAtMs)
                .atZone(ZoneId.systemDefault()).toLocalDate().toString());
        StoredAlarm updated = copyWithSkippedDates(alarm, skipped);
        application.alarmStore().save(updated);
        scheduleFollowingOccurrence(application, updated, System.currentTimeMillis());
    }

    private boolean isCurrentOccurrence(
            StoredAlarm alarm, String occurrenceId, long triggerAtMs) {
        return alarm != null && alarm.enabled && occurrenceId != null
                && occurrenceId.equals(alarm.occurrenceId)
                && triggerAtMs == alarm.nextTriggerAtMs;
    }

    private void scheduleFollowingOccurrence(
            AlarmApplication application, StoredAlarm alarm, long nowMs) {
        long nextTriggerAtMs = application.nextOccurrenceCalculator().calculateNextTriggerAtMs(
                alarm, Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());
        if (nextTriggerAtMs <= nowMs) {
            Log.e(TAG, "No future occurrence for recurring alarm " + alarm.id);
            application.registrationGateway().cancel(alarm.id);
            return;
        }
        StoredAlarm next = AlarmDeliveryPolicy.createNextOccurrence(alarm, nextTriggerAtMs);
        application.alarmStore().save(next);
        Registration registration = application.registrationGateway().schedule(next);
        if (registration.isRegistered()) {
            application.registrationGateway().scheduleAdvanceNotification(next, nextTriggerAtMs);
        } else {
            Log.e(TAG, "Failed to register next occurrence for alarm " + alarm.id
                    + ": " + registration.failureMessage);
        }
    }

    private StoredAlarm copyWithSkippedDates(StoredAlarm alarm, Set<String> skippedDates) {
        return new StoredAlarm(
                alarm.id, alarm.hour, alarm.minute, alarm.enabled, alarm.repeatMode,
                alarm.daysBitmask, alarm.label, alarm.quickNap, alarm.ringtoneUri,
                alarm.ringtoneTitle, alarm.vibrateEnabled, skippedDates,
                alarm.nextTriggerAtMs, alarm.occurrenceGeneration, alarm.occurrenceId,
                alarm.lastClaimedOccurrenceId, alarm.missedState, alarm.sourceJson);
    }

    /** Fallback surface for a claimed occurrence that cannot start its ringing service. */
    static void postFallbackNotification(Context context, StoredAlarm alarm) {
        CapabilitySnapshot capabilities = new AlarmCapabilityEvaluator(context).evaluate();
        if (!capabilities.notificationsAvailable) {
            return;
        }
        ensureFallbackChannel(context);
        // The stored record may already point at the following recurring occurrence;
        // commands must carry the occurrence that was actually claimed for this ring.
        String ringingOccurrenceId = alarm.lastClaimedOccurrenceId.isEmpty()
                ? alarm.occurrenceId : alarm.lastClaimedOccurrenceId;
        Intent presentation = new Intent(context, RingingActivity.class)
                .putExtra(EXTRA_ALARM_ID, alarm.id)
                .putExtra(EXTRA_OCCURRENCE_ID, ringingOccurrenceId);
        PendingIntent content = PendingIntent.getActivity(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarm.id),
                presentation,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(
                context, CHANNEL_ID_FALLBACK)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.alarm_ringing))
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(content)
                .setAutoCancel(false);
        if (capabilities.fullScreenAvailable) {
            builder.setFullScreenIntent(content, true);
        }
        Notification notification = builder.build();
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(AlarmDeliveryPolicy.deterministicRequestCode(alarm.id), notification);
        }
    }

    private static final String CHANNEL_ID_FALLBACK = "channel_smart_alarm_fallback";

    private static void ensureFallbackChannel(Context context) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID_FALLBACK,
                context.getString(R.string.alarm_notification_channel),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(context.getString(R.string.alarm_notification_channel_description));
        Uri sound = Settings.System.DEFAULT_ALARM_ALERT_URI;
        if (sound != null) {
            channel.setSound(sound, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }
        channel.enableVibration(true);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }
}
