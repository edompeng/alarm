#include "core/src/holiday/holiday_engine_impl.h"

#include <ctime>
#include <iomanip>
#include <sstream>

namespace edom::alarm::core {

HolidayEngineImpl::HolidayEngineImpl(data::IHolidayRepository* holiday_repo)
    : holiday_repo_(holiday_repo) {}

int HolidayEngineImpl::GetDayOfWeek(const std::string& date_str) {
    if (date_str.length() < 10) return -1;
    std::tm time_in = {};
    time_in.tm_year = std::stoi(date_str.substr(0, 4)) - 1900;
    time_in.tm_mon = std::stoi(date_str.substr(5, 2)) - 1;
    time_in.tm_mday = std::stoi(date_str.substr(8, 2));
    std::mktime(&time_in);
    return time_in.tm_wday;  // 0 = Sunday, 1 = Monday ... 6 = Saturday
}

data::DayType HolidayEngineImpl::ClassifyDate(const std::string& date_str) {
    if (holiday_repo_) {
        data::HolidayEntity rule;
        if (holiday_repo_->GetRuleByDate(date_str, &rule)) {
            return rule.day_type;
        }
    }
    int dow = GetDayOfWeek(date_str);
    if (dow == 0 || dow == 6) {
        return data::DayType::kWeekend;
    }
    return data::DayType::kWorkday;
}

bool HolidayEngineImpl::IsStatutoryWorkday(const std::string& date_str) {
    data::DayType type = ClassifyDate(date_str);
    switch (type) {
        case data::DayType::kCompensatoryWorkday:
            return true;  // Weekend overtime/working day (调休补班) -> MUST RING
        case data::DayType::kStatutoryHoliday:
            return false;  // Official national holiday -> SKIP RINGING
        case data::DayType::kWorkday:
            return true;  // Normal Monday-Friday -> MUST RING
        case data::DayType::kWeekend:
            return false;  // Normal Saturday-Sunday -> SKIP RINGING
        default:
            return false;
    }
}

bool HolidayEngineImpl::LoadBaselineJson(const std::string& json_content) {
    if (!holiday_repo_ || json_content.empty()) {
        return false;
    }
    // Simple robust manual parser for rules JSON without external dependencies
    // Format expected: "date": "YYYY-MM-DD", "type": N, "name": "..."
    std::vector<data::HolidayEntity> rules;
    size_t pos = 0;
    while ((pos = json_content.find("\"date\":", pos)) != std::string::npos) {
        size_t date_start = json_content.find("\"", pos + 7) + 1;
        size_t date_end = json_content.find("\"", date_start);
        std::string date_str = json_content.substr(date_start, date_end - date_start);

        size_t type_pos = json_content.find("\"type\":", date_end);
        if (type_pos == std::string::npos) break;
        int type_val = std::stoi(json_content.substr(type_pos + 7, 2));

        size_t name_pos = json_content.find("\"name\":", type_pos);
        std::string name_str = "";
        if (name_pos != std::string::npos && name_pos < json_content.find("}", type_pos)) {
            size_t nstart = json_content.find("\"", name_pos + 7) + 1;
            size_t nend = json_content.find("\"", nstart);
            name_str = json_content.substr(nstart, nend - nstart);
        }

        data::HolidayEntity entity;
        entity.date_str = date_str;
        entity.year = std::stoi(date_str.substr(0, 4));
        entity.day_type = static_cast<data::DayType>(type_val);
        entity.name = name_str;
        rules.push_back(entity);

        pos = type_pos + 8;
    }

    if (!rules.empty()) {
        return holiday_repo_->BatchUpsertHolidayRules(rules);
    }
    return true;
}

}  // namespace edom::alarm::core
