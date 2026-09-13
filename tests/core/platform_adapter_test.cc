#include <cmath>
#include <string>
#include <vector>

#include "core/src/adapter/i_platform_audio.h"
#include "core/src/adapter/i_platform_haptics.h"
#include "core/src/adapter/i_platform_scheduler.h"
#include "core/src/adapter/i_platform_sensor.h"
#include "tests/test_framework.h"

namespace edom::alarm::test {

class MockPlatformScheduler : public core::IPlatformScheduler {
   public:
    bool ScheduleExactAlarm(int64_t alarm_id, int64_t trigger_time_ms,
                            const std::string& title) override {
        last_scheduled_id_ = alarm_id;
        last_trigger_ms_ = trigger_time_ms;
        last_title_ = title;
        scheduled_count_++;
        return true;
    }

    bool CancelAlarm(int64_t alarm_id) override {
        last_cancelled_id_ = alarm_id;
        cancel_count_++;
        return true;
    }

    bool ScheduleAdvanceNotification(int64_t alarm_id, int64_t advance_time_ms) override {
        last_advance_id_ = alarm_id;
        last_advance_ms_ = advance_time_ms;
        advance_count_++;
        return true;
    }

    bool CancelAdvanceNotification(int64_t alarm_id) override {
        advance_cancel_count_++;
        return true;
    }

    int64_t last_scheduled_id_ = 0;
    int64_t last_trigger_ms_ = 0;
    std::string last_title_;
    int scheduled_count_ = 0;
    int64_t last_cancelled_id_ = 0;
    int cancel_count_ = 0;
    int64_t last_advance_id_ = 0;
    int64_t last_advance_ms_ = 0;
    int advance_count_ = 0;
    int advance_cancel_count_ = 0;
};

class MockPlatformAudio : public core::IPlatformAudio {
   public:
    bool StartRingtone(const std::string& audio_uri, core::AudioRoutingMode routing_mode) override {
        last_uri_ = audio_uri;
        last_routing_mode_ = routing_mode;
        is_playing_ = true;
        return true;
    }

    void RampVolume(float target_volume, int32_t duration_ms) override {
        target_volume_ = target_volume;
        ramp_duration_ms_ = duration_ms;
    }

    void AttenuateVolume(float level) override {
        attenuated_level_ = level;
        is_attenuated_ = true;
    }

    void StopRingtone() override { is_playing_ = false; }

    std::string last_uri_;
    core::AudioRoutingMode last_routing_mode_ = core::AudioRoutingMode::kSystemDefault;
    bool is_playing_ = false;
    float target_volume_ = 1.0f;
    int32_t ramp_duration_ms_ = 0;
    float attenuated_level_ = 1.0f;
    bool is_attenuated_ = false;
};

class MockPlatformHaptics : public core::IPlatformHaptics {
   public:
    bool StartVibration(core::VibrationPattern pattern, float intensity) override {
        last_pattern_ = pattern;
        last_intensity_ = intensity;
        is_vibrating_ = true;
        return true;
    }

    void StopVibration() override { is_vibrating_ = false; }

    core::VibrationPattern last_pattern_ = core::VibrationPattern::kContinuous;
    float last_intensity_ = 0.0f;
    bool is_vibrating_ = false;
};

class MockSensorObserver : public core::ISensorObserver {
   public:
    void OnDeviceFlippedFaceDown() override { flipped_count_++; }

    void OnDevicePickedUp() override { picked_up_count_++; }

    void OnShakeProgress(int32_t current_count, int32_t target_count) override {
        last_shake_current_ = current_count;
        last_shake_target_ = target_count;
    }

    int flipped_count_ = 0;
    int picked_up_count_ = 0;
    int32_t last_shake_current_ = 0;
    int32_t last_shake_target_ = 0;
};

class MockPlatformSensor : public core::IPlatformSensor {
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

    void SimulateFlip() {
        if (observer_) observer_->OnDeviceFlippedFaceDown();
    }

    void SimulatePickUp() {
        if (observer_) observer_->OnDevicePickedUp();
    }

