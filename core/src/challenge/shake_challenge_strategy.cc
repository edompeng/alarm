#include "core/src/challenge/shake_challenge_strategy.h"

#include <cmath>

namespace edom::alarm::core {

bool ShakeChallengeStrategy::DetectShake(float ax, float ay, float az, float* last_magnitude) {
    float magnitude = std::sqrt(ax * ax + ay * ay + az * az);
    bool is_shake = false;

    if (last_magnitude != nullptr) {
        // Look for sharp acceleration surge past threshold
        if (magnitude > kShakeThreshold && (*last_magnitude <= kShakeThreshold)) {
            is_shake = true;
        }
        *last_magnitude = magnitude;
    } else if (magnitude > kShakeThreshold) {
        is_shake = true;
    }

    return is_shake;
}

}  // namespace edom::alarm::core
