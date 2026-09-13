package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReason;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Restores derived AlarmManager registrations from durable logical alarm state. */
public final class AlarmScheduleReconciler {
    private final AlarmScheduleStore store;
    private final NextOccurrenceCalculator calculator;
    private final AlarmRegistrationGateway registrationGateway;
    private final LongSupplier clock;
    private final Supplier<ZoneId> zoneSupplier;

    public AlarmScheduleReconciler(
            AlarmScheduleStore store,
            NextOccurrenceCalculator calculator,
            AlarmRegistrationGateway registrationGateway,
            LongSupplier clock,
            Supplier<ZoneId> zoneSupplier) {
        this.store = Objects.requireNonNull(store);
        this.calculator = Objects.requireNonNull(calculator);
        this.registrationGateway = Objects.requireNonNull(registrationGateway);
        this.clock = Objects.requireNonNull(clock);
        this.zoneSupplier = Objects.requireNonNull(zoneSupplier);
    }

    public ReconcileReport reconcile(ReconcileReason reason) {
        long nowMs = clock.getAsLong();
        List<Registration> entries = new ArrayList<>();
        List<Long> failedAlarmIds = new ArrayList<>();
        List<StoredAlarm> alarms;
        try {
            alarms = store.loadAll();
        } catch (RuntimeException failure) {
            return new ReconcileReport(reason, false, entries, failedAlarmIds, nowMs);
        }

        for (StoredAlarm original : alarms) {
            if (!original.enabled) {
                continue;
            }
            try {
                StoredAlarm alarm = applyExpiredPolicy(original, nowMs);
                if (alarm == null || !alarm.enabled) {
                    registrationGateway.cancel(original.id);
                    registrationGateway.cancelAdvanceNotification(original.id);
                    continue;
                }

                if (alarm.nextTriggerAtMs <= nowMs || alarm.occurrenceId.isEmpty()) {
                    long nextTrigger = calculator.calculateNextTriggerAtMs(
                            alarm, Instant.ofEpochMilli(nowMs), zoneSupplier.get());
                    if (nextTrigger <= nowMs) {
                        Registration failed = failedRegistration(alarm, "No future occurrence");
                        entries.add(failed);
                        failedAlarmIds.add(alarm.id);
                        continue;
                    }
                    alarm = AlarmDeliveryPolicy.createNextOccurrence(alarm, nextTrigger);
                    store.save(alarm);
                }

                Registration result = registrationGateway.schedule(alarm);
                entries.add(result);
                if (result.isRegistered()) {
                    registrationGateway.scheduleAdvanceNotification(alarm, alarm.nextTriggerAtMs);
                } else {
                    failedAlarmIds.add(alarm.id);
                }
            } catch (RuntimeException failure) {
                entries.add(failedRegistration(original, failure.getMessage()));
                failedAlarmIds.add(original.id);
            }
        }
        return new ReconcileReport(reason, true, entries, failedAlarmIds, nowMs);
    }

    private StoredAlarm applyExpiredPolicy(StoredAlarm alarm, long nowMs) {
        AlarmDeliveryPolicy.ExpirationDecision expiration =
                AlarmDeliveryPolicy.expireOccurrence(alarm, nowMs);
        if (expiration.action == AlarmDeliveryPolicy.ExpirationAction.NONE) {
            return alarm;
        }
        MissedAlarmOutcome outcome =
                MissedAlarmOutcome.create(alarm, expiration.missedState, nowMs);
        if (expiration.action == AlarmDeliveryPolicy.ExpirationAction.DELETE) {
            store.applyMissedTransition(outcome, null, true);
            return null;
        }
        if (expiration.action == AlarmDeliveryPolicy.ExpirationAction.DISABLE) {
            StoredAlarm disabled = alarm.withMissedState(expiration.missedState).withEnabled(false);
            store.applyMissedTransition(outcome, disabled, false);
            return disabled;
        }

        long nextTrigger = calculator.calculateNextTriggerAtMs(
                alarm, Instant.ofEpochMilli(nowMs), zoneSupplier.get());
        if (nextTrigger <= nowMs) {
            store.applyMissedTransition(outcome, alarm.withMissedState(expiration.missedState), false);
            return alarm.withMissedState(expiration.missedState);
        }
        StoredAlarm advanced = AlarmDeliveryPolicy.createNextOccurrence(alarm, nextTrigger);
        store.applyMissedTransition(outcome, advanced, false);
        return advanced;
    }

    private Registration failedRegistration(StoredAlarm alarm, String message) {
        AlarmDeliveryPolicy.TriggerDescriptor identity =
                AlarmDeliveryPolicy.createTriggerDescriptor(alarm.id);
        return new Registration(
                alarm.id,
                alarm.occurrenceId,
                alarm.nextTriggerAtMs,
                RegistrationMode.NOT_REGISTERED,
                identity.requestCode,
                identity.action,
                identity.dataUri,
                message == null ? "Registration failed" : message);
    }
}
