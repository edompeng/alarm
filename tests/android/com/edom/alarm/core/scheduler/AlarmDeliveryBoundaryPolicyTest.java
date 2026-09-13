package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;

public final class AlarmDeliveryBoundaryPolicyTest {
    private AlarmDeliveryBoundaryPolicyTest() {}

    public static void main(String[] args) {
        System.exit(AlarmDeliveryTestSupport.runSuite(
                "AlarmDeliveryBoundaryPolicyTest", AlarmDeliveryBoundaryPolicyTest::run));
    }

    private static void run() {
        AlarmDeliveryPolicy.PendingIntentIdentity identity =
                AlarmDeliveryPolicy.triggerIdentity(42L);
        AlarmDeliveryTestSupport.assertEquals(42L, identity.requestCode,
                "request code is deterministic");
        AlarmDeliveryTestSupport.assertEquals(AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER,
                identity.action, "trigger action is canonical");
        AlarmDeliveryTestSupport.assertEquals("alarm://trigger/42", identity.dataUri,
                "data URI is stable");
        AlarmDeliveryTestSupport.assertTrue(identity.immutable && identity.updateCurrent,
                "identity is immutable and payload-updating");

        AlarmDeliveryTestSupport.assertEquals(
                AlarmDeliveryPolicy.HandoffDecision.START_SERVICE,
                AlarmDeliveryPolicy.decideHandoff(false,
                        new CapabilitySnapshot(true, true, true, 0L)),
                "normal handoff starts the bounded service");
        AlarmDeliveryTestSupport.assertEquals(
                AlarmDeliveryPolicy.HandoffDecision.POST_NOTIFICATION_FALLBACK,
                AlarmDeliveryPolicy.decideHandoff(true,
                        new CapabilitySnapshot(false, true, false, 0L)),
                "rejected service start uses a permitted notification fallback");
        AlarmDeliveryTestSupport.assertEquals(
                AlarmDeliveryPolicy.HandoffDecision.RECORD_UNDELIVERABLE,
                AlarmDeliveryPolicy.decideHandoff(true,
                        new CapabilitySnapshot(false, false, false, 0L)),
                "no permitted surface records an undeliverable attempt");
    }
}
