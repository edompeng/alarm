#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::core {

class IPlatformScheduler {
   public:
    virtual ~IPlatformScheduler() = default;

    // Schedules an exact high-precision alarm wakeup at trigger_time_ms (epoch ms).
    virtual bool ScheduleExactAlarm(int64_t alarm_id, int64_t trigger_time_ms,
                                    const std::string& title) = 0;

    // Cancels a scheduled exact alarm.
    virtual bool CancelAlarm(int64_t alarm_id) = 0;

    // Schedules advance notification 30-60 minutes prior to alarm.
    virtual bool ScheduleAdvanceNotification(int64_t alarm_id, int64_t advance_time_ms) = 0;

    // Cancels advance notification card.
    virtual bool CancelAdvanceNotification(int64_t alarm_id) = 0;
};

}  // namespace edom::alarm::core
