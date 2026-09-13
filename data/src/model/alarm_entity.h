#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::data {

enum class RepeatMode { kOnce = 0, kSpecificDays = 1, kStatutoryWorkdays = 2 };

struct AlarmEntity {
    int64_t id = 0;
    std::string label;
    int hour = 8;
    int minute = 0;
    bool is_enabled = true;
    RepeatMode repeat_mode = RepeatMode::kOnce;
    int days_bitmask = 0;  // Bit 0 = Sunday, Bit 1 = Monday ... Bit 6 = Saturday
    int volume = 80;
    int crescendo_seconds = 15;
    std::string vibration_pattern = "HEARTBEAT";
    int vibration_intensity = 80;
    bool force_speaker = true;
    std::string ringtone_type = "LOCAL";
    std::string ringtone_uri = "content://settings/system/alarm_alert";
    std::string ringtone_fallback_uri = "android.resource://system/alarm_beep";
    int snooze_interval_minutes = 10;
    int snooze_max_count = 3;  // -1 indicates infinite
    std::string challenge_type = "NONE";
    int challenge_difficulty = 1;
    bool tts_enabled = false;
    int64_t next_trigger_time = 0;
    int64_t created_at = 0;
    int64_t updated_at = 0;

    // Validation enforcing schema constraints
    bool IsValid() const;
};

}  // namespace edom::alarm::data
