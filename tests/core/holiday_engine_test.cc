#include <cstdio>

#include "core/src/holiday/holiday_cloud_sync_service.h"
#include "core/src/holiday/holiday_engine_impl.h"
#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/holiday_repository_impl.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;
using namespace edom::alarm::data;

static const char* kTestDbPath = "test_holiday_engine.db";

bool TestDayOfWeekCalculation() {
    // 2026-01-01 is Thursday (4)
    EXPECT_EQ(HolidayEngineImpl::GetDayOfWeek("2026-01-01"), 4);
    // 2026-10-01 is Thursday (4)
    EXPECT_EQ(HolidayEngineImpl::GetDayOfWeek("2026-10-01"), 4);
    // 2026-09-27 is Sunday (0)
    EXPECT_EQ(HolidayEngineImpl::GetDayOfWeek("2026-09-27"), 0);
    // 2026-05-09 is Saturday (6)
    EXPECT_EQ(HolidayEngineImpl::GetDayOfWeek("2026-05-09"), 6);
    return true;
}

bool TestHolidayEngineClassificationAndWorkday() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper db_helper(kTestDbPath);
    EXPECT_TRUE(db_helper.Open());
    HolidayRepositoryImpl repo(&db_helper);
    HolidayEngineImpl engine(&repo);

    // Normal dates without special rules
    // 2026-06-01 is Monday -> Workday
    EXPECT_TRUE(engine.IsStatutoryWorkday("2026-06-01"));
    EXPECT_EQ(static_cast<int>(engine.ClassifyDate("2026-06-01")),
              static_cast<int>(DayType::kWorkday));

    // 2026-06-06 is Saturday -> Weekend
    EXPECT_FALSE(engine.IsStatutoryWorkday("2026-06-06"));
    EXPECT_EQ(static_cast<int>(engine.ClassifyDate("2026-06-06")),
              static_cast<int>(DayType::kWeekend));

    // Add holiday rule: 2026-10-01 is National Day (Statutory Holiday)
    HolidayEntity national_day;
    national_day.date_str = "2026-10-01";
    national_day.year = 2026;
    national_day.day_type = DayType::kStatutoryHoliday;
    national_day.name = "国庆节";
    EXPECT_TRUE(repo.UpsertHolidayRule(national_day));

    // Even though 2026-10-01 is Thursday, it should NOT ring
    EXPECT_FALSE(engine.IsStatutoryWorkday("2026-10-01"));
    EXPECT_EQ(static_cast<int>(engine.ClassifyDate("2026-10-01")),
              static_cast<int>(DayType::kStatutoryHoliday));

    // Add compensatory workday rule: 2026-09-27 is Sunday (Compensatory Workday)
    HolidayEntity comp_workday;
    comp_workday.date_str = "2026-09-27";
    comp_workday.year = 2026;
    comp_workday.day_type = DayType::kCompensatoryWorkday;
    comp_workday.name = "国庆调休补班";
    EXPECT_TRUE(repo.UpsertHolidayRule(comp_workday));

    // Even though 2026-09-27 is Sunday, it MUST ring!
    EXPECT_TRUE(engine.IsStatutoryWorkday("2026-09-27"));
    EXPECT_EQ(static_cast<int>(engine.ClassifyDate("2026-09-27")),
              static_cast<int>(DayType::kCompensatoryWorkday));

    db_helper.Close();
    std::remove(kTestDbPath);
    return true;
}

bool TestBaselineJsonParsing() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper db_helper(kTestDbPath);
    EXPECT_TRUE(db_helper.Open());
    HolidayRepositoryImpl repo(&db_helper);
    HolidayEngineImpl engine(&repo);

    const std::string json = R"({
        "rules": [
            {"date": "2026-01-01", "type": 2, "name": "元旦"},
            {"date": "2026-02-15", "type": 3, "name": "春节补班"}
        ]
    })";

    EXPECT_TRUE(engine.LoadBaselineJson(json));
    EXPECT_FALSE(engine.IsStatutoryWorkday("2026-01-01"));  // Holiday
    EXPECT_TRUE(engine.IsStatutoryWorkday("2026-02-15"));   // Compensatory workday Sunday

    db_helper.Close();
    std::remove(kTestDbPath);
    return true;
}

bool TestHolidayCloudSyncService() {
    std::remove(kTestDbPath);
    AlarmDatabaseHelper db_helper(kTestDbPath);
    EXPECT_TRUE(db_helper.Open());
    HolidayRepositoryImpl repo(&db_helper);
    HolidayEngineImpl engine(&repo);
    HolidayCloudSyncService sync_service(&engine);

    // Empty payload handling
    EXPECT_FALSE(sync_service.ProcessRemotePayload(""));
    HolidayCloudSyncService null_sync_service(nullptr);
    EXPECT_FALSE(null_sync_service.ProcessRemotePayload("{}"));
    EXPECT_FALSE(null_sync_service.EnsureYearCoverage(2026, "{}"));

    // Valid remote payload processing
    const std::string payload = R"({
        "rules": [
            {"date": "2026-10-01", "type": 2, "name": "国庆节"},
            {"date": "2026-10-10", "type": 3, "name": "国庆补班"}
        ]
    })";
    EXPECT_TRUE(sync_service.ProcessRemotePayload(payload));
    EXPECT_FALSE(engine.IsStatutoryWorkday("2026-10-01"));
    EXPECT_TRUE(engine.IsStatutoryWorkday("2026-10-10"));

    // Fallback coverage
    EXPECT_TRUE(sync_service.EnsureYearCoverage(2026, payload));

    db_helper.Close();
    std::remove(kTestDbPath);
    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestDayOfWeekCalculation);
RUN_TEST(TestHolidayEngineClassificationAndWorkday);
RUN_TEST(TestBaselineJsonParsing);
RUN_TEST(TestHolidayCloudSyncService);
TEST_MAIN_END
