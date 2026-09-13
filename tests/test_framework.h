#pragma once

#include <cstdlib>
#include <iostream>
#include <string>

namespace edom::alarm::test {

inline int g_test_count = 0;
inline int g_pass_count = 0;
inline int g_fail_count = 0;

#define EXPECT_TRUE(cond)                                                                      \
    do {                                                                                       \
        if (!(cond)) {                                                                         \
            std::cerr << "[FAILED] " << __FILE__ << ":" << __LINE__ << " (" #cond " is false)" \
                      << std::endl;                                                            \
            ::edom::alarm::test::g_fail_count++;                                               \
            return false;                                                                      \
        }                                                                                      \
    } while (0)

#define EXPECT_FALSE(cond) EXPECT_TRUE(!(cond))

#define EXPECT_EQ(a, b)                                                                        \
    do {                                                                                       \
        if ((a) != (b)) {                                                                      \
            std::cerr << "[FAILED] " << __FILE__ << ":" << __LINE__ << " (" #a " != " #b ") [" \
                      << (a) << " vs " << (b) << "]" << std::endl;                             \
            ::edom::alarm::test::g_fail_count++;                                               \
            return false;                                                                      \
        }                                                                                      \
    } while (0)

#define EXPECT_NE(a, b)                                                                      \
    do {                                                                                     \
        if ((a) == (b)) {                                                                    \
            std::cerr << "[FAILED] " << __FILE__ << ":" << __LINE__ << " (" #a " == " #b ")" \
                      << std::endl;                                                          \
            ::edom::alarm::test::g_fail_count++;                                             \
            return false;                                                                    \
        }                                                                                    \
    } while (0)

#define RUN_TEST(func)                                          \
    do {                                                        \
        ::edom::alarm::test::g_test_count++;                    \
        std::cout << "[ RUN      ] " << #func << std::endl;     \
        if (func()) {                                           \
            ::edom::alarm::test::g_pass_count++;                \
            std::cout << "[       OK ] " << #func << std::endl; \
        } else {                                                \
            std::cerr << "[  FAILED  ] " << #func << std::endl; \
        }                                                       \
    } while (0)

#define TEST_MAIN_BEGIN int main() {
#define TEST_MAIN_END                                                            \
    std::cout << "========================================" << std::endl;        \
    std::cout << "Tests run: " << ::edom::alarm::test::g_test_count              \
              << ", Passed: " << ::edom::alarm::test::g_pass_count               \
              << ", Failed: " << ::edom::alarm::test::g_fail_count << std::endl; \
    return ::edom::alarm::test::g_fail_count == 0 ? 0 : 1;                       \
    }

}  // namespace edom::alarm::test
