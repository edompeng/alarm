#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::data {

struct SkipRuleEntity {
    int64_t id = 0;
    int64_t alarm_id = 0;
    std::string skip_date;  // ISO "YYYY-MM-DD"
    int64_t created_at = 0;

    bool IsValid() const { return alarm_id > 0 && !skip_date.empty(); }
};

}  // namespace edom::alarm::data
