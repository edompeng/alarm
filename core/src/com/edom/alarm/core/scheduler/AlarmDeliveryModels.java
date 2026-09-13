package com.edom.alarm.core.scheduler;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable values shared by alarm delivery collaborators. */
public final class AlarmDeliveryModels {
    private AlarmDeliveryModels() {}

    public enum MissedState {
        NONE,
        MISSED_ONCE,
        MISSED_QUICK_NAP,
        MISSED_RECURRING
    }

    public enum RegistrationMode {
        EXACT_ALARM_CLOCK,
        BEST_EFFORT_IDLE_ALLOWED,
        NOT_REGISTERED
    }

    public enum ProtectionLevel {
        FULL,
        LIMITED
    }

    public enum ReconcileReason {
        APP_LAUNCH,
        APP_RESUME,
        BOOT_COMPLETED,
        TIME_CHANGED,
        TIMEZONE_CHANGED,
        PACKAGE_REPLACED,
        EXACT_CAPABILITY_GRANTED
    }

    public enum ClaimResult {
        CLAIMED,
        MISSING,
        DISABLED,
        SKIPPED,
        STALE,
        ALREADY_CLAIMED,
        INVALID
    }

    /** Complete logical alarm plus its durable next-occurrence state. */
    public static final class StoredAlarm {
        public final long id;
        public final int hour;
        public final int minute;
        public final boolean enabled;
        public final int repeatMode;
        public final int daysBitmask;
        public final String label;
        public final boolean quickNap;
        public final String ringtoneUri;
        public final String ringtoneTitle;
        public final boolean vibrateEnabled;
        public final Set<String> skippedDates;
        public final long nextTriggerAtMs;
        public final long occurrenceGeneration;
        public final String occurrenceId;
        public final String lastClaimedOccurrenceId;
        public final MissedState missedState;
        public final String sourceJson;

        public StoredAlarm(
                long id,
                int hour,
                int minute,
                boolean enabled,
                int repeatMode,
                int daysBitmask,
                String label,
                boolean quickNap,
                String ringtoneUri,
                String ringtoneTitle,
                boolean vibrateEnabled,
                Set<String> skippedDates,
                long nextTriggerAtMs,
                long occurrenceGeneration,
                String occurrenceId,
                String lastClaimedOccurrenceId,
                MissedState missedState,
                String sourceJson) {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                throw new IllegalArgumentException("Invalid alarm time");
            }
            if (repeatMode < 0 || repeatMode > 2) {
                throw new IllegalArgumentException("Invalid repeat mode");
            }
            this.id = id;
            this.hour = hour;
            this.minute = minute;
            this.enabled = enabled;
            this.repeatMode = repeatMode;
            this.daysBitmask = daysBitmask;
            this.label = label == null ? "" : label;
            this.quickNap = quickNap;
            this.ringtoneUri = ringtoneUri;
            this.ringtoneTitle = ringtoneTitle == null ? "Default Alarm Sound" : ringtoneTitle;
            this.vibrateEnabled = vibrateEnabled;
            this.skippedDates = Collections.unmodifiableSet(
                    new LinkedHashSet<>(skippedDates == null ? Collections.emptySet() : skippedDates));
            this.nextTriggerAtMs = nextTriggerAtMs;
            this.occurrenceGeneration = occurrenceGeneration;
            this.occurrenceId = occurrenceId == null ? "" : occurrenceId;
            this.lastClaimedOccurrenceId =
                    lastClaimedOccurrenceId == null ? "" : lastClaimedOccurrenceId;
            this.missedState = missedState == null ? MissedState.NONE : missedState;
            this.sourceJson = sourceJson == null ? "" : sourceJson;
        }

        public StoredAlarm withEnabled(boolean value) {
            return copy(value, nextTriggerAtMs, occurrenceGeneration, occurrenceId,
                    lastClaimedOccurrenceId, missedState);
        }

        public StoredAlarm withOccurrence(long triggerAtMs, long generation) {
            String nextOccurrenceId = createOccurrenceId(id, generation, triggerAtMs);
            return copy(enabled, triggerAtMs, generation, nextOccurrenceId, "", MissedState.NONE);
        }

        public StoredAlarm withClaimedOccurrence(String claimedOccurrenceId) {
            return copy(enabled, nextTriggerAtMs, occurrenceGeneration, occurrenceId,
                    claimedOccurrenceId, missedState);
        }

        public StoredAlarm withMissedState(MissedState state) {
            return copy(enabled, nextTriggerAtMs, occurrenceGeneration, occurrenceId,
                    lastClaimedOccurrenceId, state);
        }

