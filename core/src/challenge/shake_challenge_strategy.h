#pragma once

namespace edom::alarm::core {

class ShakeChallengeStrategy {
   public:
    static constexpr float kShakeThreshold = 14.0f;  // m/s^2 acceleration magnitude threshold

    // Detects shake peak and returns true if valid shake counted
    static bool DetectShake(float ax, float ay, float az, float* last_magnitude);
};

}  // namespace edom::alarm::core
