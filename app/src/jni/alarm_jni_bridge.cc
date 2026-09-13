#include <string>

#include "core/src/audio/audio_haptic_controller_impl.h"
#include "core/src/audio/haptic_waveform_factory.h"
#include "core/src/challenge/challenge_engine_impl.h"
#include "core/src/holiday/holiday_engine_impl.h"
#include "core/src/ringtone/streaming_ringtone_resolver.h"
#include "core/src/ringtone/weather_ringtone_mapper.h"
#include "core/src/scheduler/alarm_scheduler_impl.h"
#include "core/src/scheduler/countdown_formatter.h"

namespace edom::alarm::jni {

// Exported C functions for JNI or direct dynamic invocation
extern "C" {

bool AlarmNative_IsStatutoryWorkday(const char* date_str) {
    if (!date_str) return false;
    core::HolidayEngineImpl engine(nullptr);
    return engine.IsStatutoryWorkday(date_str);
}

int64_t AlarmNative_CalculateNextTriggerTime(int hour, int minute, int repeat_mode,
                                             int days_bitmask, int64_t from_epoch_ms) {
    core::AlarmSchedulerImpl scheduler(nullptr);
    data::AlarmEntity entity;
    entity.hour = hour;
    entity.minute = minute;
    entity.repeat_mode = static_cast<data::RepeatMode>(repeat_mode);
    entity.days_bitmask = days_bitmask;
    return scheduler.CalculateNextTriggerTime(entity, from_epoch_ms, {});
}

const char* AlarmNative_GetWeatherSoundscape(const char* weather_desc) {
    if (!weather_desc) return "android.resource://soundscape/default_ambient";
    auto cond = core::WeatherRingtoneMapper::ParseWeatherString(weather_desc);
    static std::string soundscape_uri;
    soundscape_uri = core::WeatherRingtoneMapper::GetSoundscapeUriForWeather(cond);
    return soundscape_uri.c_str();
}
}

}  // namespace edom::alarm::jni
