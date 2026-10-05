#include "core/src/holiday/holiday_engine_impl.h"

#include <cstring>
#include <ctime>
#include <vector>

#include "core/src/common/iso_date.h"

namespace edom::alarm::core {

namespace {

bool IsJsonSpace(char c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
}

// Reads the quoted value of `"key" : "value"` starting at or after search_from.
bool ReadQuotedField(const std::string& json, const char* key, size_t search_from,
                     std::string* value, size_t* end_pos) {
    const size_t key_len = std::strlen(key);
    const size_t key_pos = json.find(key, search_from);
    if (key_pos == std::string::npos) {
        return false;
    }
    size_t cursor = key_pos + key_len;
    while (cursor < json.size() && IsJsonSpace(json[cursor])) {
        ++cursor;
    }
    if (cursor >= json.size() || json[cursor] != ':') {
        return false;
    }
    ++cursor;
    while (cursor < json.size() && IsJsonSpace(json[cursor])) {
        ++cursor;
    }
    if (cursor >= json.size() || json[cursor] != '"') {
        return false;
    }
    const size_t open = cursor + 1;
    const size_t close = json.find('"', open);
    if (close == std::string::npos) {
        return false;
    }
    *value = json.substr(open, close - open);
    *end_pos = close + 1;
    return true;
}

// Reads the integer value of `"key" : 12` starting at or after search_from.
bool ReadIntField(const std::string& json, const char* key, size_t search_from, int* value,
                  size_t* end_pos) {
    const size_t key_len = std::strlen(key);
    const size_t key_pos = json.find(key, search_from);
    if (key_pos == std::string::npos) {
        return false;
    }
    size_t cursor = key_pos + key_len;
    while (cursor < json.size() && IsJsonSpace(json[cursor])) {
        ++cursor;
    }
    if (cursor >= json.size() || json[cursor] != ':') {
        return false;
    }
    ++cursor;
    while (cursor < json.size() && IsJsonSpace(json[cursor])) {
        ++cursor;
    }
    bool negative = false;
    if (cursor < json.size() && json[cursor] == '-') {
        negative = true;
        ++cursor;
    }
    if (cursor >= json.size() || json[cursor] < '0' || json[cursor] > '9') {
        return false;
    }
    long long parsed = 0;
    while (cursor < json.size() && json[cursor] >= '0' && json[cursor] <= '9') {
        if (parsed < 1000000) {
            parsed = parsed * 10 + (json[cursor] - '0');
        }
        ++cursor;
    }
    *value = static_cast<int>(negative ? -parsed : parsed);
    *end_pos = cursor;
    return true;
}

// Appends the ISO dates of a JSON string array such as `"holidays": [ ... ]`.
bool AppendDatesFromArray(const std::string& json, const char* key, data::DayType day_type,
                          std::vector<data::HolidayEntity>* out_rules) {
    const size_t key_len = std::strlen(key);
    const size_t key_pos = json.find(key);
    if (key_pos == std::string::npos) {
        return false;
    }
    size_t cursor = json.find('[', key_pos + key_len);
    if (cursor == std::string::npos) {
        return false;
    }
    ++cursor;
    bool appended = false;
    while (cursor < json.size()) {
        while (cursor < json.size() && (IsJsonSpace(json[cursor]) || json[cursor] == ',')) {
            ++cursor;
        }
        if (cursor >= json.size() || json[cursor] == ']') {
            break;
        }
        if (json[cursor] != '"') {
            break;
        }
        const size_t open = cursor + 1;
        const size_t close = json.find('"', open);
        if (close == std::string::npos) {
            break;
        }
        const std::string date_str = json.substr(open, close - open);
        int year = 0;
        if (ParseIsoDate(date_str, &year, nullptr, nullptr)) {
            data::HolidayEntity entity;
            entity.date_str = date_str;
            entity.year = year;
            entity.day_type = day_type;
            out_rules->push_back(entity);
            appended = true;
        }
        cursor = close + 1;
    }
    return appended;
}

}  // namespace

HolidayEngineImpl::HolidayEngineImpl(data::IHolidayRepository* holiday_repo)
    : holiday_repo_(holiday_repo) {}

int HolidayEngineImpl::GetDayOfWeek(const std::string& date_str) {
    int year = 0;
    int month = 0;
    int day = 0;
    if (!ParseIsoDate(date_str, &year, &month, &day)) {
        return -1;
    }
    std::tm time_in = {};
    time_in.tm_year = year - 1900;
    time_in.tm_mon = month - 1;
    time_in.tm_mday = day;
    time_in.tm_isdst = -1;  // Let mktime resolve whether DST applies.
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
    // Dependency-free parser supporting both bundled baseline payloads
    // ("rules": [{"date": ..., "type": N}]) and the published HolidaySyncModel
    // payload ("holidays"/"workdays" string arrays). Malformed input is rejected
    // instead of aborting the host process.
    std::vector<data::HolidayEntity> rules;
    size_t cursor = 0;
    while (true) {
        std::string date_str;
        size_t date_end = 0;
        if (!ReadQuotedField(json_content, "\"date\"", cursor, &date_str, &date_end)) {
            break;
        }
        int type_value = 0;
        size_t type_end = 0;
        if (!ReadIntField(json_content, "\"type\"", date_end, &type_value, &type_end)) {
            break;
        }
        int year = 0;
        if (ParseIsoDate(date_str, &year, nullptr, nullptr) && type_value >= 0 &&
            type_value <= 3) {
            data::HolidayEntity entity;
            entity.date_str = date_str;
            entity.year = year;
            entity.day_type = static_cast<data::DayType>(type_value);
            const size_t object_end = json_content.find('}', type_end);
            std::string name;
            size_t name_end = 0;
            if (ReadQuotedField(json_content, "\"name\"", type_end, &name, &name_end) &&
                (object_end == std::string::npos || name_end <= object_end)) {
                entity.name = name;
            }
            rules.push_back(entity);
        }
        cursor = type_end;
    }

    if (rules.empty()) {
        AppendDatesFromArray(json_content, "\"holidays\"", data::DayType::kStatutoryHoliday, &rules);
        AppendDatesFromArray(json_content, "\"workdays\"", data::DayType::kCompensatoryWorkday,
                             &rules);
    }

    if (rules.empty()) {
        return false;
    }
    return holiday_repo_->BatchUpsertHolidayRules(rules);
}

}  // namespace edom::alarm::core
