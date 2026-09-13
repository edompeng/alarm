#pragma once

#include <string>
#include <vector>

#include "data/src/model/holiday_entity.h"

namespace edom::alarm::data {

class IHolidayRepository {
   public:
    virtual ~IHolidayRepository() = default;

    virtual bool UpsertHolidayRule(const HolidayEntity& rule) = 0;
    virtual bool BatchUpsertHolidayRules(const std::vector<HolidayEntity>& rules) = 0;
    virtual bool GetRuleByDate(const std::string& date_str, HolidayEntity* out_rule) = 0;
    virtual bool HasYearData(int year) = 0;
    virtual bool GetRulesForYear(int year, std::vector<HolidayEntity>* out_rules) = 0;
};

}  // namespace edom::alarm::data
