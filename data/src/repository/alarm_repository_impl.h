#pragma once

#include "data/src/db/alarm_database_helper.h"
#include "data/src/repository/i_alarm_repository.h"

namespace edom::alarm::data {

class AlarmRepositoryImpl : public IAlarmRepository {
   public:
    explicit AlarmRepositoryImpl(AlarmDatabaseHelper* db_helper);
    ~AlarmRepositoryImpl() override = default;

    int64_t InsertAlarm(const AlarmEntity& alarm) override;
    bool UpdateAlarm(const AlarmEntity& alarm) override;
    bool DeleteAlarm(int64_t alarm_id) override;
    bool GetAlarmById(int64_t alarm_id, AlarmEntity* out_alarm) override;
    bool GetAllAlarms(std::vector<AlarmEntity>* out_alarms) override;
    bool GetEnabledAlarms(std::vector<AlarmEntity>* out_alarms) override;

    bool AddSkipDate(int64_t alarm_id, const std::string& skip_date) override;
    bool RemoveSkipDate(int64_t alarm_id, const std::string& skip_date) override;
    bool GetSkipDates(int64_t alarm_id, std::vector<std::string>* out_dates) override;
    bool ClearExpiredSkipDates(const std::string& current_date) override;

   private:
    AlarmDatabaseHelper* db_helper_;
};

}  // namespace edom::alarm::data
