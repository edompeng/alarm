#include "core/src/audio/haptic_waveform_factory.h"

#include <algorithm>

namespace edom::alarm::core {

VibrationPatternType HapticWaveformFactory::ParsePattern(const std::string& pattern_str) {
    if (pattern_str == "WAVE") return VibrationPatternType::kWave;
    if (pattern_str == "STACCATO") return VibrationPatternType::kStaccato;
    if (pattern_str == "CONTINUOUS") return VibrationPatternType::kContinuous;
    return VibrationPatternType::kHeartbeat;
}

HapticWaveform HapticWaveformFactory::CreateWaveform(VibrationPatternType type,
                                                     int intensity_percent) {
    intensity_percent = std::clamp(intensity_percent, 0, 100);
    int max_amp = static_cast<int>(255 * (intensity_percent / 100.0f));
    int half_amp = max_amp / 2;

    HapticWaveform waveform;
    waveform.repeat_index = 0;

    switch (type) {
        case VibrationPatternType::kHeartbeat:
            // Lub-dub rhythm: pause 0, pulse 120ms, pause 100ms, pulse 160ms, pause 700ms
            waveform.timings_ms = {0, 120, 100, 160, 700};
            waveform.amplitudes = {0, half_amp, 0, max_amp, 0};
            break;

        case VibrationPatternType::kWave:
            // Progressive swell and decline
            waveform.timings_ms = {0, 150, 150, 200, 150, 150, 500};
            waveform.amplitudes = {0, half_amp / 2, half_amp, max_amp, half_amp, half_amp / 2, 0};
            break;

        case VibrationPatternType::kStaccato:
            // Crisp short rhythmic taps
            waveform.timings_ms = {0, 80, 80, 80, 80, 80, 500};
            waveform.amplitudes = {0, max_amp, 0, max_amp, 0, max_amp, 0};
            break;

        case VibrationPatternType::kContinuous:
        default:
            waveform.timings_ms = {0, 1000, 500};
            waveform.amplitudes = {0, max_amp, 0};
            break;
    }

    return waveform;
}

}  // namespace edom::alarm::core
