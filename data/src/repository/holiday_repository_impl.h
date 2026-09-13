#pragma once

#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/i_holiday_repository.h"

namespace edom::alarm::data {

class HolidayRepositoryImpl : public IHolidayRepository {
   public:
    explicit HolidayRepositoryImpl(AlarmDatabaseHelper* db_helper);
    ~HolidayRepositoryImpl() override = default;

    bool UpsertHolidayRule(const HolidayEntity& rule) override;
    bool BatchUpsertHolidayRules(const std::vector<HolidayEntity>& rules) override;
    bool GetRuleByDate(const std::string& date_str, HolidayEntity* out_rule) override;
    bool HasYearData(int year) override;
    bool GetRulesForYear(int year, std::vector<HolidayEntity>* out_rules) override;

   private:
    AlarmDatabaseHelper* db_helper_;
};

}  // namespace edom::alarm::data
