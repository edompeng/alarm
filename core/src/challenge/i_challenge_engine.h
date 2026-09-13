#pragma once

#include <string>

namespace edom::alarm::core {

enum class ChallengeType { kNone, kMath, kShake };

struct ChallengeState {
    ChallengeType type = ChallengeType::kNone;
    int difficulty = 1;
    std::string prompt_text;
    int expected_answer = 0;
    int target_shake_count = 30;
    int current_shake_count = 0;
    bool is_completed = false;
};

class IChallengeEngine {
   public:
    virtual ~IChallengeEngine() = default;

    virtual ChallengeState CreateChallenge(ChallengeType type, int difficulty) = 0;
    virtual bool VerifyMathAnswer(ChallengeState* state, int user_answer) = 0;
    virtual int RegisterShakeSample(ChallengeState* state, float ax, float ay, float az) = 0;
};

}  // namespace edom::alarm::core
