package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.Registration;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;

/** Android scheduling boundary consumed by reconciliation and UI flows. */
public interface AlarmRegistrationGateway {
    Registration schedule(StoredAlarm alarm);

    void cancel(long alarmId);

    void scheduleAdvanceNotification(StoredAlarm alarm, long triggerAtMs);

    void cancelAdvanceNotification(long alarmId);
}
