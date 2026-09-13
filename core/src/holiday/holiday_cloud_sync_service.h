#pragma once

#include <string>

#include "core/src/holiday/i_holiday_engine.h"

namespace edom::alarm::core {

class HolidayCloudSyncService {
   public:
    explicit HolidayCloudSyncService(IHolidayEngine* holiday_engine);

    // Synchronizes rules from remote payload or cached response
    bool ProcessRemotePayload(const std::string& remote_json_response);

    // Checks if the database has coverage for the target year
    bool EnsureYearCoverage(int target_year, const std::string& fallback_baseline_json);

   private:
    IHolidayEngine* holiday_engine_;
};

}  // namespace edom::alarm::core
