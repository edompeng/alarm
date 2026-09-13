package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.Collections;

/** Failure-first policy tests for durable occurrence creation, claiming, and expiry. */
public final class AlarmOccurrencePolicyTest {
  private static final long ALARM_ID = 42L;
  private static final long FIRST_TRIGGER_MS = 1_000L;

  private AlarmOccurrencePolicyTest() {}

  public static void main(String[] args) {
    System.exit(
        AlarmDeliveryTestSupport.runSuite(
            "AlarmOccurrencePolicyTest", AlarmOccurrencePolicyTest::runAll));
  }

  private static void runAll() {
    upgradesLegacyDefaultsBeforeRegistration();
    createsStableIncrementingOccurrenceIds();
    rejectsStaleAndDuplicatePayloads();
    classifiesExpiredOnceQuickNapAndRecurringOccurrences();
  }

  private static void upgradesLegacyDefaultsBeforeRegistration() {
    StoredAlarm legacy = alarm(false, 0L, 0L, "", "");

    StoredAlarm upgraded = AlarmDeliveryPolicy.upgradeLegacyOccurrence(legacy, FIRST_TRIGGER_MS);

    AlarmDeliveryTestSupport.assertEquals(
        FIRST_TRIGGER_MS, upgraded.nextTriggerAtMs, "legacy record receives its future trigger");
    AlarmDeliveryTestSupport.assertEquals(
        1L, upgraded.occurrenceGeneration, "legacy generation starts at one");
    AlarmDeliveryTestSupport.assertEquals(
        "42:1:1000", upgraded.occurrenceId, "legacy record receives canonical occurrence id");
    AlarmDeliveryTestSupport.assertEquals(
        "", upgraded.lastClaimedOccurrenceId, "upgrade leaves occurrence unclaimed");
  }

  private static void createsStableIncrementingOccurrenceIds() {
    StoredAlarm first = alarm(false, FIRST_TRIGGER_MS, 1L, "42:1:1000", "");

    StoredAlarm second = AlarmDeliveryPolicy.createNextOccurrence(first, 2_000L);

    AlarmDeliveryTestSupport.assertEquals(2L, second.occurrenceGeneration, "generation increments");
    AlarmDeliveryTestSupport.assertEquals(
        "42:2:2000", second.occurrenceId, "new occurrence id includes alarm, generation, and trigger");
    AlarmDeliveryTestSupport.assertEquals("", second.lastClaimedOccurrenceId, "new occurrence is unclaimed");
  }

  private static void rejectsStaleAndDuplicatePayloads() {
    StoredAlarm current = alarm(false, FIRST_TRIGGER_MS, 1L, "42:1:1000", "");

    AlarmDeliveryPolicy.ClaimDecision stale =
        AlarmDeliveryPolicy.claimOccurrence(current, "42:0:999", false);
    AlarmDeliveryTestSupport.assertEquals(
        ClaimResult.STALE, stale.result, "old delivery payload must be stale");

    AlarmDeliveryPolicy.ClaimDecision first =
        AlarmDeliveryPolicy.claimOccurrence(current, current.occurrenceId, false);
    AlarmDeliveryTestSupport.assertEquals(ClaimResult.CLAIMED, first.result, "matching payload claims once");
    AlarmDeliveryTestSupport.assertEquals(
        current.occurrenceId,
        first.updatedAlarm.lastClaimedOccurrenceId,
        "claim is persisted in the decision state");

    AlarmDeliveryPolicy.ClaimDecision duplicate =
        AlarmDeliveryPolicy.claimOccurrence(first.updatedAlarm, current.occurrenceId, false);
    AlarmDeliveryTestSupport.assertEquals(
        ClaimResult.ALREADY_CLAIMED, duplicate.result, "duplicate delivery cannot ring twice");
  }

  private static void classifiesExpiredOnceQuickNapAndRecurringOccurrences() {
    AlarmDeliveryPolicy.ExpirationDecision once =
        AlarmDeliveryPolicy.expireOccurrence(alarm(false, FIRST_TRIGGER_MS, 1L, "42:1:1000", ""), 1_001L);
    AlarmDeliveryTestSupport.assertEquals(
        MissedState.MISSED_ONCE, once.missedState, "expired one-time alarm is surfaced as missed");
    AlarmDeliveryTestSupport.assertEquals(
        AlarmDeliveryPolicy.ExpirationAction.DISABLE,
        once.action,
        "expired one-time alarm is disabled after durable outcome persistence");

    AlarmDeliveryPolicy.ExpirationDecision quickNap =
        AlarmDeliveryPolicy.expireOccurrence(alarm(true, FIRST_TRIGGER_MS, 1L, "42:1:1000", ""), 1_001L);
    AlarmDeliveryTestSupport.assertEquals(
        MissedState.MISSED_QUICK_NAP, quickNap.missedState, "expired Quick Nap is surfaced as missed");
    AlarmDeliveryTestSupport.assertEquals(
        AlarmDeliveryPolicy.ExpirationAction.DELETE,
        quickNap.action,
        "Quick Nap is deleted only after its missed outcome is recorded");

    AlarmDeliveryPolicy.ExpirationDecision recurring =
        AlarmDeliveryPolicy.expireOccurrence(recurringAlarm(), 1_001L);
    AlarmDeliveryTestSupport.assertEquals(
        MissedState.MISSED_RECURRING,
        recurring.missedState,
        "expired recurring alarm is surfaced as missed");
    AlarmDeliveryTestSupport.assertEquals(
        AlarmDeliveryPolicy.ExpirationAction.ADVANCE_RECURRING,
        recurring.action,
        "expired recurring alarm advances instead of replaying retroactively");
  }

  private static StoredAlarm recurringAlarm() {
    return new StoredAlarm(
        ALARM_ID,
        7,
        30,
        true,
        1,
        0x7f,
        "Recurring",
        false,
        "",
        "",
        true,
        Collections.emptySet(),
        FIRST_TRIGGER_MS,
        1L,
        "42:1:1000",
        "",
        MissedState.NONE,
        "{}");
  }

  private static StoredAlarm alarm(
      boolean quickNap,
      long triggerAtMs,
      long generation,
      String occurrenceId,
      String lastClaimedOccurrenceId) {
    return new StoredAlarm(
        ALARM_ID,
        7,
        30,
        true,
        0,
        0,
        "One time",
        quickNap,
        "",
        "",
        true,
        Collections.emptySet(),
        triggerAtMs,
        generation,
        occurrenceId,
        lastClaimedOccurrenceId,
        MissedState.NONE,
        "{}");
  }
}
