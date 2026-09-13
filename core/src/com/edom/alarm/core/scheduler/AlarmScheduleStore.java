package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.List;

/** Durable alarm state and atomic occurrence-claim boundary. */
public interface AlarmScheduleStore {
    List<StoredAlarm> loadAll();

    StoredAlarm findById(long alarmId);

    void save(StoredAlarm alarm);

    void delete(long alarmId);

    ClaimResult claimOccurrence(
            long alarmId, String occurrenceId, long triggerAtMs, String localDate);

    /** Persists the outcome before applying the alarm update or deletion in one critical section. */
    void applyMissedTransition(
            MissedAlarmOutcome outcome, StoredAlarm updatedAlarm, boolean deleteAlarm);

    List<MissedAlarmOutcome> loadPendingMissedOutcomes();

    void acknowledgeMissedOutcome(String outcomeId);
}
