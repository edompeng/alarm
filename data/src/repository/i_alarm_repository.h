#pragma once

#include <memory>
#include <string>
#include <vector>

#include "data/src/model/alarm_entity.h"
#include "data/src/model/skip_rule_entity.h"

namespace edom::alarm::data {

class IAlarmRepository {
   public:
    virtual ~IAlarmRepository() = default;

    virtual int64_t InsertAlarm(const AlarmEntity& alarm) = 0;
    virtual bool UpdateAlarm(const AlarmEntity& alarm) = 0;
    virtual bool DeleteAlarm(int64_t alarm_id) = 0;
    virtual bool GetAlarmById(int64_t alarm_id, AlarmEntity* out_alarm) = 0;
    virtual bool GetAllAlarms(std::vector<AlarmEntity>* out_alarms) = 0;
    virtual bool GetEnabledAlarms(std::vector<AlarmEntity>* out_alarms) = 0;

    // Temporary skip management
    virtual bool AddSkipDate(int64_t alarm_id, const std::string& skip_date) = 0;
    virtual bool RemoveSkipDate(int64_t alarm_id, const std::string& skip_date) = 0;
    virtual bool GetSkipDates(int64_t alarm_id, std::vector<std::string>* out_dates) = 0;
    virtual bool ClearExpiredSkipDates(const std::string& current_date) = 0;
};

}  // namespace edom::alarm::data
