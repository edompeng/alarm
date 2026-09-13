#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::data {

enum class DayType { kWorkday = 0, kWeekend = 1, kStatutoryHoliday = 2, kCompensatoryWorkday = 3 };

struct HolidayEntity {
    std::string date_str;  // ISO "YYYY-MM-DD"
    int year = 2026;
    DayType day_type = DayType::kWorkday;
    std::string name;
    int64_t updated_at = 0;

    bool IsValid() const { return !date_str.empty() && year > 2000; }
};

}  // namespace edom::alarm::data
