#pragma once

#include <string>

namespace edom::alarm::data {

class DatabaseContract {
   public:
    static constexpr const char* kDatabaseName = "smart_alarm.db";
    static constexpr int kDatabaseVersion = 1;

    // Table: alarms
    class AlarmsTable {
       public:
        static constexpr const char* kTableName = "alarms";
        static constexpr const char* kColId = "id";
        static constexpr const char* kColLabel = "label";
        static constexpr const char* kColHour = "hour";
        static constexpr const char* kColMinute = "minute";
        static constexpr const char* kColIsEnabled = "is_enabled";
        static constexpr const char* kColRepeatMode = "repeat_mode";
        static constexpr const char* kColDaysBitmask = "days_bitmask";
        static constexpr const char* kColVolume = "volume";
        static constexpr const char* kColCrescendoSeconds = "crescendo_seconds";
        static constexpr const char* kColVibrationPattern = "vibration_pattern";
        static constexpr const char* kColVibrationIntensity = "vibration_intensity";
        static constexpr const char* kColForceSpeaker = "force_speaker";
        static constexpr const char* kColRingtoneType = "ringtone_type";
        static constexpr const char* kColRingtoneUri = "ringtone_uri";
        static constexpr const char* kColRingtoneFallbackUri = "ringtone_fallback_uri";
        static constexpr const char* kColSnoozeIntervalMinutes = "snooze_interval_minutes";
        static constexpr const char* kColSnoozeMaxCount = "snooze_max_count";
        static constexpr const char* kColChallengeType = "challenge_type";
        static constexpr const char* kColChallengeDifficulty = "challenge_difficulty";
        static constexpr const char* kColTtsEnabled = "tts_enabled";
        static constexpr const char* kColNextTriggerTime = "next_trigger_time";
        static constexpr const char* kColCreatedAt = "created_at";
        static constexpr const char* kColUpdatedAt = "updated_at";
    };

    // Table: alarm_skip_rules
    class SkipRulesTable {
       public:
        static constexpr const char* kTableName = "alarm_skip_rules";
        static constexpr const char* kColId = "id";
        static constexpr const char* kColAlarmId = "alarm_id";
        static constexpr const char* kColSkipDate = "skip_date";
        static constexpr const char* kColCreatedAt = "created_at";
    };

    // Table: holiday_calendar
    class HolidayTable {
       public:
        static constexpr const char* kTableName = "holiday_calendar";
        static constexpr const char* kColDateStr = "date_str";
        static constexpr const char* kColYear = "year";
        static constexpr const char* kColDayType = "day_type";
        static constexpr const char* kColName = "name";
        static constexpr const char* kColUpdatedAt = "updated_at";
    };

    // Table: app_configurations
    class ConfigTable {
       public:
        static constexpr const char* kTableName = "app_configurations";
        static constexpr const char* kColKey = "config_key";
        static constexpr const char* kColValue = "config_value";
        static constexpr const char* kColUpdatedAt = "updated_at";
    };
};

}  // namespace edom::alarm::data
