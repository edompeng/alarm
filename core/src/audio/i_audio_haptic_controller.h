#pragma once

#include <string>

#include "core/src/audio/haptic_waveform_factory.h"

namespace edom::alarm::core {

class IAudioHapticController {
   public:
    virtual ~IAudioHapticController() = default;

    virtual void StartAlarm(const std::string& ringtone_uri, const std::string& fallback_uri,
                            int target_volume, int crescendo_seconds, bool force_speaker,
                            VibrationPatternType pattern, int vibration_intensity) = 0;

    virtual void StopAlarm() = 0;
    virtual void AttenuateVolumeForPickup() = 0;
    virtual float GetCurrentVolume() const = 0;
    virtual bool IsPlaying() const = 0;
};

}  // namespace edom::alarm::core
