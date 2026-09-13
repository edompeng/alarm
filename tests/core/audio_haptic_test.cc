#include "core/src/audio/audio_haptic_controller_impl.h"
#include "core/src/audio/haptic_waveform_factory.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;

bool TestVolumeCrescendo() {
    // 0s elapsed with 15s crescendo: volume must be 0.0
    float v0 = AudioHapticControllerImpl::CalculateCrescendoVolume(0.0f, 15.0f, 100);
    EXPECT_EQ(v0, 0.0f);

    // 7.5s elapsed with 15s crescendo (halfway) for 100% target: volume must be ~0.5
    float v_half = AudioHapticControllerImpl::CalculateCrescendoVolume(7.5f, 15.0f, 100);
    EXPECT_TRUE(v_half >= 0.49f && v_half <= 0.51f);

    // 15s elapsed with 15s crescendo for 80% target: volume must be 0.8
    float v_full = AudioHapticControllerImpl::CalculateCrescendoVolume(15.0f, 15.0f, 80);
    EXPECT_TRUE(v_full >= 0.79f && v_full <= 0.81f);

    // 30s elapsed with 15s crescendo (exceeded): volume must clamp at target (0.8)
    float v_clamp = AudioHapticControllerImpl::CalculateCrescendoVolume(30.0f, 15.0f, 80);
    EXPECT_TRUE(v_clamp >= 0.79f && v_clamp <= 0.81f);

    // Immediate full volume (crescendo 0s)
    float v_imm = AudioHapticControllerImpl::CalculateCrescendoVolume(0.0f, 0.0f, 90);
    EXPECT_TRUE(v_imm >= 0.89f && v_imm <= 0.91f);

    return true;
}

bool TestPickupVolumeAttenuation() {
    float full_volume = 0.8f;
    float attenuated = AudioHapticControllerImpl::CalculatePickupVolume(full_volume);
    // Dropped to 25% (0.2f)
    EXPECT_TRUE(attenuated >= 0.19f && attenuated <= 0.21f);

    // If volume is already very low, clamp to min 0.1f
    float low_volume = 0.2f;
    float attenuated_low = AudioHapticControllerImpl::CalculatePickupVolume(low_volume);
    EXPECT_EQ(attenuated_low, 0.1f);

    return true;
}

bool TestHapticWaveformGeneration() {
    // Heartbeat pattern
    HapticWaveform hb =
        HapticWaveformFactory::CreateWaveform(VibrationPatternType::kHeartbeat, 100);
    EXPECT_EQ(hb.timings_ms.size(), 5);
    EXPECT_EQ(hb.amplitudes.size(), 5);
    EXPECT_EQ(hb.amplitudes[3], 255);  // Peak lub-dub amplitude

    // Wave pattern with 50% intensity
    HapticWaveform wave = HapticWaveformFactory::CreateWaveform(VibrationPatternType::kWave, 50);
    EXPECT_EQ(wave.timings_ms.size(), 7);
    int expected_peak = static_cast<int>(255 * 0.5f);
    EXPECT_EQ(wave.amplitudes[3], expected_peak);

    // Staccato pattern
    HapticWaveform staccato =
        HapticWaveformFactory::CreateWaveform(VibrationPatternType::kStaccato, 80);
    EXPECT_EQ(staccato.timings_ms.size(), 7);

    // Pattern string parsing
    EXPECT_EQ(static_cast<int>(HapticWaveformFactory::ParsePattern("WAVE")),
              static_cast<int>(VibrationPatternType::kWave));
    EXPECT_EQ(static_cast<int>(HapticWaveformFactory::ParsePattern("STACCATO")),
              static_cast<int>(VibrationPatternType::kStaccato));
    EXPECT_EQ(static_cast<int>(HapticWaveformFactory::ParsePattern("UNKNOWN")),
              static_cast<int>(VibrationPatternType::kHeartbeat));

    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestVolumeCrescendo);
RUN_TEST(TestPickupVolumeAttenuation);
RUN_TEST(TestHapticWaveformGeneration);
TEST_MAIN_END
