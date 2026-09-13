#include "core/src/ringtone/streaming_ringtone_resolver.h"
#include "core/src/ringtone/weather_ringtone_mapper.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;

bool TestPlatformIdentification() {
    EXPECT_EQ(static_cast<int>(StreamingRingtoneResolver::IdentifyPlatform("qqmusic://song/12345")),
              static_cast<int>(StreamingPlatform::kQqMusic));

    EXPECT_EQ(
        static_cast<int>(StreamingRingtoneResolver::IdentifyPlatform("cloudmusic://radar/today")),
        static_cast<int>(StreamingPlatform::kNetEaseMusic));

    EXPECT_EQ(static_cast<int>(StreamingRingtoneResolver::IdentifyPlatform(
                  "https://stream.example.com/audio.mp3")),
              static_cast<int>(StreamingPlatform::kCustomStream));

    EXPECT_EQ(static_cast<int>(
                  StreamingRingtoneResolver::IdentifyPlatform("content://media/internal/audio/1")),
              static_cast<int>(StreamingPlatform::kLocal));

    return true;
}

bool TestRingtoneOfflineFallback() {
    const std::string online_stream = "qqmusic://recommend/morning";
    const std::string local_fallback = "android.resource://system/local_chime";

    // Scenario 1: Network available -> Uses streaming URI
    std::string uri_online =
        StreamingRingtoneResolver::ResolvePlayableUri(online_stream, local_fallback, true);
    EXPECT_EQ(uri_online, online_stream);

    // Scenario 2: Network unavailable (Airplane mode / Offline) -> Must fall back to local URI
    std::string uri_offline =
        StreamingRingtoneResolver::ResolvePlayableUri(online_stream, local_fallback, false);
    EXPECT_EQ(uri_offline, local_fallback);

    // Scenario 3: Local URI with no network -> Still uses local URI
    std::string local_uri = "content://settings/system/alarm";
    std::string uri_local_offline =
        StreamingRingtoneResolver::ResolvePlayableUri(local_uri, local_fallback, false);
    EXPECT_EQ(uri_local_offline, local_uri);

    return true;
}

bool TestWeatherRingtoneMapping() {
    // Weather condition parsing
    EXPECT_EQ(static_cast<int>(WeatherRingtoneMapper::ParseWeatherString("晴天 25°C")),
              static_cast<int>(WeatherCondition::kSunny));

    EXPECT_EQ(static_cast<int>(WeatherRingtoneMapper::ParseWeatherString("雷阵雨转小雨")),
              static_cast<int>(WeatherCondition::kRainy));

    EXPECT_EQ(static_cast<int>(WeatherRingtoneMapper::ParseWeatherString("暴雪黄色预警")),
              static_cast<int>(WeatherCondition::kSnowy));

    EXPECT_EQ(static_cast<int>(WeatherRingtoneMapper::ParseWeatherString("多云转阴")),
              static_cast<int>(WeatherCondition::kOvercast));

    // Soundscape URI resolution
    std::string rain_sound =
        WeatherRingtoneMapper::GetSoundscapeUriForWeather(WeatherCondition::kRainy);
    EXPECT_TRUE(rain_sound.find("rain") != std::string::npos);

    std::string sunny_sound =
        WeatherRingtoneMapper::GetSoundscapeUriForWeather(WeatherCondition::kSunny);
    EXPECT_TRUE(sunny_sound.find("sunny") != std::string::npos);

    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestPlatformIdentification);
RUN_TEST(TestRingtoneOfflineFallback);
RUN_TEST(TestWeatherRingtoneMapping);
TEST_MAIN_END
