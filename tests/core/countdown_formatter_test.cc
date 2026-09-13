#include "core/src/scheduler/countdown_formatter.h"

#include "tests/test_framework.h"

using namespace edom::alarm::core;

bool TestCountdownFormattingZh() {
    int64_t now = 1000000000000LL;

    // 45 minutes delta
    int64_t t_45m = now + (45LL * 60 * 1000);
    std::string s_45m = CountdownFormatter::FormatCountdownZh(t_45m, now);
    EXPECT_EQ(s_45m, std::string("距离响铃还有 45 分钟"));

    // 2 hours 10 minutes delta
    int64_t t_2h10m = now + (130LL * 60 * 1000);
    std::string s_2h10m = CountdownFormatter::FormatCountdownZh(t_2h10m, now);
    EXPECT_EQ(s_2h10m, std::string("距离响铃还有 2 小时 10 分钟"));

    // 3 days 5 hours 20 minutes delta
    int64_t t_3d = now + ((3LL * 24 * 60 + 5 * 60 + 20) * 60 * 1000);
    std::string s_3d = CountdownFormatter::FormatCountdownZh(t_3d, now);
    EXPECT_EQ(s_3d, std::string("距离响铃还有 3 天 5 小时 20 分钟"));

    // Immediate / already passed
    std::string s_past = CountdownFormatter::FormatCountdownZh(now - 1000, now);
    EXPECT_EQ(s_past, std::string("闹钟即将响铃"));

    return true;
}

bool TestCountdownFormattingEn() {
    int64_t now = 1000000000000LL;
    int64_t t_1h = now + (60LL * 60 * 1000);
    std::string s_1h = CountdownFormatter::FormatCountdownEn(t_1h, now);
    EXPECT_EQ(s_1h, std::string("Alarm will ring in 1 hour 0 minutes"));
    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestCountdownFormattingZh);
RUN_TEST(TestCountdownFormattingEn);
TEST_MAIN_END
