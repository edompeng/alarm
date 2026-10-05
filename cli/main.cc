#include <iostream>
#include <string>

#include "core/src/holiday/holiday_engine_impl.h"

namespace {

void PrintUsage(const char* prog_name) {
    std::cout << "Smart Alarm CLI (Cross-Platform Edition)\n"
              << "Usage: " << prog_name << " <command> [arguments]\n\n"
              << "Commands:\n"
              << "  check-date <YYYY-MM-DD>          Check day type (Workday / Weekend / Holiday)\n"
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

    std::cerr << "Unknown command: " << command << "\n\n";
    PrintUsage(argv[0]);
    return 1;
}
