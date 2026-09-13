#pragma once

#include <cstdint>
#include <string>
#include <vector>

#include "data/src/model/alarm_entity.h"

namespace edom::alarm::core {

class IAlarmScheduler {
   public:
    virtual ~IAlarmScheduler() = default;

    // Computes the next epoch trigger time taking into account holiday calendars,
    // custom repeat bitmasks, and temporary skip rules.
    virtual int64_t CalculateNextTriggerTime(const data::AlarmEntity& alarm, int64_t from_epoch_ms,
                                             const std::vector<std::string>& skip_dates) = 0;

    // Computes the snooze trigger timestamp
    virtual int64_t CalculateSnoozeTime(int64_t current_time_ms, int interval_minutes) = 0;
};

}  // namespace edom::alarm::core
