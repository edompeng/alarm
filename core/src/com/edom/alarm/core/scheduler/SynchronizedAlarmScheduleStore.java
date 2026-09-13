package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Framework-free synchronized store semantics over an injected durable persistence adapter. */
public final class SynchronizedAlarmScheduleStore implements AlarmScheduleStore {
    public interface Persistence {
        List<StoredAlarm> readAlarms();

        void writeAlarms(List<StoredAlarm> alarms);

        List<MissedAlarmOutcome> readOutcomes();

        void writeOutcomes(List<MissedAlarmOutcome> outcomes);
    }

    private final Persistence persistence;

    public SynchronizedAlarmScheduleStore(Persistence persistence) {
        this.persistence = Objects.requireNonNull(persistence);
    }

    @Override
    public synchronized List<StoredAlarm> loadAll() {
        return new ArrayList<>(persistence.readAlarms());
    }

    @Override
    public synchronized StoredAlarm findById(long alarmId) {
        for (StoredAlarm alarm : persistence.readAlarms()) {
            if (alarm.id == alarmId) {
                return alarm;
            }
        }
        return null;
    }

    @Override
    public synchronized void save(StoredAlarm alarm) {
        List<StoredAlarm> alarms = new ArrayList<>(persistence.readAlarms());
        boolean replaced = false;
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).id == alarm.id) {
                alarms.set(i, alarm);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            alarms.add(alarm);
        }
        persistence.writeAlarms(alarms);
    }

    @Override
    public synchronized void delete(long alarmId) {
        List<StoredAlarm> alarms = new ArrayList<>(persistence.readAlarms());
        alarms.removeIf(alarm -> alarm.id == alarmId);
        persistence.writeAlarms(alarms);
    }

    @Override
    public synchronized ClaimResult claimOccurrence(
            long alarmId, String occurrenceId, long triggerAtMs, String localDate) {
        List<StoredAlarm> alarms = new ArrayList<>(persistence.readAlarms());
        for (int i = 0; i < alarms.size(); i++) {
            StoredAlarm alarm = alarms.get(i);
            if (alarm.id != alarmId) {
                continue;
            }
            boolean skipped = localDate != null && alarm.skippedDates.contains(localDate);
            if (triggerAtMs != alarm.nextTriggerAtMs) {
                return ClaimResult.STALE;
            }
            AlarmDeliveryPolicy.ClaimDecision decision =
                    AlarmDeliveryPolicy.claimOccurrence(alarm, occurrenceId, skipped);
            if (decision.result == ClaimResult.CLAIMED) {
                alarms.set(i, decision.updatedAlarm);
                persistence.writeAlarms(alarms);
            }
            return decision.result;
        }
        return ClaimResult.MISSING;
    }

    @Override
    public synchronized void applyMissedTransition(
            MissedAlarmOutcome outcome, StoredAlarm updatedAlarm, boolean deleteAlarm) {
        List<MissedAlarmOutcome> outcomes = new ArrayList<>(persistence.readOutcomes());
        boolean duplicate = outcomes.stream()
                .anyMatch(existing -> existing.outcomeId.equals(outcome.outcomeId));
        if (!duplicate) {
            outcomes.add(outcome);
            persistence.writeOutcomes(outcomes);
        }
        if (deleteAlarm) {
            delete(outcome.alarmId);
        } else if (updatedAlarm != null) {
            save(updatedAlarm);
        }
    }

    @Override
    public synchronized List<MissedAlarmOutcome> loadPendingMissedOutcomes() {
        return new ArrayList<>(persistence.readOutcomes());
    }

    @Override
    public synchronized void acknowledgeMissedOutcome(String outcomeId) {
        List<MissedAlarmOutcome> outcomes = new ArrayList<>(persistence.readOutcomes());
        if (outcomes.removeIf(outcome -> outcome.outcomeId.equals(outcomeId))) {
            persistence.writeOutcomes(outcomes);
        }
    }
}
