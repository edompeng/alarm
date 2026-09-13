#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::core {

class CountdownFormatter {
   public:
    // Formats delta milliseconds into Chinese Toast string: "距离响铃还有 [X天] [Y小时] Z分钟"
    static std::string FormatCountdownZh(int64_t target_epoch_ms, int64_t current_epoch_ms);

    // Formats delta milliseconds into English Toast string: "Alarm rings in [X days] [Y hours] Z
    // minutes"
    static std::string FormatCountdownEn(int64_t target_epoch_ms, int64_t current_epoch_ms);
};

}  // namespace edom::alarm::core
