# Interface Contract: Alarm Scheduler Engine (`IAlarmScheduler`)

**Package**: `com.edom.alarm.core.scheduler`
**Contract Type**: System Service & Domain Component Interface

---

## 1. Overview
The `IAlarmScheduler` manages next-occurrence resolution across recurrence rules, statutory holiday calendars, temporary skips, and snooze. Android framework registration, capability degradation, PendingIntent identity, ringing handoff, and recovery are defined by [alarm_delivery_contract.md](alarm_delivery_contract.md).

---

## 2. API Methods

```java
public interface IAlarmScheduler {

    /**
     * Schedules or reschedules the next occurrence for the specified alarm.
     * Calculates the next epoch trigger time taking into account holiday calendars,
     * day-of-week bitmasks, and temporary skip rules.
     *
     * @param alarmId Primary key of the alarm
     * @return Calculated epoch timestamp in milliseconds, or -1 if no future trigger
     */
    long scheduleAlarm(long alarmId);

    /**
     * Cancels an active system alarm and removes any scheduled advance notification.
     *
     * @param alarmId Primary key of the alarm
     */
    void cancelAlarm(long alarmId);

    /**
     * Reschedules all enabled alarms across the system.
     * Invoked after explicit application launch, device reboot, timezone/clock change,
     * package replacement, exact-alarm capability recovery, or holiday database sync.
     */
    void rescheduleAllAlarms();

    /**
     * Computes the exact next trigger time for an alarm without persisting it.
     *
     * @param hour Hour of day (0-23)
     * @param minute Minute of hour (0-59)
     * @param repeatMode 0=Once, 1=Custom Days, 2=Statutory Workdays
     * @param daysBitmask Bitmask of selected days
     * @param skipDates Set of ISO date strings ('YYYY-MM-DD') marked to skip
     * @param fromEpochMs Base time to calculate from
     * @return Epoch timestamp in milliseconds
     */
    long calculateNextTriggerTime(
        int hour,
        int minute,
        int repeatMode,
        int daysBitmask,
        Set<String> skipDates,
        long fromEpochMs
    );

    /**
     * Initiates snooze mode for an active ringing alarm.
     *
     * @param alarmId Primary key of the alarm
     * @param intervalMinutes Snooze duration in minutes
     * @param currentSnoozeCount Number of times already snoozed
     * @return True if snooze successfully scheduled; False if snooze limit exceeded
     */
    boolean snoozeAlarm(long alarmId, int intervalMinutes, int currentSnoozeCount);

    /**
     * Temporarily skips the immediate next occurrence of an alarm without disabling future repeats.
     *
     * @param alarmId Primary key of the alarm
     */
    void skipNextOccurrence(long alarmId);
}
```

---

## 3. Events & Intent Actions

| Action | Intent Extra Keys | Description |
| :--- | :--- | :--- |
| `com.edom.alarm.ACTION_ALARM_TRIGGER` | `EXTRA_ALARM_ID` (long), `EXTRA_OCCURRENCE_ID` (string), `EXTRA_TRIGGER_TIME` (long) | Canonical explicit broadcast operation fired by AlarmManager. Receiver validates persisted state and hands off to ringing execution/UI according to capability. |
| `com.edom.alarm.ACTION_ADVANCE_NOTIFICATION` | `EXTRA_ALARM_ID` (long), `EXTRA_TRIGGER_TIME` (long) | Fired 30-60 min prior to alarm. Displays advance skip card. |
| `com.edom.alarm.ACTION_SKIP_TODAY` | `EXTRA_ALARM_ID` (long) | Fired from the advance notification card button to dismiss the upcoming ring. |
| `com.edom.alarm.ACTION_SNOOZE` | `EXTRA_ALARM_ID` (long) | Fired to trigger snooze. |
| `com.edom.alarm.ACTION_DISMISS` | `EXTRA_ALARM_ID` (long) | Fired to terminate ringing and advance recurring schedule. |

---

## 4. Integration Invariants

1. Domain next-occurrence calculation contains no Android framework dependency.
2. Only the Android scheduler gateway defined in `alarm_delivery_contract.md` creates, replaces, or cancels AlarmManager operations.
3. Schedule and cancel use identical PendingIntent identity; extras are validated payload, not identity.
4. A recurring occurrence is not complete until its next valid occurrence is persisted/derived and registered.
5. Best-effort registration returns an explicit delivery mode and MUST NOT be reported as exact/full protection.
