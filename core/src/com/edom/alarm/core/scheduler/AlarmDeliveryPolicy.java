package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ProtectionLevel;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Framework-free occurrence, registration, and protection decisions. */
public final class AlarmDeliveryPolicy {
    public static final String ACTION_ALARM_TRIGGER =
            "com.edom.alarm.ACTION_ALARM_TRIGGER";
    public static final int FLAG_UPDATE_CURRENT = 0x08000000;
    public static final int FLAG_IMMUTABLE = 0x04000000;

    private AlarmDeliveryPolicy() {}

    public enum ExpirationAction {
        NONE,
        DISABLE,
        DELETE,
        ADVANCE_RECURRING
    }

    public enum HandoffDecision {
        START_SERVICE,
        POST_NOTIFICATION_FALLBACK,
        RECORD_UNDELIVERABLE
    }

    public static class TriggerDescriptor {
        public final String receiverClassName;
        public final String action;
        public final int requestCode;
        public final String dataUri;
        public final int flags;

        TriggerDescriptor(long alarmId) {
            receiverClassName = "com.edom.alarm.core.scheduler.AlarmTriggerReceiver";
            action = ACTION_ALARM_TRIGGER;
            requestCode = deterministicRequestCode(alarmId);
            dataUri = "alarm://trigger/" + alarmId;
            flags = FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
        }
    }

    public static final class PendingIntentIdentity extends TriggerDescriptor {
        public final boolean immutable = true;
        public final boolean updateCurrent = true;

        PendingIntentIdentity(long alarmId) {
            super(alarmId);
        }
    }

    public static final class ClaimDecision {
        public final ClaimResult result;
        public final StoredAlarm updatedAlarm;

        ClaimDecision(ClaimResult result, StoredAlarm updatedAlarm) {
            this.result = result;
            this.updatedAlarm = updatedAlarm;
        }
    }

    public static final class ExpirationDecision {
        public final ExpirationAction action;
        public final MissedState missedState;

        ExpirationDecision(ExpirationAction action, MissedState missedState) {
            this.action = action;
            this.missedState = missedState;
        }
    }

    public static StoredAlarm upgradeLegacyOccurrence(StoredAlarm alarm, long triggerAtMs) {
        if (alarm.nextTriggerAtMs > 0L && !alarm.occurrenceId.isEmpty()) {
            return alarm;
        }
        return alarm.withOccurrence(triggerAtMs, Math.max(1L, alarm.occurrenceGeneration + 1L));
    }

    public static StoredAlarm createNextOccurrence(StoredAlarm alarm, long triggerAtMs) {
        return alarm.withOccurrence(triggerAtMs, alarm.occurrenceGeneration + 1L);
    }

    public static ClaimDecision claimOccurrence(
            StoredAlarm alarm, String payloadOccurrenceId, boolean skipped) {
        if (alarm == null) {
            return new ClaimDecision(ClaimResult.MISSING, null);
        }
        if (!alarm.enabled) {
            return new ClaimDecision(ClaimResult.DISABLED, alarm);
        }
        if (skipped) {
            return new ClaimDecision(ClaimResult.SKIPPED, alarm);
        }
        if (payloadOccurrenceId == null || payloadOccurrenceId.isEmpty()
                || !payloadOccurrenceId.equals(alarm.occurrenceId)) {
            return new ClaimDecision(ClaimResult.STALE, alarm);
        }
        if (payloadOccurrenceId.equals(alarm.lastClaimedOccurrenceId)) {
            return new ClaimDecision(ClaimResult.ALREADY_CLAIMED, alarm);
        }
        return new ClaimDecision(
                ClaimResult.CLAIMED, alarm.withClaimedOccurrence(payloadOccurrenceId));
    }

    public static ExpirationDecision expireOccurrence(StoredAlarm alarm, long nowMs) {
        if (alarm == null || alarm.nextTriggerAtMs <= 0L || alarm.nextTriggerAtMs > nowMs) {
            return new ExpirationDecision(ExpirationAction.NONE, MissedState.NONE);
        }
        if (alarm.quickNap) {
            return new ExpirationDecision(
                    ExpirationAction.DELETE, MissedState.MISSED_QUICK_NAP);
        }
        if (alarm.repeatMode == 0) {
            return new ExpirationDecision(ExpirationAction.DISABLE, MissedState.MISSED_ONCE);
        }
        return new ExpirationDecision(
                ExpirationAction.ADVANCE_RECURRING, MissedState.MISSED_RECURRING);
    }

    public static RegistrationMode chooseRegistrationMode(
            boolean exactAvailable, boolean enabled, long triggerAtMs, long nowMs) {
        if (!enabled || triggerAtMs <= nowMs) {
            return RegistrationMode.NOT_REGISTERED;
        }
        return exactAvailable
                ? RegistrationMode.EXACT_ALARM_CLOCK
                : RegistrationMode.BEST_EFFORT_IDLE_ALLOWED;
    }

    public static TriggerDescriptor createTriggerDescriptor(long alarmId) {
        return new TriggerDescriptor(alarmId);
    }

    public static PendingIntentIdentity triggerIdentity(long alarmId) {
        return new PendingIntentIdentity(alarmId);
    }

    public static ProtectionLevel deriveProtection(
            CapabilitySnapshot capabilities,
            ReconcileReport report,
            List<Long> enabledAlarmIds) {
        if (enabledAlarmIds == null || enabledAlarmIds.isEmpty()) {
            return ProtectionLevel.FULL;
        }
        if (capabilities == null || !capabilities.exactAlarmAvailable
                || !capabilities.notificationsAvailable || !capabilities.fullScreenAvailable
                || report == null || !report.completed || !report.failedAlarmIds.isEmpty()) {
            return ProtectionLevel.LIMITED;
        }
        Set<Long> successful = new HashSet<>();
        for (Registration entry : report.entries) {
            if (entry != null && entry.isRegistered()) {
                successful.add(entry.alarmId);
            }
        }
        return successful.containsAll(enabledAlarmIds)
                ? ProtectionLevel.FULL : ProtectionLevel.LIMITED;
    }

    public static boolean isForceStopGuidanceRequired(int apiLevel) {
        return apiLevel >= 30;
    }

    public static HandoffDecision decideHandoff(
            boolean serviceStartRejected, CapabilitySnapshot capabilities) {
        if (!serviceStartRejected) {
            return HandoffDecision.START_SERVICE;
        }
        if (capabilities != null
                && (capabilities.notificationsAvailable || capabilities.fullScreenAvailable)) {
            return HandoffDecision.POST_NOTIFICATION_FALLBACK;
        }
        return HandoffDecision.RECORD_UNDELIVERABLE;
    }

    public static int deterministicRequestCode(long alarmId) {
        return (int) (alarmId ^ (alarmId >>> 32));
    }
}
