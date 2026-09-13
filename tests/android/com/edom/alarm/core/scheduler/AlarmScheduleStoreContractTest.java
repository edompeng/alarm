package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class AlarmScheduleStoreContractTest {
    private static final String LEGACY_FIXTURE =
            "{\"id\":7,\"hour\":6,\"minute\":30,\"isEnabled\":true,"
                    + "\"repeatMode\":1,\"daysBitmask\":62,\"label\":\"Work\","
                    + "\"isQuickNap\":false,\"ringtoneUri\":\"content://tone\","
                    + "\"ringtoneTitle\":\"Tone\",\"vibrateEnabled\":true,"
                    + "\"skippedDates\":[\"2026-09-15\"],\"futureKey\":\"keep\"}";

    private AlarmScheduleStoreContractTest() {}

    public static void main(String[] args) {
        System.exit(AlarmDeliveryTestSupport.runSuite(
                "AlarmScheduleStoreContractTest", AlarmScheduleStoreContractTest::run));
    }

    private static void run() throws Exception {
        MemoryPersistence persistence = new MemoryPersistence();
        SynchronizedAlarmScheduleStore store = new SynchronizedAlarmScheduleStore(persistence);
        StoredAlarm alarm = alarm(LEGACY_FIXTURE).withOccurrence(2_000L, 1L);
        store.save(alarm);
        AlarmDeliveryTestSupport.assertEquals(LEGACY_FIXTURE, store.findById(7L).sourceJson,
                "the exact production fixture and unknown key survive round-trip");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger claimed = new AtomicInteger();
        Runnable claimant = () -> {
            ready.countDown();
            try {
                start.await();
                if (store.claimOccurrence(7L, "7:1:2000", 2_000L, "2026-09-14")
                        == ClaimResult.CLAIMED) {
                    claimed.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Thread first = new Thread(claimant);
        Thread second = new Thread(claimant);
        first.start();
        second.start();
        ready.await();
        start.countDown();
        first.join();
        second.join();
        AlarmDeliveryTestSupport.assertEquals(1L, claimed.get(),
                "exactly one concurrent occurrence claimant wins");

        MissedAlarmOutcome outcome = MissedAlarmOutcome.create(
                store.findById(7L), MissedState.MISSED_QUICK_NAP, 3_000L);
        store.applyMissedTransition(outcome, null, true);
        AlarmDeliveryTestSupport.assertEquals(1L, store.loadPendingMissedOutcomes().size(),
                "outcome persists before alarm deletion");
        AlarmDeliveryTestSupport.assertEquals(null, store.findById(7L),
                "quick nap can be deleted after outcome persistence");
        store.acknowledgeMissedOutcome(outcome.outcomeId);
        store.acknowledgeMissedOutcome(outcome.outcomeId);
        AlarmDeliveryTestSupport.assertTrue(store.loadPendingMissedOutcomes().isEmpty(),
                "acknowledgement is idempotent");
    }

    private static StoredAlarm alarm(String sourceJson) {
        return new StoredAlarm(7L, 6, 30, true, 1, 62, "Work", false,
                "content://tone", "Tone", true,
                Collections.singleton("2026-09-15"), 0L, 0L, "", "",
                MissedState.NONE, sourceJson);
    }

    private static final class MemoryPersistence
            implements SynchronizedAlarmScheduleStore.Persistence {
        private List<StoredAlarm> alarms = new ArrayList<>();
        private List<MissedAlarmOutcome> outcomes = new ArrayList<>();

        @Override
        public List<StoredAlarm> readAlarms() {
            return new ArrayList<>(alarms);
        }

        @Override
        public void writeAlarms(List<StoredAlarm> value) {
            alarms = new ArrayList<>(value);
        }

        @Override
        public List<MissedAlarmOutcome> readOutcomes() {
            return new ArrayList<>(outcomes);
        }

        @Override
        public void writeOutcomes(List<MissedAlarmOutcome> value) {
            outcomes = new ArrayList<>(value);
        }
    }
}
