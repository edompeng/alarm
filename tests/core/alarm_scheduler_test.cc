#include <cstdio>

#include "core/src/holiday/holiday_engine_impl.h"
#include "core/src/scheduler/alarm_scheduler_impl.h"
#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/holiday_repository_impl.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;
using namespace edom::alarm::data;

static const char* kTestDbPath = "test_alarm_scheduler.db";

bool TestSnoozeCalculation() {
    int64_t now_ms = 1774000000000LL;
    AlarmSchedulerImpl scheduler(nullptr);
    int64_t snooze_ms = scheduler.CalculateSnoozeTime(now_ms, 10);
    EXPECT_EQ(snooze_ms, now_ms + (10 * 60 * 1000));
    return true;
}

bool TestOnceAlarmCalculation() {
    AlarmSchedulerImpl scheduler(nullptr);
    AlarmEntity alarm;
    alarm.hour = 8;
    alarm.minute = 30;
    alarm.repeat_mode = RepeatMode::kOnce;

    // Suppose current time is 2026-06-01 07:00:00
    int64_t base_time = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 7, 0);
    int64_t next_trigger = scheduler.CalculateNextTriggerTime(alarm, base_time, {});
    int64_t expected = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 8, 30);
    EXPECT_EQ(next_trigger, expected);

    // Suppose current time is 2026-06-01 09:00:00 (already passed 08:30)
    int64_t passed_time = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 9, 0);
    int64_t next_trigger_tmrw = scheduler.CalculateNextTriggerTime(alarm, passed_time, {});
    int64_t expected_tmrw = AlarmSchedulerImpl::ComputeEpochMs("2026-06-02", 8, 30);
    EXPECT_EQ(next_trigger_tmrw, expected_tmrw);

    return true;
}

bool TestStatutoryWorkdaySchedulerWithHolidays() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper db_helper(kTestDbPath);
    EXPECT_TRUE(db_helper.Open());
    HolidayRepositoryImpl repo(&db_helper);
    HolidayEngineImpl engine(&repo);
    AlarmSchedulerImpl scheduler(&engine);

    // Add holiday rule: 2026-10-01 (Thursday) is National Day
    // 2026-10-02 (Friday) is Holiday
    // 2026-10-03 (Saturday) is Weekend
    // 2026-10-04 (Sunday) is Weekend
    // 2026-10-05 (Monday) is Holiday
    // 2026-10-06 (Tuesday) is Holiday
    // 2026-10-07 (Wednesday) is Holiday
    // 2026-10-08 (Thursday) is normal Workday
    // 2026-09-27 (Sunday) is Compensatory Workday
    HolidayEntity r1 = {"2026-09-27", 2026, DayType::kCompensatoryWorkday, "补班", 0};
    HolidayEntity r2 = {"2026-10-01", 2026, DayType::kStatutoryHoliday, "国庆", 0};
    HolidayEntity r3 = {"2026-10-02", 2026, DayType::kStatutoryHoliday, "国庆", 0};
    HolidayEntity r4 = {"2026-10-05", 2026, DayType::kStatutoryHoliday, "国庆", 0};
    HolidayEntity r5 = {"2026-10-06", 2026, DayType::kStatutoryHoliday, "国庆", 0};
    HolidayEntity r6 = {"2026-10-07", 2026, DayType::kStatutoryHoliday, "国庆", 0};
    EXPECT_TRUE(repo.BatchUpsertHolidayRules({r1, r2, r3, r4, r5, r6}));

    AlarmEntity alarm;
    alarm.hour = 8;
    alarm.minute = 0;
    alarm.repeat_mode = RepeatMode::kStatutoryWorkdays;

    // Test A: Current time is 2026-09-26 12:00:00 (Saturday).
    // Tomorrow is 2026-09-27 Sunday (Compensatory Workday). It should ring on Sunday!
    int64_t time_sat = AlarmSchedulerImpl::ComputeEpochMs("2026-09-26", 12, 0);
    int64_t trigger_sunday = scheduler.CalculateNextTriggerTime(alarm, time_sat, {});
    int64_t expected_sunday = AlarmSchedulerImpl::ComputeEpochMs("2026-09-27", 8, 0);
    EXPECT_EQ(trigger_sunday, expected_sunday);

    // Test B: Current time is 2026-09-30 20:00:00 (Wednesday night before Golden Week).
    // The entire week Oct 1 - Oct 7 is off. The next trigger MUST skip to 2026-10-08 Thursday!
    int64_t time_eve = AlarmSchedulerImpl::ComputeEpochMs("2026-09-30", 20, 0);
    int64_t trigger_after_holiday = scheduler.CalculateNextTriggerTime(alarm, time_eve, {});
    int64_t expected_oct8 = AlarmSchedulerImpl::ComputeEpochMs("2026-10-08", 8, 0);
    EXPECT_EQ(trigger_after_holiday, expected_oct8);

    db_helper.Close();
    std::remove(kTestDbPath);
    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestSnoozeCalculation);
RUN_TEST(TestOnceAlarmCalculation);
RUN_TEST(TestStatutoryWorkdaySchedulerWithHolidays);
TEST_MAIN_END
