package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Sole core boundary for resolving a logical alarm to its next valid occurrence. */
public interface NextOccurrenceCalculator {
    interface HolidayCalendar {
        boolean isStatutoryWorkday(LocalDate date);
    }

    long calculateNextTriggerAtMs(StoredAlarm alarm, Instant now, ZoneId zoneId);
}
