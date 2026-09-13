#include "core/src/audio/audio_haptic_controller_impl.h"

#include <algorithm>

namespace edom::alarm::core {

AudioHapticControllerImpl::AudioHapticControllerImpl() = default;

float AudioHapticControllerImpl::CalculateCrescendoVolume(float elapsed_seconds,
                                                          float total_crescendo_seconds,
                                                          int target_volume_percent) {
    float max_scalar = std::clamp(target_volume_percent / 100.0f, 0.0f, 1.0f);
    if (total_crescendo_seconds <= 0.0f) {
        return max_scalar;
    }
    float progress = std::clamp(elapsed_seconds / total_crescendo_seconds, 0.0f, 1.0f);
    return progress * max_scalar;
}

float AudioHapticControllerImpl::CalculatePickupVolume(float current_volume) {
    // Drop to gentle ambient background volume (max 25% of current volume, min 0.1)
    return std::max(0.1f, current_volume * 0.25f);
}

void AudioHapticControllerImpl::StartAlarm(const std::string& /*ringtone_uri*/,
                                           const std::string& /*fallback_uri*/, int target_volume,
                                           int crescendo_seconds, bool force_speaker,
                                           VibrationPatternType /*pattern*/,
                                           int /*vibration_intensity*/) {
    is_playing_ = true;
    target_volume_percent_ = target_volume;
    crescendo_seconds_ = crescendo_seconds;
    force_speaker_ = force_speaker;

    // Start at initial volume (0 if crescendo enabled, else target volume)
    if (crescendo_seconds > 0) {
        current_volume_ = 0.0f;
    } else {
        current_volume_ = std::clamp(target_volume / 100.0f, 0.0f, 1.0f);
    }
}

void AudioHapticControllerImpl::StopAlarm() {
    is_playing_ = false;
    current_volume_ = 0.0f;
}

void AudioHapticControllerImpl::AttenuateVolumeForPickup() {
    if (is_playing_) {
        current_volume_ = CalculatePickupVolume(current_volume_);
    }
}

}  // namespace edom::alarm::core
