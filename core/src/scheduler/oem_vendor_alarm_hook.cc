#include "core/src/scheduler/oem_vendor_alarm_hook.h"

#include <algorithm>

namespace edom::alarm::core {

OemVendorType OemVendorAlarmHook::DetectVendor(const std::string& manufacturer,
                                               const std::string& brand) {
    std::string m = manufacturer;
    std::string b = brand;
    std::transform(m.begin(), m.end(), m.begin(), ::tolower);
    std::transform(b.begin(), b.end(), b.begin(), ::tolower);

    if (m.find("samsung") != std::string::npos || b.find("samsung") != std::string::npos) {
        return OemVendorType::kSamsung;
    }
    if (m.find("vivo") != std::string::npos || b.find("vivo") != std::string::npos ||
        m.find("iqoo") != std::string::npos || b.find("iqoo") != std::string::npos) {
        return OemVendorType::kIQOOVivo;
    }
    return OemVendorType::kStandardAndroid;
}

std::string OemVendorAlarmHook::GetPowerOffWakeupAction(OemVendorType vendor) {
    switch (vendor) {
        case OemVendorType::kSamsung:
            return "com.samsung.sec.android.clockpackage.alarm.ALARM_STARTED";
        case OemVendorType::kIQOOVivo:
            return "com.vivo.daemonService.alarm.POWER_OFF_WAKE";
        default:
            return "android.intent.action.ALARM_CHANGED";
    }
}

bool OemVendorAlarmHook::SupportsHardwareRtcWake(OemVendorType vendor) {
    // Both Samsung One UI (S25 Ultra) and Vivo/iQOO OriginOS (Z9 Turbo+) have dedicated RTC wake
    // chips
    return vendor == OemVendorType::kSamsung || vendor == OemVendorType::kIQOOVivo;
}

}  // namespace edom::alarm::core
