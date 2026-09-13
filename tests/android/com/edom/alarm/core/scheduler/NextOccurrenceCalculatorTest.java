package com.edom.alarm.core.scheduler;

import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Failure-first tests for the sole calendar and timezone occurrence authority. */
public final class NextOccurrenceCalculatorTest {
  private static final ZoneId UTC = ZoneId.of("UTC");

  private NextOccurrenceCalculatorTest() {}

  public static void main(String[] args) {
    System.exit(
        AlarmDeliveryTestSupport.runSuite(
            "NextOccurrenceCalculatorTest", NextOccurrenceCalculatorTest::runAll));
  }

  private static void runAll() {
    resolvesCustomDayBitmaskUsingSundayAsBitZero();
    appliesStatutoryHolidaysAndMakeupWorkdays();
    skipsExplicitlySkippedDates();
    doesNotResurrectAnExpiredQuickNap();
    recalculatesUsingTheSuppliedZone();
    resolvesDstGapsAndOverlapsDeterministically();
  }

  private static void resolvesCustomDayBitmaskUsingSundayAsBitZero() {
    NextOccurrenceCalculator calculator = new DefaultNextOccurrenceCalculator(new FakeHolidayCalendar());
    StoredAlarm mondayOnly = alarm(1, 1 << 1, false, Collections.emptySet(), 9, 0);

    long trigger =
        calculator.calculateNextTriggerAtMs(
            mondayOnly, Instant.parse("2026-06-07T10:00:00Z"), UTC);

    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-08T09:00:00Z").toEpochMilli(),
        trigger,
        "bit one selects Monday when bit zero is Sunday");
  }

  private static void appliesStatutoryHolidaysAndMakeupWorkdays() {
    StoredAlarm statutory = alarm(2, 0, false, Collections.emptySet(), 8, 0);

    FakeHolidayCalendar holidayCalendar = new FakeHolidayCalendar();
    holidayCalendar.addWorkday(LocalDate.of(2026, 6, 10));
    NextOccurrenceCalculator holidayCalculator = new DefaultNextOccurrenceCalculator(holidayCalendar);

    long afterHoliday =
        holidayCalculator.calculateNextTriggerAtMs(
            statutory, Instant.parse("2026-06-07T12:00:00Z"), UTC);
    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-10T08:00:00Z").toEpochMilli(),
        afterHoliday,
        "statutory holidays and rest days are skipped until the next workday");

    FakeHolidayCalendar makeupCalendar = new FakeHolidayCalendar();
    makeupCalendar.addWorkday(LocalDate.of(2026, 6, 13));
    NextOccurrenceCalculator makeupCalculator = new DefaultNextOccurrenceCalculator(makeupCalendar);

    long makeupSaturday =
        makeupCalculator.calculateNextTriggerAtMs(
            statutory, Instant.parse("2026-06-12T12:00:00Z"), UTC);
    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-13T08:00:00Z").toEpochMilli(),
        makeupSaturday,
        "calendar-provided make-up Saturday is a statutory workday");
  }

  private static void skipsExplicitlySkippedDates() {
    NextOccurrenceCalculator calculator = new DefaultNextOccurrenceCalculator(new FakeHolidayCalendar());
    Set<String> skippedDates = new HashSet<>();
    skippedDates.add("2026-06-08");
    StoredAlarm daily = alarm(1, 0x7f, false, skippedDates, 9, 0);

    long trigger =
        calculator.calculateNextTriggerAtMs(daily, Instant.parse("2026-06-07T12:00:00Z"), UTC);

    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-09T09:00:00Z").toEpochMilli(),
        trigger,
        "skip date excludes an otherwise matching occurrence");
  }

  private static void doesNotResurrectAnExpiredQuickNap() {
    NextOccurrenceCalculator calculator = new DefaultNextOccurrenceCalculator(new FakeHolidayCalendar());
    StoredAlarm quickNap = alarm(0, 0, true, Collections.emptySet(), 9, 0);

    long trigger =
        calculator.calculateNextTriggerAtMs(quickNap, Instant.parse("2026-06-08T10:00:00Z"), UTC);

    AlarmDeliveryTestSupport.assertEquals(0L, trigger, "expired Quick Nap has no replacement occurrence");
  }

  private static void recalculatesUsingTheSuppliedZone() {
    NextOccurrenceCalculator calculator = new DefaultNextOccurrenceCalculator(new FakeHolidayCalendar());
    StoredAlarm daily = alarm(1, 0x7f, false, Collections.emptySet(), 9, 0);
    Instant now = Instant.parse("2026-06-01T00:30:00Z");

    long shanghai = calculator.calculateNextTriggerAtMs(daily, now, ZoneId.of("Asia/Shanghai"));
    long newYork = calculator.calculateNextTriggerAtMs(daily, now, ZoneId.of("America/New_York"));

    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-01T01:00:00Z").toEpochMilli(), shanghai, "Shanghai local time is used");
    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-06-01T13:00:00Z").toEpochMilli(), newYork, "New York local time is used");
  }

  private static void resolvesDstGapsAndOverlapsDeterministically() {
    NextOccurrenceCalculator calculator = new DefaultNextOccurrenceCalculator(new FakeHolidayCalendar());
    ZoneId newYork = ZoneId.of("America/New_York");

    long gap =
        calculator.calculateNextTriggerAtMs(
            alarm(1, 0x7f, false, Collections.emptySet(), 2, 30),
            Instant.parse("2026-03-08T05:00:00Z"),
            newYork);
    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(),
        gap,
        "DST gap shifts the local 02:30 occurrence forward");

    long overlap =
        calculator.calculateNextTriggerAtMs(
            alarm(1, 0x7f, false, Collections.emptySet(), 1, 30),
            Instant.parse("2026-11-01T04:00:00Z"),
            newYork);
    AlarmDeliveryTestSupport.assertEquals(
        Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(),
        overlap,
        "DST overlap chooses the earlier offset deterministically");
  }

  private static StoredAlarm alarm(
      int repeatMode,
      int daysBitmask,
      boolean quickNap,
      Set<String> skippedDates,
      int hour,
      int minute) {
    return new StoredAlarm(
        7L,
        hour,
        minute,
        true,
        repeatMode,
        daysBitmask,
        "Calendar",
        quickNap,
        "",
        "",
        true,
        skippedDates,
        0L,
        0L,
        "",
        "",
        MissedState.NONE,
        "{}");
  }

  private static final class FakeHolidayCalendar implements NextOccurrenceCalculator.HolidayCalendar {
    private final Set<LocalDate> workdays = new HashSet<>();

    void addWorkday(LocalDate date) {
      workdays.add(date);
    }

    @Override
    public boolean isStatutoryWorkday(LocalDate date) {
      return workdays.contains(date);
    }
  }
}
