#pragma once

#include "core/src/holiday/i_holiday_engine.h"
#include "core/src/scheduler/i_alarm_scheduler.h"

namespace edom::alarm::core {

class AlarmSchedulerImpl : public IAlarmScheduler {
   public:
    explicit AlarmSchedulerImpl(IHolidayEngine* holiday_engine);
    ~AlarmSchedulerImpl() override = default;

    int64_t CalculateNextTriggerTime(const data::AlarmEntity& alarm, int64_t from_epoch_ms,
                                     const std::vector<std::string>& skip_dates) override;

    int64_t CalculateSnoozeTime(int64_t current_time_ms, int interval_minutes) override;

    // Utility: Format epoch ms to "YYYY-MM-DD"
    static std::string FormatIsoDate(int64_t epoch_ms);

    // Utility: Compute epoch ms for a given date string and hour/minute
    static int64_t ComputeEpochMs(const std::string& date_str, int hour, int minute);

   private:
    IHolidayEngine* holiday_engine_;
};

}  // namespace edom::alarm::core
