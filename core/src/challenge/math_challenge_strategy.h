#pragma once

#include <string>

namespace edom::alarm::core {

class MathChallengeStrategy {
   public:
    struct MathProblem {
        std::string question;
        int answer = 0;
    };

    static MathProblem GenerateProblem(int difficulty, int seed = 0);
    static bool Verify(const MathProblem& problem, int user_answer);
};

}  // namespace edom::alarm::core
