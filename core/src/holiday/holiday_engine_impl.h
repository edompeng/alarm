#pragma once

#include "core/src/holiday/i_holiday_engine.h"
#include "data/src/repository/i_holiday_repository.h"

namespace edom::alarm::core {

class HolidayEngineImpl : public IHolidayEngine {
   public:
    explicit HolidayEngineImpl(data::IHolidayRepository* holiday_repo);
    ~HolidayEngineImpl() override = default;

    bool IsStatutoryWorkday(const std::string& date_str) override;
    data::DayType ClassifyDate(const std::string& date_str) override;
    bool LoadBaselineJson(const std::string& json_content) override;

    // Utility: calculate day of week for YYYY-MM-DD (0=Sunday, 1=Monday... 6=Saturday)
    static int GetDayOfWeek(const std::string& date_str);

   private:
    data::IHolidayRepository* holiday_repo_;
};

}  // namespace edom::alarm::core
