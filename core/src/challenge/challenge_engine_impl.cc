#include "core/src/challenge/challenge_engine_impl.h"

#include <algorithm>

#include "core/src/challenge/math_challenge_strategy.h"
#include "core/src/challenge/shake_challenge_strategy.h"

namespace edom::alarm::core {

ChallengeState ChallengeEngineImpl::CreateChallenge(ChallengeType type, int difficulty) {
    ChallengeState state;
    state.type = type;
    state.difficulty = difficulty;

    if (type == ChallengeType::kMath) {
        auto prob = MathChallengeStrategy::GenerateProblem(difficulty);
        state.prompt_text = prob.question;
        state.expected_answer = prob.answer;
    } else if (type == ChallengeType::kShake) {
        state.target_shake_count = (difficulty > 0) ? difficulty : 30;
        state.current_shake_count = 0;
        state.prompt_text = "Shake phone firmly to dismiss!";
    } else {
        state.is_completed = true;
    }
    return state;
}

bool ChallengeEngineImpl::VerifyMathAnswer(ChallengeState* state, int user_answer) {
    if (!state || state->type != ChallengeType::kMath) {
        return false;
    }
    if (state->expected_answer == user_answer) {
        state->is_completed = true;
        return true;
    }
    return false;
}

int ChallengeEngineImpl::RegisterShakeSample(ChallengeState* state, float ax, float ay, float az) {
    if (!state || state->type != ChallengeType::kShake) {
        return 100;
    }
    if (ShakeChallengeStrategy::DetectShake(ax, ay, az, &last_shake_magnitude_)) {
        state->current_shake_count++;
        if (state->current_shake_count >= state->target_shake_count) {
            state->is_completed = true;
        }
    }
    int progress =
        static_cast<int>((state->current_shake_count * 100.0f) / state->target_shake_count);
    return std::clamp(progress, 0, 100);
}

}  // namespace edom::alarm::core