    void SimulateShake(int32_t count) {
        if (observer_) observer_->OnShakeProgress(count, target_shake_count_);
    }

    core::ISensorObserver* observer_ = nullptr;
    int32_t target_shake_count_ = 0;
    bool is_listening_ = false;
};

bool TestPlatformSchedulerAdapter() {
    MockPlatformScheduler scheduler;
    EXPECT_TRUE(scheduler.ScheduleExactAlarm(101, 1726200000000LL, "Workday Alarm"));
    EXPECT_EQ(scheduler.scheduled_count_, 1);
    EXPECT_EQ(scheduler.last_scheduled_id_, 101);
    EXPECT_EQ(scheduler.last_trigger_ms_, 1726200000000LL);
    EXPECT_EQ(scheduler.last_title_, "Workday Alarm");

    EXPECT_TRUE(scheduler.ScheduleAdvanceNotification(101, 1726197000000LL));
    EXPECT_EQ(scheduler.advance_count_, 1);
    EXPECT_EQ(scheduler.last_advance_ms_, 1726197000000LL);

    EXPECT_TRUE(scheduler.CancelAlarm(101));
    EXPECT_EQ(scheduler.cancel_count_, 1);
    EXPECT_EQ(scheduler.last_cancelled_id_, 101);
    return true;
}

bool TestPlatformAudioAdapter() {
    MockPlatformAudio audio;
    EXPECT_TRUE(audio.StartRingtone("content://ringtone/1", core::AudioRoutingMode::kForceSpeaker));
    EXPECT_TRUE(audio.is_playing_);
    EXPECT_EQ(audio.last_uri_, "content://ringtone/1");
    EXPECT_TRUE(audio.last_routing_mode_ == core::AudioRoutingMode::kForceSpeaker);

    audio.RampVolume(0.9f, 15000);
    EXPECT_TRUE(std::abs(audio.target_volume_ - 0.9f) < 0.001f);
    EXPECT_EQ(audio.ramp_duration_ms_, 15000);

    audio.AttenuateVolume(0.2f);
    EXPECT_TRUE(audio.is_attenuated_);
    EXPECT_TRUE(std::abs(audio.attenuated_level_ - 0.2f) < 0.001f);

    audio.StopRingtone();
    EXPECT_FALSE(audio.is_playing_);
    return true;
}

bool TestPlatformHapticsAdapter() {
    MockPlatformHaptics haptics;
    EXPECT_TRUE(haptics.StartVibration(core::VibrationPattern::kHeartbeat, 0.85f));
    EXPECT_TRUE(haptics.is_vibrating_);
    EXPECT_TRUE(haptics.last_pattern_ == core::VibrationPattern::kHeartbeat);
    EXPECT_TRUE(std::abs(haptics.last_intensity_ - 0.85f) < 0.001f);

    haptics.StopVibration();
    EXPECT_FALSE(haptics.is_vibrating_);
    return true;
}

bool TestPlatformSensorAdapter() {
    MockPlatformSensor sensor;
    MockSensorObserver observer;

    EXPECT_TRUE(sensor.StartListening(&observer, 30));
    EXPECT_TRUE(sensor.is_listening_);

    sensor.SimulateFlip();
    EXPECT_EQ(observer.flipped_count_, 1);

    sensor.SimulatePickUp();
    EXPECT_EQ(observer.picked_up_count_, 1);

    sensor.SimulateShake(15);
    EXPECT_EQ(observer.last_shake_current_, 15);
    EXPECT_EQ(observer.last_shake_target_, 30);

    sensor.StopListening();
    EXPECT_FALSE(sensor.is_listening_);
    return true;
}

}  // namespace edom::alarm::test

TEST_MAIN_BEGIN
RUN_TEST(::edom::alarm::test::TestPlatformSchedulerAdapter);
RUN_TEST(::edom::alarm::test::TestPlatformAudioAdapter);
RUN_TEST(::edom::alarm::test::TestPlatformHapticsAdapter);
RUN_TEST(::edom::alarm::test::TestPlatformSensorAdapter);
TEST_MAIN_END
