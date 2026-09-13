package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.CapabilitySnapshot;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ProtectionLevel;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReason;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ReconcileReport;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.RegistrationMode;
import java.util.Arrays;
import java.util.Collections;

public final class AlarmProtectionPolicyTest {
    private AlarmProtectionPolicyTest() {}

    public static void main(String[] args) {
        System.exit(AlarmDeliveryTestSupport.runSuite(
                "AlarmProtectionPolicyTest", AlarmProtectionPolicyTest::run));
    }

    private static void run() {
        CapabilitySnapshot fullCapabilities = new CapabilitySnapshot(true, true, true, 100L);
        Registration success = new Registration(
                7L, "7:1:1000", 1000L, RegistrationMode.EXACT_ALARM_CLOCK, 7,
                AlarmDeliveryPolicy.ACTION_ALARM_TRIGGER, "alarm://trigger/7", "");
        ReconcileReport complete = new ReconcileReport(
                ReconcileReason.APP_RESUME, true, Collections.singletonList(success),
                Collections.emptyList(), 100L);

        AlarmDeliveryTestSupport.assertEquals(
                ProtectionLevel.FULL,
                AlarmDeliveryPolicy.deriveProtection(fullCapabilities, complete, Arrays.asList(7L)),
                "all capabilities and registrations produce full protection");
        AlarmDeliveryTestSupport.assertEquals(
                ProtectionLevel.LIMITED,
                AlarmDeliveryPolicy.deriveProtection(
                        new CapabilitySnapshot(false, true, true, 100L), complete, Arrays.asList(7L)),
                "missing exact capability remains limited");
        AlarmDeliveryTestSupport.assertEquals(
                ProtectionLevel.LIMITED,
                AlarmDeliveryPolicy.deriveProtection(fullCapabilities,
                        new ReconcileReport(ReconcileReason.APP_RESUME, false,
                                Collections.singletonList(success), Collections.emptyList(), 100L),
                        Arrays.asList(7L)),
                "incomplete reconciliation remains limited");
        AlarmDeliveryTestSupport.assertEquals(
                ProtectionLevel.LIMITED,
                AlarmDeliveryPolicy.deriveProtection(fullCapabilities,
                        new ReconcileReport(ReconcileReason.APP_RESUME, true,
                                Collections.singletonList(success), Arrays.asList(8L), 100L),
                        Arrays.asList(7L, 8L)),
                "failed or missing alarm registration remains limited");
        AlarmDeliveryTestSupport.assertTrue(
                AlarmDeliveryPolicy.isForceStopGuidanceRequired(30)
                        && AlarmDeliveryPolicy.isForceStopGuidanceRequired(34),
                "force-stop guidance remains reachable on every supported API");
    }
}
