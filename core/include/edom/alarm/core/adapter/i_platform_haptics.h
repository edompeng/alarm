#pragma once

#include <cstdint>

namespace edom::alarm::core {

enum class VibrationPattern {
    kHeartbeat = 0,
    kWave = 1,
    kStaccato = 2,
    kContinuous = 3,
};

class IPlatformHaptics {
   public:
    virtual ~IPlatformHaptics() = default;

    // Initiates linear motor vibration with selected waveform and intensity (0.0f - 1.0f).
    virtual bool StartVibration(VibrationPattern pattern, float intensity) = 0;

    // Stops vibration motor immediately.
    virtual void StopVibration() = 0;
};

}  // namespace edom::alarm::core
