#include "core/src/scheduler/alarm_scheduler_impl.h"

#include <algorithm>
#include <ctime>
#include <iomanip>
#include <sstream>

namespace edom::alarm::core {

AlarmSchedulerImpl::AlarmSchedulerImpl(IHolidayEngine* holiday_engine)
    : holiday_engine_(holiday_engine) {}

std::string AlarmSchedulerImpl::FormatIsoDate(int64_t epoch_ms) {
    std::time_t raw_time = static_cast<std::time_t>(epoch_ms / 1000);
    std::tm* time_info = std::localtime(&raw_time);
    if (!time_info) return "";
    char buffer[16];
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%d", time_info);
    return std::string(buffer);
}

int64_t AlarmSchedulerImpl::ComputeEpochMs(const std::string& date_str, int hour, int minute) {
    if (date_str.length() < 10) return -1;
    std::tm time_in = {};
    time_in.tm_year = std::stoi(date_str.substr(0, 4)) - 1900;
    time_in.tm_mon = std::stoi(date_str.substr(5, 2)) - 1;
    time_in.tm_mday = std::stoi(date_str.substr(8, 2));
    time_in.tm_hour = hour;
    time_in.tm_min = minute;
    time_in.tm_sec = 0;
    std::time_t epoch_sec = std::mktime(&time_in);
    return static_cast<int64_t>(epoch_sec) * 1000;
}

int64_t AlarmSchedulerImpl::CalculateSnoozeTime(int64_t current_time_ms, int interval_minutes) {
    if (interval_minutes <= 0) interval_minutes = 10;
    return current_time_ms + (static_cast<int64_t>(interval_minutes) * 60 * 1000);
}

int64_t AlarmSchedulerImpl::CalculateNextTriggerTime(const data::AlarmEntity& alarm,
                                                     int64_t from_epoch_ms,
                                                     const std::vector<std::string>& skip_dates) {
    // Helper lambda to check if date is in skip list
    auto is_skipped = [&skip_dates](const std::string& d) {
        return std::find(skip_dates.begin(), skip_dates.end(), d) != skip_dates.end();
    };

    // 1. One-off alarm
    if (alarm.repeat_mode == data::RepeatMode::kOnce) {
        std::string today_str = FormatIsoDate(from_epoch_ms);
        int64_t today_target = ComputeEpochMs(today_str, alarm.hour, alarm.minute);
        if (today_target > from_epoch_ms && !is_skipped(today_str)) {
            return today_target;
        }
        // Tomorrow
        int64_t tomorrow_ms = from_epoch_ms + 24LL * 3600 * 1000;
        std::string tomorrow_str = FormatIsoDate(tomorrow_ms);
        return ComputeEpochMs(tomorrow_str, alarm.hour, alarm.minute);
    }

    // 2. Custom repeat bitmask or 3. Statutory Workdays
    // Iterate up to 365 days to find next matching valid date
    for (int day_offset = 0; day_offset <= 365; ++day_offset) {
        int64_t candidate_day_ms =
            from_epoch_ms + (static_cast<int64_t>(day_offset) * 24LL * 3600 * 1000);
        std::string candidate_date = FormatIsoDate(candidate_day_ms);
        int64_t candidate_target_ms = ComputeEpochMs(candidate_date, alarm.hour, alarm.minute);

        if (candidate_target_ms <= from_epoch_ms) {
            continue;  // Already passed today
        }

        if (is_skipped(candidate_date)) {
            continue;  // Explicitly marked as skipped
        }

        if (alarm.repeat_mode == data::RepeatMode::kSpecificDays) {
            std::time_t raw_time = static_cast<std::time_t>(candidate_target_ms / 1000);
            std::tm* time_info = std::localtime(&raw_time);
            int wday = time_info ? time_info->tm_wday : 0;  // 0=Sun, 1=Mon ... 6=Sat
            if ((alarm.days_bitmask & (1 << wday)) != 0) {
                return candidate_target_ms;
            }
        } else if (alarm.repeat_mode == data::RepeatMode::kStatutoryWorkdays) {
            if (holiday_engine_ && holiday_engine_->IsStatutoryWorkday(candidate_date)) {
                return candidate_target_ms;
            }
        }
    }

    return -1;
}

}  // namespace edom::alarm::core
