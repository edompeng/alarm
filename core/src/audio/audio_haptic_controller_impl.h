#pragma once

#include "core/src/audio/i_audio_haptic_controller.h"

namespace edom::alarm::core {

class AudioHapticControllerImpl : public IAudioHapticController {
   public:
    AudioHapticControllerImpl();
    ~AudioHapticControllerImpl() override = default;

    void StartAlarm(const std::string& ringtone_uri, const std::string& fallback_uri,
                    int target_volume, int crescendo_seconds, bool force_speaker,
                    VibrationPatternType pattern, int vibration_intensity) override;

    void StopAlarm() override;
    void AttenuateVolumeForPickup() override;
    float GetCurrentVolume() const override { return current_volume_; }
    bool IsPlaying() const override { return is_playing_; }

    // Computes volume scalar (0.0 to 1.0) based on elapsed time and crescendo duration
    static float CalculateCrescendoVolume(float elapsed_seconds, float total_crescendo_seconds,
                                          int target_volume_percent);

    // Computes attenuated volume scalar when phone is picked up
    static float CalculatePickupVolume(float current_volume);

   private:
    bool is_playing_ = false;
    float current_volume_ = 0.0f;
    int target_volume_percent_ = 80;
    int crescendo_seconds_ = 15;
    bool force_speaker_ = true;
};

}  // namespace edom::alarm::core
