#include "core/src/challenge/math_challenge_strategy.h"

#include <cstdlib>
#include <sstream>

namespace edom::alarm::core {

MathChallengeStrategy::MathProblem MathChallengeStrategy::GenerateProblem(int difficulty,
                                                                          int seed) {
    if (seed != 0) {
        std::srand(seed);
    }
    MathProblem problem;
    if (difficulty == 1) {
        // Easy: a + b (20..99 + 10..49)
        int a = 20 + (std::rand() % 80);
        int b = 10 + (std::rand() % 40);
        problem.answer = a + b;
        std::ostringstream oss;
        oss << a << " + " << b << " = ?";
        problem.question = oss.str();
    } else if (difficulty == 2) {
        // Medium: a * b (6..15 * 6..12)
        int a = 6 + (std::rand() % 10);
        int b = 6 + (std::rand() % 7);
        problem.answer = a * b;
        std::ostringstream oss;
        oss << a << " x " << b << " = ?";
        problem.question = oss.str();
    } else {
        // Hard: (a * b) + c
        int a = 7 + (std::rand() % 8);
        int b = 4 + (std::rand() % 6);
        int c = 15 + (std::rand() % 35);
        problem.answer = (a * b) + c;
        std::ostringstream oss;
        oss << "(" << a << " x " << b << ") + " << c << " = ?";
        problem.question = oss.str();
    }
    return problem;
}

bool MathChallengeStrategy::Verify(const MathProblem& problem, int user_answer) {
    return problem.answer == user_answer;
}

}  // namespace edom::alarm::core
