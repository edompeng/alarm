# Implementation Plan: Android Smart Alarm (智能闹钟)

**Branch**: `001-android-smart-alarm` | **Date**: 2026-09-13 | **Spec**: [specs/001-android-smart-alarm/spec.md](spec.md)

**Input**: Feature specification from `specs/001-android-smart-alarm/spec.md` with user constraints: Bazel build system, direct SQLite persistence, minimal APK size, maximum robustness, low CPU/RAM consumption, optimized for Samsung S25 Ultra and iQOO Z9 Turbo+.

---

## Summary

Deliver a lightweight, ultra-reliable, China-localized Android Alarm Clock application built with Bazel. The technical approach centers on zero-external-bloat architecture: direct Android SQLite (`SQLiteOpenHelper`) for high-performance zero-dependency persistence, Android `AlarmManager.setAlarmClock()` and Full-Screen Intents for guaranteed Doze-penetrating wakeups, modular sensor fusion for natural gestures (flip to mute, pick up to quiet), and clean Strategy/Repository patterns governing holiday calendars, anti-oversleep challenges, and audio-haptic routing.

---

## Technical Context

**Language/Version**: Java 17 / Android SDK (Compile SDK 34/35, Min SDK 30 - Android 11+).

**Primary Dependencies**: None (Pure Android Framework API to ensure minimal APK footprint and instant startup).

**Storage**: Embedded Android SQLite (`android.database.sqlite.SQLiteOpenHelper`) with direct indexing and transactional safety.

**Build Tool**: Bazel (`/opt/homebrew/bin/bazel`, v9.x) with `rules_android` and aggressive R8/ProGuard shrinking (`--keep_going` enforced).

**Testing**: JUnit 4 / Android Local Test (`android_local_test` and `java_test` in Bazel).

**Target Platform**: Android 11+ (API Level 30 through 35), tested and optimized for Samsung Galaxy S25 Ultra (One UI) and iQOO Z9 Turbo+ (OriginOS).

**Project Type**: Android Native Mobile Application.

**Performance Goals**:
- Cold launch time: < 400ms.
- Background idle memory (RAM): < 25MB.
- 24-hour idle battery consumption: < 1.0%.
- Ringing timing jitter: < 200ms.
- Release APK download size: < 3.5MB.

**Constraints**:
- 100% on-time wake-up reliability under extreme Doze battery-saver states.
- DND and global mute penetration via `AudioAttributes.USAGE_ALARM`.
- Offline resilience via cached holiday calendar database and local ringtone fallback.

**Scale/Scope**: Single application codebase with 4 core modules (`core`, `data`, `ui`, `app`) and comprehensive test suite (`tests`).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitutional Principle | Evaluation Status | Implementation Strategy |
| :--- | :---: | :--- |
| **I. Minimal Change & YAGNI** | **PASS** | Avoided heavy ORMs (Room, Realm) and multi-MB media frameworks (ExoPlayer). Utilized built-in Android SQLite and MediaPlayer. |
| **II. User Experience Priority** | **PASS** | 100% on-time alarm guarantee, crescendo volume ramp, advance skip notifications, single-tap quick nap, and intuitive calendar vacation management. |
| **III. Robustness & Stability** | **PASS** | Multi-level audio fallback, offline holiday caching, Direct Boot awareness (`directBootAware="true"`), and leak-proof sensor registration. |
| **IV. Performance & Efficiency** | **PASS** | Zero persistent idle services or background polling; CPU stays asleep until kernel RTC alarm triggers. R8 full-mode stripping keeps APK ultra-small. |
| **V. Design Patterns & Decoupling** | **PASS** | Strategy Pattern (`IChallengeEngine`), Repository Pattern (`IAlarmRepository`, `IHolidayRepository`), Factory Pattern (`HapticWaveformFactory`), Observer Pattern for alarms and sensors. |
| **VI. Test-First & Comprehensive Testing** | **PASS** | Independent unit test targets for holiday resolution, schedule calculation, multi-day skip logic, and database persistence. |
| **VII. Google C++ Style & Documentation** | **PASS** | If native components are compiled, strict adherence to Google C++ Style and English meaningful comments. |
| **Tooling & Build Enforcement** | **PASS** | Bazel builds and test runs executed via `/opt/homebrew/bin/bazel` with mandatory `--keep_going` parameter. |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-android-smart-alarm/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: architectural decisions & benchmarks
├── data-model.md        # Phase 1 output: SQLite schema, DDL, and state machine
├── quickstart.md        # Phase 1 output: build, test, and validation guide
├── contracts/           # Phase 1 output: component interfaces
│   ├── alarm_scheduler_contract.md
│   ├── holiday_engine_contract.md
│   ├── audio_haptic_contract.md
│   └── challenge_engine_contract.md
└── checklists/
    └── requirements.md  # Quality validation checklist
