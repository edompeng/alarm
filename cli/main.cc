#include <chrono>
#include <cerrno>
#include <cstdlib>
#include <iostream>
#include <string>
#include <vector>

#include "core/src/challenge/challenge_engine_impl.h"
#include "core/src/common/iso_date.h"
#include "core/src/holiday/holiday_engine_impl.h"
#include "core/src/ringtone/weather_ringtone_mapper.h"
#include "core/src/scheduler/countdown_formatter.h"

namespace {

void PrintUsage(const char* prog_name) {
    std::cout << "Smart Alarm CLI (Cross-Platform Edition)\n"
              << "Usage: " << prog_name << " <command> [arguments]\n\n"
              << "Commands:\n"
              << "  check-date <YYYY-MM-DD>          Check day type (Workday / Weekend / Holiday)\n"
              << "  countdown <minutes>              Format countdown in Chinese and English\n"
              << "  weather <condition>              Query dynamic soundscape URI for weather\n"
              << "  math-challenge                   Generate an interactive math challenge\n"
              << "  version                          Print version and architecture info\n"
              << "  help                             Show this help message\n";
}

#ifndef SMART_ALARM_VERSION
#define SMART_ALARM_VERSION "1.0.0"
#endif

void PrintVersion() {
    std::cout << "Smart Alarm Core Engine v" << SMART_ALARM_VERSION << "\n"
              << "Standard: C++20 Clean Architecture\n"
              << "Platforms: Android (Universal/ARM64/ARMv7/x86_64), Linux, macOS, Windows\n";
}

// Parses a strictly positive minute count, rejecting trailing garbage and
// out-of-range values instead of relying on atoi's undefined overflow behaviour.
bool ParsePositiveMinutes(const char* text, int* out_minutes) {
    if (text == nullptr || *text == '\0') {
        return false;
    }
    errno = 0;
    char* end = nullptr;
    const long long value = std::strtoll(text, &end, 10);
    if (errno != 0 || end == text || *end != '\0') {
        return false;
    }
    if (value < 1 || value > 1000000) {
        return false;
    }
    *out_minutes = static_cast<int>(value);
    return true;
}

int HandleCheckDate(const std::string& date_str) {
    edom::alarm::core::HolidayEngineImpl engine(nullptr);
    const int dow = edom::alarm::core::HolidayEngineImpl::GetDayOfWeek(date_str);
    if (dow < 0) {
        std::cerr << "Error: invalid date '" << date_str << "', expected YYYY-MM-DD.\n";
        return 1;
    }
    auto day_type = engine.ClassifyDate(date_str);
    bool is_workday = engine.IsStatutoryWorkday(date_str);

    const char* dow_str[] = {"Sunday",   "Monday", "Tuesday", "Wednesday",
                             "Thursday", "Friday", "Saturday"};

    std::cout << "Date: " << date_str << " (" << dow_str[dow % 7] << ")\n";
    std::cout << "Classification: ";
    switch (day_type) {
        case edom::alarm::data::DayType::kWorkday:
            std::cout << "Regular Workday (常规工作日)\n";
            break;
        case edom::alarm::data::DayType::kWeekend:
            std::cout << "Weekend Rest (周末休息日)\n";
            break;
        case edom::alarm::data::DayType::kStatutoryHoliday:
            std::cout << "Statutory Holiday (法定节假日)\n";
            break;
        case edom::alarm::data::DayType::kCompensatoryWorkday:
            std::cout << "Compensatory Workday (调休补班工作日)\n";
            break;
        default:
            std::cout << "Unknown\n";
            break;
    }
    std::cout << "Alarm rings: " << (is_workday ? "YES (响铃)" : "NO (跳过)") << "\n";
    return 0;
}

int HandleCountdown(int minutes) {
    if (minutes <= 0) {
        std::cerr << "Error: minutes must be positive.\n";
        return 1;
    }
    auto now_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                      std::chrono::system_clock::now().time_since_epoch())
                      .count();
    int64_t target_ms = now_ms + static_cast<int64_t>(minutes) * 60 * 1000;

    std::cout << "Countdown for " << minutes << " minutes:\n";
    std::cout << "  ZH: "
              << edom::alarm::core::CountdownFormatter::FormatCountdownZh(target_ms, now_ms)
              << "\n";
    std::cout << "  EN: "
              << edom::alarm::core::CountdownFormatter::FormatCountdownEn(target_ms, now_ms)
              << "\n";
    return 0;
}

int HandleWeather(const std::string& weather_desc) {
    auto cond = edom::alarm::core::WeatherRingtoneMapper::ParseWeatherString(weather_desc);
    std::string uri = edom::alarm::core::WeatherRingtoneMapper::GetSoundscapeUriForWeather(cond);
    std::cout << "Weather: " << weather_desc << "\n";
    std::cout << "Mapped Soundscape URI: " << uri << "\n";
    return 0;
}

int HandleMathChallenge() {
    edom::alarm::core::ChallengeEngineImpl engine;
    auto state = engine.CreateChallenge(edom::alarm::core::ChallengeType::kMath, 1);
    std::cout << "Math Anti-Snooze Challenge:\n";
    std::cout << "Question: " << state.prompt_text << "\n";
    std::cout << "Expected Answer: " << state.expected_answer << "\n";
    return 0;
}

}  // namespace

int main(int argc, char* argv[]) {
    if (argc < 2) {
        PrintUsage(argv[0]);
        return 0;
    }

    std::string command = argv[1];

    if (command == "help" || command == "--help" || command == "-h") {
        PrintUsage(argv[0]);
        return 0;
    }
    if (command == "version" || command == "--version" || command == "-v") {
        PrintVersion();
        return 0;
    }
    if (command == "check-date") {
        if (argc < 3) {
            std::cerr << "Usage: " << argv[0] << " check-date <YYYY-MM-DD>\n";
            return 1;
        }
        return HandleCheckDate(argv[2]);
    }
    if (command == "countdown") {
        if (argc < 3) {
            std::cerr << "Usage: " << argv[0] << " countdown <minutes>\n";
            return 1;
        }
        int mins = 0;
        if (!ParsePositiveMinutes(argv[2], &mins)) {
            std::cerr << "Error: minutes must be a whole number between 1 and 1000000.\n";
            return 1;
        }
        return HandleCountdown(mins);
    }
    if (command == "weather") {
        if (argc < 3) {
            std::cerr << "Usage: " << argv[0] << " weather <condition>\n";
            return 1;
        }
        return HandleWeather(argv[2]);
    }
    if (command == "math-challenge") {
        return HandleMathChallenge();
    }

    std::cerr << "Unknown command: " << command << "\n\n";
    PrintUsage(argv[0]);
    return 1;
}
