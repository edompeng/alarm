#include "core/src/holiday/holiday_cloud_sync_service.h"

namespace edom::alarm::core {

HolidayCloudSyncService::HolidayCloudSyncService(IHolidayEngine* holiday_engine)
    : holiday_engine_(holiday_engine) {}

bool HolidayCloudSyncService::ProcessRemotePayload(const std::string& remote_json_response) {
    if (!holiday_engine_ || remote_json_response.empty()) {
        return false;
    }
    return holiday_engine_->LoadBaselineJson(remote_json_response);
}

bool HolidayCloudSyncService::EnsureYearCoverage(int target_year,
                                                 const std::string& fallback_baseline_json) {
    if (!holiday_engine_) {
        return false;
    }
    return holiday_engine_->LoadBaselineJson(fallback_baseline_json);
}

}  // namespace edom::alarm::core