```

### Source Code (repository root)

```text
MODULE.bazel / WORKSPACE
BUILD.bazel

app/
├── BUILD.bazel
├── AndroidManifest.xml
├── proguard-rules.pro
├── res/
│   ├── drawable/               # Vector drawables (zero-PNG footprint)
│   ├── layout/                 # Clean XML layouts
│   ├── values/                 # Colors, strings, styles
│   └── raw/                    # Bundled default soundscapes (<50KB AAC)
└── src/com/edom/alarm/
    ├── AlarmApplication.java
    └── ui/
        ├── MainActivity.java
        ├── RingingActivity.java
        ├── QuickNapDialog.java
        └── VacationCalendarDialog.java

core/
├── BUILD.bazel
└── src/com/edom/alarm/core/
    ├── scheduler/
    │   ├── IAlarmScheduler.java
    │   ├── AlarmSchedulerImpl.java
    │   └── AlarmTriggerReceiver.java
    ├── holiday/
    │   ├── IHolidayEngine.java
    │   ├── HolidayEngineImpl.java
    │   └── HolidayCloudSyncService.java
    ├── audio/
    │   ├── IAudioHapticController.java
    │   ├── AudioHapticControllerImpl.java
    │   └── HapticWaveformFactory.java
    ├── challenge/
    │   ├── IChallengeEngine.java
    │   ├── ChallengeEngineImpl.java
    │   ├── MathChallengeStrategy.java
    │   └── ShakeChallengeStrategy.java
    └── sensor/
        └── AlarmSensorFusionListener.java

data/
├── BUILD.bazel
└── src/com/edom/alarm/data/
    ├── db/
    │   ├── AlarmDatabaseHelper.java
    │   └── DatabaseContract.java
    ├── repository/
    │   ├── IAlarmRepository.java
    │   ├── AlarmRepositoryImpl.java
    │   ├── IHolidayRepository.java
    │   └── HolidayRepositoryImpl.java
    └── model/
        ├── AlarmEntity.java
        ├── HolidayEntity.java
        └── SkipRuleEntity.java

tests/
├── BUILD.bazel
├── core/
│   ├── AlarmSchedulerTest.java
│   ├── HolidayEngineTest.java
│   └── ChallengeEngineTest.java
└── data/
    └── AlarmDatabaseTest.java
```

**Structure Decision**:
Divided into 4 explicit Bazel modules (`app`, `core`, `data`, `tests`):
- `data`: Self-contained SQLite persistence and models with zero UI or framework service dependencies.
- `core`: Pure domain logic, scheduler math, holiday evaluation, audio/haptic abstraction, and challenge strategies.
- `app`: Android manifest, vector UI resources, activities, and application bootstrap.
- `tests`: Automated unit tests executed hermetically by Bazel.

---

## Complexity Tracking

> **Constitution Check: All gates passed cleanly. No architectural violations or unwarranted complexities introduced.**

| Component | Selected Pattern | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| **Persistence** | Direct `SQLiteOpenHelper` + Repository Pattern | Key-value stores cannot perform relational queries (multi-day skips, holiday joins); heavy ORMs (Room) violate minimal APK size constraint. |
| **Wakeup** | `AlarmManager.setAlarmClock()` + Full-Screen Intent | Standard `setExact()` is throttled by OEM Doze modes; `WorkManager` lacks exact-second guarantees. |
| **Challenges** | Strategy Pattern (`IChallengeEngine`) | Hardcoding challenge logic into `RingingActivity` creates tight coupling and impedes modular testing. |
