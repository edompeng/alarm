#pragma once

#include <string>

namespace edom::alarm::core {

enum class WeatherCondition { kSunny, kOvercast, kRainy, kSnowy, kUnknown };

class WeatherRingtoneMapper {
   public:
    static std::string GetSoundscapeUriForWeather(WeatherCondition condition);
    static WeatherCondition ParseWeatherString(const std::string& weather_desc);
};

}  // namespace edom::alarm::core
