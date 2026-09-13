#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::core {

enum class AudioRoutingMode {
    kSystemDefault = 0,
    kForceSpeaker = 1,
};

class IPlatformAudio {
   public:
    virtual ~IPlatformAudio() = default;

    // Starts ringtone playback using designated routing mode.
    virtual bool StartRingtone(const std::string& audio_uri, AudioRoutingMode routing_mode) = 0;

    // Smoothly ramps audio volume to target_volume over duration_ms.
    virtual void RampVolume(float target_volume, int32_t duration_ms) = 0;

    // Immediately attenuates volume to level (e.g. 0.2f when picked up).
    virtual void AttenuateVolume(float level) = 0;

    // Stops audio playback.
    virtual void StopRingtone() = 0;
};

}  // namespace edom::alarm::core
