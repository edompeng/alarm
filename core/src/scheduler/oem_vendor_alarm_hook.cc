#include "core/src/scheduler/oem_vendor_alarm_hook.h"

namespace edom::alarm::core {

namespace {

// ASCII-only lowercasing: std::tolower on a negative signed char is undefined
// behaviour, and OEM manufacturer strings can contain non-ASCII bytes.
std::string ToLowerAscii(const std::string& value) {
    std::string lower = value;
    for (char& c : lower) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return lower;
}

}  // namespace

OemVendorType OemVendorAlarmHook::DetectVendor(const std::string& manufacturer,
                                               const std::string& brand) {
    const std::string m = ToLowerAscii(manufacturer);
    const std::string b = ToLowerAscii(brand);

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
