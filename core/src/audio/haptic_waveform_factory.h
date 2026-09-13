#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace edom::alarm::core {

enum class VibrationPatternType { kHeartbeat, kWave, kStaccato, kContinuous };

struct HapticWaveform {
    std::vector<int64_t> timings_ms;
    std::vector<int> amplitudes;  // 0 to 255
    int repeat_index = -1;        // -1 = no repeat, 0 = repeat from start
};

class HapticWaveformFactory {
   public:
    // Creates waveform with scaled amplitude (intensity 0-100)
    static HapticWaveform CreateWaveform(VibrationPatternType type, int intensity_percent);

    // Parses string pattern to enum
    static VibrationPatternType ParsePattern(const std::string& pattern_str);
};

}  // namespace edom::alarm::core
