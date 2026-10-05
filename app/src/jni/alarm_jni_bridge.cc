#if __has_include(<jni.h>)
#include <jni.h>
#else
typedef void* jobject;
typedef void* JNIEnv;
typedef void* jclass;
typedef int jint;
typedef long long jlong;
typedef unsigned char jboolean;
#endif

#include <cstdint>
#include <functional>
#include <memory>
#include <string>

#include "core/src/adapter/i_platform_audio.h"
#include "core/src/adapter/i_platform_haptics.h"
#include "core/src/adapter/i_platform_scheduler.h"
#include "core/src/adapter/i_platform_sensor.h"
#include "core/src/audio/audio_haptic_controller_impl.h"
#include "core/src/audio/haptic_waveform_factory.h"
#include "core/src/challenge/challenge_engine_impl.h"
#include "core/src/holiday/holiday_engine_impl.h"
#include "core/src/ringtone/streaming_ringtone_resolver.h"
#include "core/src/ringtone/weather_ringtone_mapper.h"
#include "core/src/scheduler/alarm_scheduler_impl.h"

namespace edom::alarm::jni {

// Android platform implementation of IPlatformScheduler
class AndroidPlatformScheduler : public core::IPlatformScheduler {
   public:
    using ScheduleCallback =
        std::function<bool(int64_t alarm_id, int64_t trigger_time_ms, const std::string& title)>;
    using CancelCallback = std::function<bool(int64_t alarm_id)>;

    void SetScheduleCallback(ScheduleCallback cb) { schedule_cb_ = std::move(cb); }
    void SetCancelCallback(CancelCallback cb) { cancel_cb_ = std::move(cb); }

    bool ScheduleExactAlarm(int64_t alarm_id, int64_t trigger_time_ms,
                            const std::string& title) override {
        if (schedule_cb_) {
            return schedule_cb_(alarm_id, trigger_time_ms, title);
        }
        return true;
    }

    bool CancelAlarm(int64_t alarm_id) override {
        if (cancel_cb_) {
            return cancel_cb_(alarm_id);
        }
        return true;
    }

    bool ScheduleAdvanceNotification(int64_t alarm_id, int64_t advance_time_ms) override {
        return true;
    }

    bool CancelAdvanceNotification(int64_t alarm_id) override { return true; }

   private:
    ScheduleCallback schedule_cb_;
    CancelCallback cancel_cb_;
};

// Android platform implementation of IPlatformAudio
class AndroidPlatformAudio : public core::IPlatformAudio {
   public:
    using AudioActionCallback =
        std::function<void(const std::string& uri, int routing_mode, float volume)>;

    void SetAudioActionCallback(AudioActionCallback cb) { action_cb_ = std::move(cb); }

    bool StartRingtone(const std::string& audio_uri, core::AudioRoutingMode routing_mode) override {
        current_uri_ = audio_uri;
        current_mode_ = routing_mode;
        is_playing_ = true;
        if (action_cb_) {
            action_cb_(audio_uri, static_cast<int>(routing_mode), current_volume_);
        }
        return true;
    }

    void RampVolume(float target_volume, int32_t duration_ms) override {
        current_volume_ = target_volume;
    }

    void AttenuateVolume(float level) override { current_volume_ = level; }

    void StopRingtone() override {
        is_playing_ = false;
        if (action_cb_) {
            action_cb_("", 0, 0.0f);
        }
    }

   private:
    std::string current_uri_;
    core::AudioRoutingMode current_mode_ = core::AudioRoutingMode::kSystemDefault;
    float current_volume_ = 1.0f;
    bool is_playing_ = false;
    AudioActionCallback action_cb_;
};

// Android platform implementation of IPlatformHaptics
class AndroidPlatformHaptics : public core::IPlatformHaptics {
   public:
    using HapticCallback = std::function<void(int pattern, float intensity)>;

    void SetHapticCallback(HapticCallback cb) { haptic_cb_ = std::move(cb); }

    bool StartVibration(core::VibrationPattern pattern, float intensity) override {
        is_vibrating_ = true;
        if (haptic_cb_) {
            haptic_cb_(static_cast<int>(pattern), intensity);
        }
        return true;
    }

    void StopVibration() override {
        is_vibrating_ = false;
        if (haptic_cb_) {
            haptic_cb_(-1, 0.0f);
        }
    }

   private:
    bool is_vibrating_ = false;
    HapticCallback haptic_cb_;
};

// Android platform implementation of IPlatformSensor
class AndroidPlatformSensor : public core::IPlatformSensor {
   public:
    bool StartListening(core::ISensorObserver* observer, int32_t target_shake_count) override {
        observer_ = observer;
        target_shake_count_ = target_shake_count;
        is_listening_ = true;
        return true;
    }

    void StopListening() override {
        observer_ = nullptr;
        is_listening_ = false;
    }

    void DispatchFlipEvent() {
        if (observer_) observer_->OnDeviceFlippedFaceDown();
    }

    void DispatchPickUpEvent() {
        if (observer_) observer_->OnDevicePickedUp();
    }

    void DispatchShakeProgress(int32_t current) {
        if (observer_) observer_->OnShakeProgress(current, target_shake_count_);
    }

   private:
    core::ISensorObserver* observer_ = nullptr;
    int32_t target_shake_count_ = 0;
    bool is_listening_ = false;
};

// Global adapter instances
static std::unique_ptr<AndroidPlatformScheduler> g_scheduler_adapter =
    std::make_unique<AndroidPlatformScheduler>();
static std::unique_ptr<AndroidPlatformAudio> g_audio_adapter =
    std::make_unique<AndroidPlatformAudio>();
static std::unique_ptr<AndroidPlatformHaptics> g_haptics_adapter =
    std::make_unique<AndroidPlatformHaptics>();
static std::unique_ptr<AndroidPlatformSensor> g_sensor_adapter =
    std::make_unique<AndroidPlatformSensor>();

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

// Platform Adapter JNI entrypoints
core::IPlatformScheduler* AlarmNative_GetSchedulerAdapter() { return g_scheduler_adapter.get(); }

core::IPlatformAudio* AlarmNative_GetAudioAdapter() { return g_audio_adapter.get(); }

core::IPlatformHaptics* AlarmNative_GetHapticsAdapter() { return g_haptics_adapter.get(); }

core::IPlatformSensor* AlarmNative_GetSensorAdapter() { return g_sensor_adapter.get(); }

void AlarmNative_OnSensorFlip() {
    if (g_sensor_adapter) g_sensor_adapter->DispatchFlipEvent();
}

void AlarmNative_OnSensorPickUp() {
    if (g_sensor_adapter) g_sensor_adapter->DispatchPickUpEvent();
}

void AlarmNative_OnSensorShake(int32_t current_count) {
    if (g_sensor_adapter) g_sensor_adapter->DispatchShakeProgress(current_count);
}

}  // extern "C"

}  // namespace edom::alarm::jni
