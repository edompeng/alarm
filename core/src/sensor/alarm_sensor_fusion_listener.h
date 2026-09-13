#pragma once

namespace edom::alarm::core {

enum class GestureEvent { kNone, kFlipToMute, kPickUpToQuiet };

class AlarmSensorFusionListener {
   public:
    AlarmSensorFusionListener() = default;

    // Evaluates sensor readings (3-axis acceleration and proximity distance in cm)
    GestureEvent ProcessSensorSample(float ax, float ay, float az, float proximity_cm);

    void Reset();

   private:
    float last_magnitude_ = 9.8f;
    bool was_face_down_ = false;
    bool was_picked_up_ = false;
};

}  // namespace edom::alarm::core
