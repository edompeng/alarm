package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReason;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlarmReconciliationPolicyTest {
    private AlarmReconciliationPolicyTest() {}

    public static void main(String[] args) {
        System.exit(AlarmDeliveryTestSupport.runSuite(
                "AlarmReconciliationPolicyTest", AlarmReconciliationPolicyTest::run));
    }

    private static void run() {
        FakeStore store = new FakeStore();
        store.save(alarm(1L, 0, 900L, "1:1:900"));
        store.save(alarm(2L, 1, 2_000L, "2:1:2000"));
        FakeGateway gateway = new FakeGateway();
        gateway.failAlarmId = 2L;
        NextOccurrenceCalculator calculator =
                (alarm, now, zone) -> alarm.id == 1L ? 0L : 5_000L;
        AlarmScheduleReconciler reconciler = new AlarmScheduleReconciler(
                store, calculator, gateway, () -> 1_000L, () -> ZoneId.of("Asia/Shanghai"));

        ReconcileReport report = reconciler.reconcile(ReconcileReason.APP_LAUNCH);
        AlarmDeliveryTestSupport.assertTrue(report.completed,
                "one registration failure does not interrupt reconciliation");
        AlarmDeliveryTestSupport.assertEquals(Collections.singletonList(2L), report.failedAlarmIds,
                "failed alarm IDs are retained");
        AlarmDeliveryTestSupport.assertEquals(1L, store.outcomes.size(),
                "expired one-time occurrence is queued as missed");
        AlarmDeliveryTestSupport.assertFalse(store.findById(1L).enabled,
                "expired one-time alarm is disabled instead of shifted to tomorrow");

        ReconcileReport second = reconciler.reconcile(ReconcileReason.APP_RESUME);
        AlarmDeliveryTestSupport.assertEquals(1L, store.outcomes.size(),
                "reconciliation does not duplicate the same missed outcome");
        AlarmDeliveryTestSupport.assertEquals(
                gateway.identities.get(gateway.identities.size() - 2),
                gateway.identities.get(gateway.identities.size() - 1),
                "repeated reconciliation replaces the same canonical registration");
        AlarmDeliveryTestSupport.assertEquals(1L, second.entries.size(),
                "report has one entry for every remaining enabled alarm");
        deletesExpiredAlarmsWhenConfigured();
    }

    /** With "delete expired alarms" enabled the record is removed after being recorded. */
    private static void deletesExpiredAlarmsWhenConfigured() {
        FakeStore store = new FakeStore();
        store.save(alarm(3L, 0, 900L, "3:1:900"));
        FakeGateway gateway = new FakeGateway();
        NextOccurrenceCalculator calculator = (alarm, now, zone) -> 0L;
        AlarmScheduleReconciler reconciler = new AlarmScheduleReconciler(
                store, calculator, gateway, () -> 1_000L, () -> ZoneId.of("Asia/Shanghai"),
                expired -> true);

        ReconcileReport report = reconciler.reconcile(ReconcileReason.APP_LAUNCH);

        AlarmDeliveryTestSupport.assertTrue(report.completed,
                "reconciliation still completes when expired alarms are deleted");
        AlarmDeliveryTestSupport.assertTrue(store.findById(3L) == null,
                "expired one-time alarm is deleted when the setting is enabled");
        AlarmDeliveryTestSupport.assertEquals(1L, store.outcomes.size(),
                "missed outcome is recorded before the alarm is deleted");
    }

    private static StoredAlarm alarm(long id, int repeatMode, long triggerAt, String occurrenceId) {
        return new StoredAlarm(id, 8, 0, true, repeatMode, repeatMode == 1 ? 127 : 0,
                "Alarm " + id, false, null, "Default", true, Collections.emptySet(),
                triggerAt, 1L, occurrenceId, "", MissedState.NONE, "");
    }

    private static final class FakeGateway implements AlarmRegistrationGateway {
        private long failAlarmId = -1L;
        private final List<String> identities = new ArrayList<>();

        @Override
        public Registration schedule(StoredAlarm alarm) {
            identities.add(alarm.occurrenceId);
            RegistrationMode mode = alarm.id == failAlarmId
                    ? RegistrationMode.NOT_REGISTERED : RegistrationMode.EXACT_ALARM_CLOCK;
            return new Registration(alarm.id, alarm.occurrenceId, alarm.nextTriggerAtMs, mode,
                    (int) alarm.id, AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER,
                    "alarm://trigger/" + alarm.id, mode == RegistrationMode.NOT_REGISTERED
                            ? "injected registration failure" : "");
        }

        @Override
        public void cancel(long alarmId) {}

        @Override
        public void scheduleAdvanceNotification(StoredAlarm alarm, long triggerAtMs) {}

        @Override
        public void cancelAdvanceNotification(long alarmId) {}
    }

    private static final class FakeStore implements AlarmScheduleStore {
        private final Map<Long, StoredAlarm> alarms = new LinkedHashMap<>();
        private final List<MissedAlarmOutcome> outcomes = new ArrayList<>();

        @Override
        public synchronized List<StoredAlarm> loadAll() {
            return new ArrayList<>(alarms.values());
        }

        @Override
        public synchronized StoredAlarm findById(long alarmId) {
            return alarms.get(alarmId);
        }

        @Override
        public synchronized void save(StoredAlarm alarm) {
            alarms.put(alarm.id, alarm);
        }

        @Override
        public synchronized void delete(long alarmId) {
            alarms.remove(alarmId);
        }

        @Override
        public synchronized ClaimResult claimOccurrence(
                long alarmId, String occurrenceId, long triggerAtMs, String localDate) {
            return ClaimResult.INVALID;
        }

        @Override
        public synchronized void applyMissedTransition(
                MissedAlarmOutcome outcome, StoredAlarm updatedAlarm, boolean deleteAlarm) {
            boolean exists = outcomes.stream().anyMatch(o -> o.outcomeId.equals(outcome.outcomeId));
            if (!exists) {
                outcomes.add(outcome);
            }
            if (deleteAlarm) {
                alarms.remove(outcome.alarmId);
            } else if (updatedAlarm != null) {
                alarms.put(updatedAlarm.id, updatedAlarm);
            }
        }

        @Override
        public synchronized List<MissedAlarmOutcome> loadPendingMissedOutcomes() {
            return new ArrayList<>(outcomes);
        }

        @Override
        public synchronized void acknowledgeMissedOutcome(String outcomeId) {
            outcomes.removeIf(outcome -> outcome.outcomeId.equals(outcomeId));
        }
    }
}
