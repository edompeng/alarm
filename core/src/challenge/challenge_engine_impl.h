#pragma once

#include "core/src/challenge/i_challenge_engine.h"

namespace edom::alarm::core {

class ChallengeEngineImpl : public IChallengeEngine {
   public:
    ChallengeEngineImpl() = default;
    ~ChallengeEngineImpl() override = default;

    ChallengeState CreateChallenge(ChallengeType type, int difficulty) override;
    bool VerifyMathAnswer(ChallengeState* state, int user_answer) override;
    int RegisterShakeSample(ChallengeState* state, float ax, float ay, float az) override;

   private:
    float last_shake_magnitude_ = 9.8f;
};

}  // namespace edom::alarm::core
