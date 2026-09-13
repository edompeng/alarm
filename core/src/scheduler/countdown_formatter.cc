#include "core/src/scheduler/countdown_formatter.h"

#include <sstream>

namespace edom::alarm::core {

std::string CountdownFormatter::FormatCountdownZh(int64_t target_epoch_ms,
                                                  int64_t current_epoch_ms) {
    int64_t delta_ms = target_epoch_ms - current_epoch_ms;
    if (delta_ms <= 0) {
        return "闹钟即将响铃";
    }

    int64_t total_minutes = (delta_ms + 59999) / 60000;  // Round up
    int64_t days = total_minutes / (24 * 60);
    int64_t hours = (total_minutes % (24 * 60)) / 60;
    int64_t minutes = total_minutes % 60;

    std::ostringstream oss;
    oss << "距离响铃还有 ";
    if (days > 0) {
        oss << days << " 天 ";
    }
    if (hours > 0 || days > 0) {
        oss << hours << " 小时 ";
    }
    oss << minutes << " 分钟";
    return oss.str();
}

std::string CountdownFormatter::FormatCountdownEn(int64_t target_epoch_ms,
                                                  int64_t current_epoch_ms) {
    int64_t delta_ms = target_epoch_ms - current_epoch_ms;
    if (delta_ms <= 0) {
        return "Alarm will ring in less than 1 minute";
    }

    int64_t total_minutes = (delta_ms + 59999) / 60000;
    int64_t days = total_minutes / (24 * 60);
    int64_t hours = (total_minutes % (24 * 60)) / 60;
    int64_t minutes = total_minutes % 60;

    std::ostringstream oss;
    oss << "Alarm will ring in ";
    if (days > 0) {
        oss << days << (days == 1 ? " day " : " days ");
    }
    if (hours > 0 || days > 0) {
        oss << hours << (hours == 1 ? " hour " : " hours ");
    }
    oss << minutes << (minutes == 1 ? " minute" : " minutes");
    return oss.str();
}

}  // namespace edom::alarm::core
