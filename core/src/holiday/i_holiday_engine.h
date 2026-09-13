#pragma once

#include <string>
#include <vector>

#include "data/src/model/holiday_entity.h"

namespace edom::alarm::core {

class IHolidayEngine {
   public:
    virtual ~IHolidayEngine() = default;

    // Evaluates whether an alarm set to Statutory Workdays should ring on date_str (YYYY-MM-DD)
    virtual bool IsStatutoryWorkday(const std::string& date_str) = 0;

    // Returns exact classification for a date
    virtual data::DayType ClassifyDate(const std::string& date_str) = 0;

    // Parses and loads baseline JSON holiday rules into repository
    virtual bool LoadBaselineJson(const std::string& json_content) = 0;
};

}  // namespace edom::alarm::core
