#pragma once

#include <cstddef>
#include <string>

namespace edom::alarm::core {

// Parses a strict "YYYY-MM-DD" string. Returns false for malformed input or
// impossible calendar dates (for example "2026-02-30"). Never throws, so it is
// safe to use on untrusted payloads coming from remote calendar syncs.
inline bool ParseIsoDate(const std::string& date_str, int* year, int* month, int* day) {
    if (date_str.size() != 10 || date_str[4] != '-' || date_str[7] != '-') {
        return false;
    }
    int parts[3] = {0, 0, 0};
    const std::size_t offsets[3] = {0, 5, 8};
    const std::size_t lengths[3] = {4, 2, 2};
    for (int part = 0; part < 3; ++part) {
        for (std::size_t i = 0; i < lengths[part]; ++i) {
            const char c = date_str[offsets[part] + i];
            if (c < '0' || c > '9') {
                return false;
            }
            parts[part] = parts[part] * 10 + (c - '0');
        }
    }
    if (parts[0] < 1970 || parts[0] > 9999 || parts[1] < 1 || parts[1] > 12) {
        return false;
    }
    static const int kDaysPerMonth[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    int max_day = kDaysPerMonth[parts[1] - 1];
    const bool leap_year = (parts[0] % 4 == 0 && parts[0] % 100 != 0) || parts[0] % 400 == 0;
    if (parts[1] == 2 && leap_year) {
        max_day = 29;
    }
    if (parts[2] < 1 || parts[2] > max_day) {
        return false;
    }
    if (year != nullptr) {
        *year = parts[0];
    }
    if (month != nullptr) {
        *month = parts[1];
    }
    if (day != nullptr) {
        *day = parts[2];
    }
    return true;
}

}  // namespace edom::alarm::core
