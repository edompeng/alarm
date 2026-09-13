# Interface Contract: Holiday Engine (`IHolidayEngine`)

**Package**: `com.edom.alarm.core.holiday`
**Contract Type**: Domain Component & Remote Data Provider Interface

---

## 1. Overview
The `IHolidayEngine` determines whether a given date is a statutory working day, weekend, legal holiday, or compensatory working weekend (调休补班) based on State Council official schedules.

---

## 2. API Methods

```java
public interface IHolidayEngine {

    enum DayClassification {
        WORKDAY(0),
        WEEKEND(1),
        STATUTORY_HOLIDAY(2),
        COMPENSATORY_WORKDAY(3);

        private final int value;
        DayClassification(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    /**
     * Evaluates whether an alarm configured for "Statutory Workdays" should ring on the given date.
     * Rules:
     * - Returns true for normal Monday-Friday UNLESS it is a statutory holiday.
     * - Returns true for Saturday/Sunday IF it is a designated compensatory workday (调休补班).
     * - Returns false for all other dates.
     *
     * @param date Calendar date (LocalDate or 'YYYY-MM-DD')
     * @return True if alarm should ring; False if skipped
     */
    boolean isStatutoryWorkday(String date);

    /**
     * Returns the exact classification of the given date.
     *
     * @param date Calendar date string ('YYYY-MM-DD')
     * @return DayClassification enum
     */
    DayClassification classifyDate(String date);

    /**
     * Fetches and synchronizes holiday rules from the remote cloud endpoint.
     * Updates the local SQLite holiday_calendar table in a single atomic transaction.
     *
     * @param forceRefresh True to bypass cache validation
     * @return Result containing count of updated dates or error diagnostic
     */
    SyncResult syncHolidayRules(boolean forceRefresh);

    /**
     * Loads the bundled baseline holiday calendar from local assets if the database is empty.
     */
    void loadBundledBaselineIfEmpty();

    /**
     * Checks whether holiday data for the upcoming year is present.
     *
     * @param targetYear Year to inspect (e.g. 2027)
     * @return True if rules exist for targetYear
     */
    boolean hasYearData(int targetYear);
}
```

---

## 3. Remote Cloud Sync Schema (JSON)

```json
{
  "version": "2026.1",
  "published_at": "2025-11-20T10:00:00Z",
  "authority": "General Office of the State Council",
  "rules": [
    {
      "date": "2026-01-01",
      "type": 2,
      "name": "元旦"
    },
    {
      "date": "2026-02-15",
      "type": 3,
      "name": "春节补班"
    }
  ]
}
```
