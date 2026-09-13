#include <cstdio>

#include "core/src/holiday/holiday_engine_impl.h"
#include "core/src/scheduler/alarm_scheduler_impl.h"
#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/holiday_repository_impl.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;
using namespace edom::alarm::data;

bool TestSingleAdvanceSkip() {
    AlarmSchedulerImpl scheduler(nullptr);
    AlarmEntity alarm;
    alarm.hour = 8;
    alarm.minute = 0;
    alarm.repeat_mode = RepeatMode::kSpecificDays;
    alarm.days_bitmask = 0x3E;  // Monday through Friday (bit 1 to 5)

    // Base time: Monday 2026-06-01 07:00:00
    int64_t base_time = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 7, 0);

    // Without skip, it triggers on 2026-06-01 08:00
    int64_t normal_trigger = scheduler.CalculateNextTriggerTime(alarm, base_time, {});
    int64_t expected_monday = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 8, 0);
    EXPECT_EQ(normal_trigger, expected_monday);

    // With "Skip Today" (2026-06-01) in skip list
    std::vector<std::string> skip_list = {"2026-06-01"};
    int64_t skipped_trigger = scheduler.CalculateNextTriggerTime(alarm, base_time, skip_list);
    // MUST advance to Tuesday 2026-06-02 08:00!
    int64_t expected_tuesday = AlarmSchedulerImpl::ComputeEpochMs("2026-06-02", 8, 0);
    EXPECT_EQ(skipped_trigger, expected_tuesday);

    return true;
}

bool TestMultiDayVacationSkip() {
    AlarmSchedulerImpl scheduler(nullptr);
    AlarmEntity alarm;
    alarm.hour = 8;
    alarm.minute = 0;
    alarm.repeat_mode = RepeatMode::kSpecificDays;
    alarm.days_bitmask = 0x3E;  // Mon-Fri

    // Base time: 2026-06-01 07:00:00 (Monday)
    int64_t base_time = AlarmSchedulerImpl::ComputeEpochMs("2026-06-01", 7, 0);

    // Vacation skip: Mon 06-01, Tue 06-02, Wed 06-03
    std::vector<std::string> vacation_skips = {"2026-06-01", "2026-06-02", "2026-06-03"};
    int64_t next_trigger = scheduler.CalculateNextTriggerTime(alarm, base_time, vacation_skips);

    // MUST ring on Thursday 2026-06-04!
    int64_t expected_thursday = AlarmSchedulerImpl::ComputeEpochMs("2026-06-04", 8, 0);
    EXPECT_EQ(next_trigger, expected_thursday);

    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestSingleAdvanceSkip);
RUN_TEST(TestMultiDayVacationSkip);
TEST_MAIN_END
