#include "core/src/challenge/challenge_engine_impl.h"
#include "core/src/challenge/math_challenge_strategy.h"
#include "tests/test_framework.h"

using namespace edom::alarm::core;

bool TestMathChallengeGenerationAndVerification() {
    ChallengeEngineImpl engine;

    // Easy challenge (Difficulty 1)
    ChallengeState easy = engine.CreateChallenge(ChallengeType::kMath, 1);
    EXPECT_FALSE(easy.prompt_text.empty());
    EXPECT_FALSE(easy.is_completed);

    // Verify wrong answer fails
    EXPECT_FALSE(engine.VerifyMathAnswer(&easy, easy.expected_answer + 1));
    EXPECT_FALSE(easy.is_completed);

    // Verify right answer succeeds
    EXPECT_TRUE(engine.VerifyMathAnswer(&easy, easy.expected_answer));
    EXPECT_TRUE(easy.is_completed);

    // Medium challenge (Difficulty 2)
    ChallengeState med = engine.CreateChallenge(ChallengeType::kMath, 2);
    EXPECT_TRUE(engine.VerifyMathAnswer(&med, med.expected_answer));
    EXPECT_TRUE(med.is_completed);

    return true;
}

TEST_MAIN_BEGIN
RUN_TEST(TestMathChallengeGenerationAndVerification);
TEST_MAIN_END
