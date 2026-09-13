#include "data/src/model/alarm_entity.h"

namespace edom::alarm::data {

bool AlarmEntity::IsValid() const {
    if (hour < 0 || hour > 23) {
        return false;
    }
    if (minute < 0 || minute > 59) {
        return false;
    }
    if (volume < 0 || volume > 100) {
        return false;
    }
    if (crescendo_seconds < 0 || crescendo_seconds > 30) {
        return false;
    }
    if (vibration_intensity < 0 || vibration_intensity > 100) {
        return false;
    }
    if (snooze_interval_minutes < 1 || snooze_interval_minutes > 60) {
        return false;
    }
    if (repeat_mode != RepeatMode::kOnce && repeat_mode != RepeatMode::kSpecificDays &&
        repeat_mode != RepeatMode::kStatutoryWorkdays) {
        return false;
    }
    return true;
}

}  // namespace edom::alarm::data
