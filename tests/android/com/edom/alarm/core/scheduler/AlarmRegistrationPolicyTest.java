package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;

/** Failure-first tests for registration degradation and canonical Android-operation identity. */
public final class AlarmRegistrationPolicyTest {
  private AlarmRegistrationPolicyTest() {}

  public static void main(String[] args) {
    System.exit(
        AlarmDeliveryTestSupport.runSuite(
            "AlarmRegistrationPolicyTest", AlarmRegistrationPolicyTest::runAll));
  }

  private static void runAll() {
    selectsExactOrBestEffortForAValidFutureOccurrence();
    rejectsDisabledOrNonFutureOccurrences();
    createsOneStableExplicitTriggerDescriptorPerAlarm();
  }

  private static void selectsExactOrBestEffortForAValidFutureOccurrence() {
    AlarmDeliveryTestSupport.assertEquals(
        RegistrationMode.EXACT_ALARM_CLOCK,
        AlarmDeliveryPolicy.chooseRegistrationMode(true, true, 2_000L, 1_000L),
        "exact capability selects alarm-clock registration");
    AlarmDeliveryTestSupport.assertEquals(
        RegistrationMode.BEST_EFFORT_IDLE_ALLOWED,
        AlarmDeliveryPolicy.chooseRegistrationMode(false, true, 2_000L, 1_000L),
        "missing exact capability selects best-effort idle-allowed registration");
  }

  private static void rejectsDisabledOrNonFutureOccurrences() {
    AlarmDeliveryTestSupport.assertEquals(
        RegistrationMode.NOT_REGISTERED,
        AlarmDeliveryPolicy.chooseRegistrationMode(true, false, 2_000L, 1_000L),
        "disabled alarm cannot register");
    AlarmDeliveryTestSupport.assertEquals(
        RegistrationMode.NOT_REGISTERED,
        AlarmDeliveryPolicy.chooseRegistrationMode(true, true, 1_000L, 1_000L),
        "expired occurrence cannot register");
  }

  private static void createsOneStableExplicitTriggerDescriptorPerAlarm() {
    AlarmDeliveryPolicy.PendingIntentIdentity first = AlarmDeliveryPolicy.triggerIdentity(88L);
    AlarmDeliveryPolicy.PendingIntentIdentity sameAlarm = AlarmDeliveryPolicy.triggerIdentity(88L);
    AlarmDeliveryPolicy.PendingIntentIdentity anotherAlarm = AlarmDeliveryPolicy.triggerIdentity(89L);

    AlarmDeliveryTestSupport.assertEquals(
        "com.edom.alarm.core.scheduler.AlarmTriggerReceiver",
        first.receiverClassName,
        "trigger is explicit to AlarmTriggerReceiver");
    AlarmDeliveryTestSupport.assertEquals(
        AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER, first.action, "trigger action is canonical");
    AlarmDeliveryTestSupport.assertEquals(
        "alarm://trigger/88", first.dataUri, "trigger data URI is alarm-specific and stable");
    AlarmDeliveryTestSupport.assertEquals(
        88L, first.requestCode, "request code is a deterministic conversion of the alarm id");
    AlarmDeliveryTestSupport.assertEquals(
        first.requestCode, sameAlarm.requestCode, "schedule and cancel share request code");
    AlarmDeliveryTestSupport.assertEquals(
        first.dataUri, sameAlarm.dataUri, "schedule and cancel share data URI");
    AlarmDeliveryTestSupport.assertFalse(
        first.dataUri.equals(anotherAlarm.dataUri), "different alarm ids have distinct data identities");
    AlarmDeliveryTestSupport.assertTrue(
        first.immutable && first.updateCurrent,
        "descriptor includes immutable and update-current flags");
  }
}
