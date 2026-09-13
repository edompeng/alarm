#include "core/src/sensor/alarm_sensor_fusion_listener.h"

#include <cmath>

namespace edom::alarm::core {

void AlarmSensorFusionListener::Reset() {
    last_magnitude_ = 9.8f;
    was_face_down_ = false;
    was_picked_up_ = false;
}

GestureEvent AlarmSensorFusionListener::ProcessSensorSample(float ax, float ay, float az,
                                                            float proximity_cm) {
    float magnitude = std::sqrt(ax * ax + ay * ay + az * az);

    // 1. Flip face down: Z is pointing down (< -7.5 m/s^2) and proximity is close (< 2.0 cm)
    if (az < -7.5f && proximity_cm < 2.0f) {
        if (!was_face_down_) {
            was_face_down_ = true;
            return GestureEvent::kFlipToMute;
        }
    } else {
        was_face_down_ = false;
    }

    // 2. Pick up phone: Phone is lifted up from resting position
    // Proximity is clear (> 3.0 cm) and dynamic acceleration shows lifting movement (delta > 1.2
    // m/s^2)
    float delta_accel = std::abs(magnitude - 9.8f);
    if (proximity_cm > 3.0f && delta_accel > 1.2f) {
        if (!was_picked_up_) {
            was_picked_up_ = true;
            return GestureEvent::kPickUpToQuiet;
        }
    }

    last_magnitude_ = magnitude;
    return GestureEvent::kNone;
}

}  // namespace edom::alarm::core
