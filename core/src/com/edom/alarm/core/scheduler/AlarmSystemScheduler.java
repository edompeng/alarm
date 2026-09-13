package com.edom.alarm.core.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import com.edom.alarm.ui.MainActivity;

/** Sole Android AlarmManager and PendingIntent factory for alarm registrations. */
public final class AlarmSystemScheduler implements AlarmRegistrationGateway {
    private static final String ACTION_ADVANCE_NOTIFICATION =
            AlarmTriggerReceiver.ACTION_ADVANCE_NOTIFICATION;
    private static final int ADVANCE_REQUEST_CODE_XOR = 0x40000000;

    private final Context context;
    private final AlarmManager alarmManager;
    private final AlarmScheduleStore store;
    private final AlarmCapabilityEvaluator capabilityEvaluator;

    public AlarmSystemScheduler(
            Context context,
            AlarmScheduleStore store,
            AlarmCapabilityEvaluator capabilityEvaluator) {
        this.context = context.getApplicationContext();
        this.alarmManager = context.getSystemService(AlarmManager.class);
        this.store = store;
        this.capabilityEvaluator = capabilityEvaluator;
    }

    @Override
    public Registration schedule(StoredAlarm alarm) {
        AlarmDeliveryPolicy.TriggerDescriptor descriptor =
                AlarmDeliveryPolicy.createTriggerDescriptor(alarm.id);
        if (alarmManager == null) {
            return failed(alarm, descriptor, "AlarmManager unavailable");
        }

        CapabilitySnapshot capabilities = capabilityEvaluator.evaluate();
        RegistrationMode mode = AlarmDeliveryPolicy.chooseRegistrationMode(
                capabilities.exactAlarmAvailable,
                alarm.enabled,
                alarm.nextTriggerAtMs,
                System.currentTimeMillis());
        if (mode == RegistrationMode.NOT_REGISTERED) {
            return failed(alarm, descriptor, "Alarm is disabled or has no future occurrence");
        }

        try {
            // Durable occurrence identity must exist before AlarmManager can deliver it.
            store.save(alarm);
            PendingIntent operation =
                    triggerPendingIntent(alarm, PendingIntent.FLAG_UPDATE_CURRENT);
            if (mode == RegistrationMode.EXACT_ALARM_CLOCK) {
                PendingIntent showIntent = PendingIntent.getActivity(
                        context,
                        descriptor.requestCode,
                        new Intent(context, MainActivity.class),
                        immutableFlags(PendingIntent.FLAG_UPDATE_CURRENT));
                try {
                    alarmManager.setAlarmClock(
                            new AlarmManager.AlarmClockInfo(alarm.nextTriggerAtMs, showIntent),
                            operation);
                } catch (SecurityException ignored) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, alarm.nextTriggerAtMs, operation);
                    mode = RegistrationMode.BEST_EFFORT_IDLE_ALLOWED;
                }
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, alarm.nextTriggerAtMs, operation);
            }
            return new Registration(
                    alarm.id,
                    alarm.occurrenceId,
                    alarm.nextTriggerAtMs,
                    mode,
                    descriptor.requestCode,
                    descriptor.action,
                    descriptor.dataUri,
                    "");
        } catch (RuntimeException failure) {
            return failed(alarm, descriptor, failure.toString());
        }
    }

    @Override
    public void cancel(long alarmId) {
        if (alarmManager == null) {
            return;
        }
        PendingIntent existing = triggerPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            alarmManager.cancel(existing);
            existing.cancel();
        }
    }

    @Override
    public void scheduleAdvanceNotification(StoredAlarm alarm, long triggerAtMs) {
        if (alarmManager == null) {
            return;
        }
        SharedPreferences preferences = context.getSharedPreferences(
                "smart_alarm_prefs", Context.MODE_PRIVATE);
        if (!preferences.getBoolean("advance_notif_enabled", true)) {
            cancelAdvanceNotification(alarm.id);
            return;
        }
        int minutes = preferences.getInt("advance_notif_minutes", 30);
        if (minutes < 1 || minutes > 1440) {
            minutes = 30;
        }
        long advanceAtMs = triggerAtMs - minutes * 60L * 1000L;
        if (advanceAtMs <= System.currentTimeMillis()) {
            cancelAdvanceNotification(alarm.id);
            return;
        }
        PendingIntent operation = advancePendingIntent(
                alarm, triggerAtMs, minutes, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, advanceAtMs, operation);
    }

    @Override
    public void cancelAdvanceNotification(long alarmId) {
        if (alarmManager == null) {
            return;
        }
        PendingIntent existing = advancePendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE);
        if (existing != null) {
            alarmManager.cancel(existing);
            existing.cancel();
        }
    }

    public boolean hasTriggerPendingIntent(long alarmId) {
        PendingIntent existing = triggerPendingIntent(alarmId, PendingIntent.FLAG_NO_CREATE);
        return existing != null;
    }

    private PendingIntent triggerPendingIntent(StoredAlarm alarm, int lookupFlag) {
        Intent intent = canonicalIntent(
                alarm.id, AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER, "trigger");
        intent.putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarm.id);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_OCCURRENCE_ID, alarm.occurrenceId);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_TRIGGER_AT_MS, alarm.nextTriggerAtMs);
        return PendingIntent.getBroadcast(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarm.id),
                intent,
                immutableFlags(lookupFlag));
    }

    private PendingIntent triggerPendingIntent(long alarmId, int lookupFlag) {
        Intent intent = canonicalIntent(
                alarmId, AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER, "trigger");
        return PendingIntent.getBroadcast(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarmId),
                intent,
                immutableFlags(lookupFlag));
    }

    private PendingIntent advancePendingIntent(long alarmId, int lookupFlag) {
        Intent intent = canonicalIntent(alarmId, ACTION_ADVANCE_NOTIFICATION, "advance");
        intent.putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarmId);
        return PendingIntent.getBroadcast(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarmId) ^ ADVANCE_REQUEST_CODE_XOR,
                intent,
                immutableFlags(lookupFlag));
    }

    private PendingIntent advancePendingIntent(
            StoredAlarm alarm, long triggerAtMs, int minutes, int lookupFlag) {
        Intent intent = canonicalIntent(alarm.id, ACTION_ADVANCE_NOTIFICATION, "advance");
        intent.putExtra(AlarmTriggerReceiver.EXTRA_ALARM_ID, alarm.id);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_OCCURRENCE_ID, alarm.occurrenceId);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_TRIGGER_AT_MS, triggerAtMs);
        intent.putExtra(AlarmTriggerReceiver.EXTRA_ADVANCE_MINUTES, minutes);
        return PendingIntent.getBroadcast(
                context,
                AlarmDeliveryPolicy.deterministicRequestCode(alarm.id)
                        ^ ADVANCE_REQUEST_CODE_XOR,
                intent,
                immutableFlags(lookupFlag));
    }

    private Intent canonicalIntent(long alarmId, String action, String kind) {
        return new Intent(context, AlarmTriggerReceiver.class)
                .setAction(action)
                .setData(Uri.parse("alarm://" + kind + "/" + alarmId));
    }

    private int immutableFlags(int flags) {
        return flags | PendingIntent.FLAG_IMMUTABLE;
    }

    private Registration failed(
            StoredAlarm alarm,
            AlarmDeliveryPolicy.TriggerDescriptor descriptor,
            String message) {
        return new Registration(
                alarm.id,
                alarm.occurrenceId,
                alarm.nextTriggerAtMs,
                RegistrationMode.NOT_REGISTERED,
                descriptor.requestCode,
                descriptor.action,
                descriptor.dataUri,
                message);
    }
}
