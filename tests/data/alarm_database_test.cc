#include <cstdio>

#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/alarm_repository_impl.h"
#include "data/src/repository/holiday_repository_impl.h"
#include "tests/test_framework.h"

using namespace edom::alarm::data;

static const char* kTestDbPath = "test_smart_alarm.db";

bool TestAlarmEntityValidation() {
    AlarmEntity valid_alarm;
    valid_alarm.hour = 7;
    valid_alarm.minute = 30;
    valid_alarm.volume = 90;
    valid_alarm.crescendo_seconds = 20;
    valid_alarm.vibration_intensity = 80;
    valid_alarm.snooze_interval_minutes = 10;
    EXPECT_TRUE(valid_alarm.IsValid());

    AlarmEntity invalid_hour = valid_alarm;
    invalid_hour.hour = 25;
    EXPECT_FALSE(invalid_hour.IsValid());

    AlarmEntity invalid_minute = valid_alarm;
    invalid_minute.minute = 65;
    EXPECT_FALSE(invalid_minute.IsValid());

    AlarmEntity invalid_volume = valid_alarm;
    invalid_volume.volume = 120;
    EXPECT_FALSE(invalid_volume.IsValid());

    AlarmEntity invalid_crescendo = valid_alarm;
    invalid_crescendo.crescendo_seconds = 45;
    EXPECT_FALSE(invalid_crescendo.IsValid());

    AlarmEntity invalid_snooze = valid_alarm;
    invalid_snooze.snooze_interval_minutes = 0;
    EXPECT_FALSE(invalid_snooze.IsValid());

    return true;
}

bool TestDatabaseOpenAndTables() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper helper(kTestDbPath);
    EXPECT_TRUE(helper.Open());
    EXPECT_TRUE(helper.IsOpen());
    helper.Close();
    EXPECT_FALSE(helper.IsOpen());
    std::remove(kTestDbPath);
    return true;
}

bool TestAlarmCrudOperations() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper helper(kTestDbPath);
    EXPECT_TRUE(helper.Open());
    AlarmRepositoryImpl repo(&helper);

    AlarmEntity alarm;
    alarm.label = "Morning Workday Alarm";
    alarm.hour = 8;
    alarm.minute = 15;
    alarm.repeat_mode = RepeatMode::kStatutoryWorkdays;
    alarm.volume = 85;
    alarm.crescendo_seconds = 15;
    alarm.vibration_pattern = "HEARTBEAT";
    alarm.force_speaker = true;

    int64_t id = repo.InsertAlarm(alarm);
    EXPECT_TRUE(id > 0);

    AlarmEntity fetched;
    EXPECT_TRUE(repo.GetAlarmById(id, &fetched));
    EXPECT_EQ(fetched.id, id);
    EXPECT_EQ(fetched.label, std::string("Morning Workday Alarm"));
    EXPECT_EQ(fetched.hour, 8);
    EXPECT_EQ(fetched.minute, 15);
    EXPECT_TRUE(fetched.force_speaker);

    // Update alarm
    fetched.hour = 9;
    fetched.volume = 100;
    EXPECT_TRUE(repo.UpdateAlarm(fetched));

    AlarmEntity updated;
    EXPECT_TRUE(repo.GetAlarmById(id, &updated));
    EXPECT_EQ(updated.hour, 9);
    EXPECT_EQ(updated.volume, 100);

    // Skip rules
    EXPECT_TRUE(repo.AddSkipDate(id, "2026-10-01"));
    EXPECT_TRUE(repo.AddSkipDate(id, "2026-10-02"));

    std::vector<std::string> skip_dates;
    EXPECT_TRUE(repo.GetSkipDates(id, &skip_dates));
    EXPECT_EQ(skip_dates.size(), 2);
    EXPECT_EQ(skip_dates[0], std::string("2026-10-01"));
    EXPECT_EQ(skip_dates[1], std::string("2026-10-02"));

    EXPECT_TRUE(repo.ClearExpiredSkipDates("2026-10-02"));
    EXPECT_TRUE(repo.GetSkipDates(id, &skip_dates));
    EXPECT_EQ(skip_dates.size(), 1);
    EXPECT_EQ(skip_dates[0], std::string("2026-10-02"));

    // Delete
    EXPECT_TRUE(repo.DeleteAlarm(id));
    EXPECT_FALSE(repo.GetAlarmById(id, &fetched));

    helper.Close();
    std::remove(kTestDbPath);
    return true;
}

bool TestHolidayRepository() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper helper(kTestDbPath);
    EXPECT_TRUE(helper.Open());
    HolidayRepositoryImpl holiday_repo(&helper);

    EXPECT_FALSE(holiday_repo.HasYearData(2026));

    HolidayEntity rule1;
    rule1.date_str = "2026-01-01";
    rule1.year = 2026;
    rule1.day_type = DayType::kStatutoryHoliday;
    rule1.name = "元旦";

    HolidayEntity rule2;
    rule2.date_str = "2026-02-15";
    rule2.year = 2026;
    rule2.day_type = DayType::kCompensatoryWorkday;
    rule2.name = "春节补班";

    std::vector<HolidayEntity> batch = {rule1, rule2};
    EXPECT_TRUE(holiday_repo.BatchUpsertHolidayRules(batch));
    EXPECT_TRUE(holiday_repo.HasYearData(2026));

    HolidayEntity queried;
    EXPECT_TRUE(holiday_repo.GetRuleByDate("2026-01-01", &queried));
    EXPECT_EQ(static_cast<int>(queried.day_type), static_cast<int>(DayType::kStatutoryHoliday));
    EXPECT_EQ(queried.name, std::string("元旦"));

    EXPECT_TRUE(holiday_repo.GetRuleByDate("2026-02-15", &queried));
    EXPECT_EQ(static_cast<int>(queried.day_type), static_cast<int>(DayType::kCompensatoryWorkday));

    helper.Close();
    std::remove(kTestDbPath);
    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestAlarmEntityValidation);
RUN_TEST(TestDatabaseOpenAndTables);
RUN_TEST(TestAlarmCrudOperations);
RUN_TEST(TestHolidayRepository);
TEST_MAIN_END