        private StoredAlarm copy(
                boolean nextEnabled,
                long nextTrigger,
                long nextGeneration,
                String nextOccurrence,
                String nextClaimed,
                MissedState nextMissed) {
            return new StoredAlarm(id, hour, minute, nextEnabled, repeatMode, daysBitmask, label,
                    quickNap, ringtoneUri, ringtoneTitle, vibrateEnabled, skippedDates, nextTrigger,
                    nextGeneration, nextOccurrence, nextClaimed, nextMissed, sourceJson);
        }
    }

    public static final class MissedAlarmOutcome {
        public final String outcomeId;
        public final long alarmId;
        public final String occurrenceId;
        public final MissedState missedState;
        public final long missedAtMs;
        public final String label;

        public MissedAlarmOutcome(
                String outcomeId,
                long alarmId,
                String occurrenceId,
                MissedState missedState,
                long missedAtMs,
                String label) {
            if (missedState == null || missedState == MissedState.NONE) {
                throw new IllegalArgumentException("A missed outcome requires a missed state");
            }
            this.outcomeId = Objects.requireNonNull(outcomeId);
            this.alarmId = alarmId;
            this.occurrenceId = occurrenceId == null ? "" : occurrenceId;
            this.missedState = missedState;
            this.missedAtMs = missedAtMs;
            this.label = label == null ? "" : label;
        }

        public static MissedAlarmOutcome create(StoredAlarm alarm, MissedState state, long nowMs) {
            String outcomeId = alarm.id + ":" + alarm.occurrenceId + ":" + nowMs;
            return new MissedAlarmOutcome(
                    outcomeId, alarm.id, alarm.occurrenceId, state, nowMs, alarm.label);
        }
    }

    public static final class CapabilitySnapshot {
        public final boolean exactAlarmAvailable;
        public final boolean notificationsAvailable;
        public final boolean fullScreenAvailable;
        public final long evaluatedAtMs;

        public CapabilitySnapshot(
                boolean exactAlarmAvailable,
                boolean notificationsAvailable,
                boolean fullScreenAvailable,
                long evaluatedAtMs) {
            this.exactAlarmAvailable = exactAlarmAvailable;
            this.notificationsAvailable = notificationsAvailable;
            this.fullScreenAvailable = fullScreenAvailable;
            this.evaluatedAtMs = evaluatedAtMs;
        }
    }

    public static final class Registration {
        public final long alarmId;
        public final String occurrenceId;
        public final long triggerAtMs;
        public final RegistrationMode mode;
        public final int pendingIntentRequestCode;
        public final String pendingIntentAction;
        public final String pendingIntentData;
        public final String failureMessage;

        public Registration(
                long alarmId,
                String occurrenceId,
                long triggerAtMs,
                RegistrationMode mode,
                int pendingIntentRequestCode,
                String pendingIntentAction,
                String pendingIntentData,
                String failureMessage) {
            this.alarmId = alarmId;
            this.occurrenceId = occurrenceId == null ? "" : occurrenceId;
            this.triggerAtMs = triggerAtMs;
            this.mode = Objects.requireNonNull(mode);
            this.pendingIntentRequestCode = pendingIntentRequestCode;
            this.pendingIntentAction = pendingIntentAction == null ? "" : pendingIntentAction;
            this.pendingIntentData = pendingIntentData == null ? "" : pendingIntentData;
            this.failureMessage = failureMessage == null ? "" : failureMessage;
        }

        public boolean isRegistered() {
            return mode != RegistrationMode.NOT_REGISTERED;
        }
    }

    public static final class ReconcileReport {
        public final ReconcileReason reason;
        public final boolean completed;
        public final List<Registration> entries;
        public final List<Long> failedAlarmIds;
        public final long evaluatedAtMs;

        public ReconcileReport(
                ReconcileReason reason,
                boolean completed,
                List<Registration> entries,
                List<Long> failedAlarmIds,
                long evaluatedAtMs) {
            this.reason = Objects.requireNonNull(reason);
            this.completed = completed;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.failedAlarmIds = Collections.unmodifiableList(new ArrayList<>(failedAlarmIds));
            this.evaluatedAtMs = evaluatedAtMs;
        }
    }

    public static String createOccurrenceId(long alarmId, long generation, long triggerAtMs) {
        return alarmId + ":" + generation + ":" + triggerAtMs;
    }

    public static Instant instant(long epochMs) {
        return Instant.ofEpochMilli(epochMs);
    }

    public static ZoneId zone(String zoneId) {
        return ZoneId.of(zoneId);
    }
}
