package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/** Deterministic recurrence calculator with no Android framework dependency. */
public final class DefaultNextOccurrenceCalculator implements NextOccurrenceCalculator {
    private static final int MAX_SEARCH_DAYS = 370;
    private final HolidayCalendar holidayCalendar;

    public DefaultNextOccurrenceCalculator(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = Objects.requireNonNull(holidayCalendar);
    }

    @Override
    public long calculateNextTriggerAtMs(StoredAlarm alarm, Instant now, ZoneId zoneId) {
        Objects.requireNonNull(alarm);
        Objects.requireNonNull(now);
        Objects.requireNonNull(zoneId);
        if (!alarm.enabled) {
            return 0L;
        }
        if (alarm.quickNap) {
            return alarm.nextTriggerAtMs > now.toEpochMilli() ? alarm.nextTriggerAtMs : 0L;
        }

        ZonedDateTime zonedNow = now.atZone(zoneId);
        LocalDate startDate = zonedNow.toLocalDate();
        LocalTime alarmTime = LocalTime.of(alarm.hour, alarm.minute);
        for (int offset = 0; offset <= MAX_SEARCH_DAYS; offset++) {
            LocalDate candidateDate = startDate.plusDays(offset);
            if (!matchesRecurrence(alarm, candidateDate)
                    || alarm.skippedDates.contains(candidateDate.toString())) {
                continue;
            }
            ZonedDateTime candidate = ZonedDateTime.of(
                    LocalDateTime.of(candidateDate, alarmTime), zoneId);
            if (candidate.toInstant().isAfter(now)) {
                return candidate.toInstant().toEpochMilli();
            }
        }
        return 0L;
    }

    private boolean matchesRecurrence(StoredAlarm alarm, LocalDate date) {
        if (alarm.repeatMode == 0) {
            return true;
        }
        if (alarm.repeatMode == 2) {
            return holidayCalendar.isStatutoryWorkday(date);
        }
        int sundayBasedIndex = date.getDayOfWeek().getValue() % 7;
        return (alarm.daysBitmask & (1 << sundayBasedIndex)) != 0;
    }
}
