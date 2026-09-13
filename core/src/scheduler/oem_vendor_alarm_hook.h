#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::core {

enum class OemVendorType { kSamsung, kIQOOVivo, kStandardAndroid };

class OemVendorAlarmHook {
   public:
    static OemVendorType DetectVendor(const std::string& manufacturer, const std::string& brand);

    // Returns the OEM-specific power-off wakeup broadcast action
    static std::string GetPowerOffWakeupAction(OemVendorType vendor);

    // Dispatches vendor registration parameters
    static bool SupportsHardwareRtcWake(OemVendorType vendor);
};

}  // namespace edom::alarm::core
