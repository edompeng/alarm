#include "core/src/ringtone/weather_ringtone_mapper.h"

namespace edom::alarm::core {

WeatherCondition WeatherRingtoneMapper::ParseWeatherString(const std::string& weather_desc) {
    if (weather_desc.find("晴") != std::string::npos ||
        weather_desc.find("Sun") != std::string::npos) {
        return WeatherCondition::kSunny;
    }
    if (weather_desc.find("阴") != std::string::npos ||
        weather_desc.find("多云") != std::string::npos ||
        weather_desc.find("Cloud") != std::string::npos) {
        return WeatherCondition::kOvercast;
    }
    if (weather_desc.find("雨") != std::string::npos ||
        weather_desc.find("Rain") != std::string::npos) {
        return WeatherCondition::kRainy;
    }
    if (weather_desc.find("雪") != std::string::npos ||
        weather_desc.find("Snow") != std::string::npos) {
        return WeatherCondition::kSnowy;
    }
    return WeatherCondition::kUnknown;
}

std::string WeatherRingtoneMapper::GetSoundscapeUriForWeather(WeatherCondition condition) {
    switch (condition) {
        case WeatherCondition::kSunny:
            return "android.resource://soundscape/sunny_morning_birds";
        case WeatherCondition::kOvercast:
            return "android.resource://soundscape/gentle_morning_wind";
        case WeatherCondition::kRainy:
            return "android.resource://soundscape/calm_raindrops";
        case WeatherCondition::kSnowy:
            return "android.resource://soundscape/soft_winter_snow";
        default:
            return "android.resource://soundscape/default_ambient";
    }
}

}  // namespace edom::alarm::core
