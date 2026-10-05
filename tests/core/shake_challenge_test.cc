#include "core/src/challenge/challenge_engine_impl.h"
#include "core/src/sensor/alarm_sensor_fusion_listener.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;

bool TestShakeChallengeProgression() {
    ChallengeEngineImpl engine;

    // Target 5 shakes for testing
    ChallengeState state = engine.CreateChallenge(ChallengeType::kShake, 5);
    EXPECT_EQ(state.target_shake_count, 5);
    EXPECT_EQ(state.current_shake_count, 0);
    EXPECT_FALSE(state.is_completed);

    // Normal resting gravity (ax=0, ay=0, az=9.8): No shake
    int progress0 = engine.RegisterShakeSample(&state, 0.0f, 0.0f, 9.8f);
    EXPECT_EQ(progress0, 0);
    EXPECT_EQ(state.current_shake_count, 0);

    // Simulate 5 vigorous shakes exceeding 14.0 m/s^2
    for (int i = 1; i <= 5; ++i) {
        // High surge
        engine.RegisterShakeSample(&state, 0.0f, 15.5f, 9.8f);
        // Return to rest
        engine.RegisterShakeSample(&state, 0.0f, 0.0f, 9.8f);
    }

    EXPECT_EQ(state.current_shake_count, 5);
    EXPECT_TRUE(state.is_completed);
    return true;
}

bool TestSensorFusionFlipAndPickup() {
    AlarmSensorFusionListener listener;

    // Resting on table screen-up (az = +9.8, proximity = 10.0cm)
    GestureEvent e1 = listener.ProcessSensorSample(0.0f, 0.0f, 9.8f, 10.0f);
    EXPECT_EQ(static_cast<int>(e1), static_cast<int>(GestureEvent::kNone));

    // Flip face down on table (az = -9.8, proximity = 0.5cm)
    GestureEvent e2 = listener.ProcessSensorSample(0.0f, 0.0f, -9.8f, 0.5f);
    EXPECT_EQ(static_cast<int>(e2), static_cast<int>(GestureEvent::kFlipToMute));

    // Picked up from table (surge delta > 1.2 m/s^2, proximity clear > 3.0cm)
    GestureEvent e3 = listener.ProcessSensorSample(0.0f, 5.0f, 11.5f, 15.0f);
    EXPECT_EQ(static_cast<int>(e3), static_cast<int>(GestureEvent::kPickUpToQuiet));

    return true;
}

bool TestShakeChallengeInvalidTargetIsSafe() {
    ChallengeEngineImpl engine;
    ChallengeState state;
    state.type = ChallengeType::kShake;
    state.target_shake_count = 0;  // Corrupt or legacy value must not divide by zero.

    int progress = engine.RegisterShakeSample(&state, 0.0f, 15.5f, 9.8f);
    EXPECT_TRUE(progress >= 0 && progress <= 100);
    EXPECT_TRUE(state.current_shake_count >= 0);
    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestShakeChallengeProgression);
RUN_TEST(TestSensorFusionFlipAndPickup);
RUN_TEST(TestShakeChallengeInvalidTargetIsSafe);
TEST_MAIN_END
