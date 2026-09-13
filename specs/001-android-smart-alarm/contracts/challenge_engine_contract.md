# Interface Contract: Anti-Oversleep Challenge Engine (`IChallengeEngine`)

**Package**: `com.edom.alarm.core.challenge`
**Contract Type**: Strategy Pattern & Interaction Contract

---

## 1. Overview
The `IChallengeEngine` enforces dismiss-blocking tasks (arithmetic calculation or physical device shaking) during alarm ringing to ensure the user is completely conscious before the alarm is dismissed.

---

## 2. API Methods

```java
public interface IChallengeEngine {

    enum ChallengeType {
        NONE,
        MATH,
        SHAKE
    }

    /**
     * Initializes and generates a new challenge session.
     *
     * @param type ChallengeType (MATH or SHAKE)
     * @param difficulty Level 1 (Easy), 2 (Medium), 3 (Hard) or shake count
     * @return ChallengeSession containing initial prompt and verification state
     */
    ChallengeSession createSession(ChallengeType type, int difficulty);

    /**
     * Verifies user response for a Math challenge.
     *
     * @param session Current challenge session
     * @param userAnswer User inputted numeric answer
     * @return True if correct; False if incorrect
     */
    boolean verifyMathAnswer(ChallengeSession session, int userAnswer);

    /**
     * Feeds accelerometer delta samples to a Shake challenge session.
     *
     * @param session Current challenge session
     * @param deltaAcceleration Vector magnitude change: sqrt(dx^2 + dy^2 + dz^2)
     * @return Progress percentage (0 to 100). Reaching 100 indicates completion.
     */
    int registerShakeSample(ChallengeSession session, float deltaAcceleration);
}
```

---

## 3. Session State & Behavior

```java
public class ChallengeSession {
    public ChallengeType type;
    public int difficulty;
    public String mathQuestionText; // e.g. "37 + 58 = ?"
    public int expectedMathAnswer;
    public int targetShakeCount;
    public int currentShakeCount;
    public boolean isCompleted;
}
```
