# Implementation Plan: Android Smart Alarm (智能闹钟)

**Branch**: `001-android-smart-alarm` | **Date**: 2026-09-13 | **Spec**: [specs/001-android-smart-alarm/spec.md](spec.md)

**Input**: Feature specification from `specs/001-android-smart-alarm/spec.md` with user constraints: Bazel build system, direct SQLite persistence, minimal APK size, maximum robustness, low CPU/RAM consumption, optimized for Samsung S25 Ultra and iQOO Z9 Turbo+.

---

### Summary

Deliver a lightweight, ultra-reliable, China-localized Android Alarm Clock application built with Bazel, architected with a decoupled cross-platform C++ engine ready for future iOS porting. The technical approach centers on zero-external-bloat architecture: direct SQLite3 for high-performance zero-dependency persistence, pure C++ abstract platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`) decoupling the business engine from OS details, Android JNI bridge linking to Java/Android UI, Android `AlarmManager.setAlarmClock()` and Full-Screen Intents for guaranteed Doze-penetrating wakeups, and automated end-to-end verification executed on the local Android emulator (`emulator-5554`).

---

## Technical Context

**Language/Version**: Modern C++ (C++17/20, Google C++ Style) for Core Domain and SQLite Persistence; Java 17 / Android SDK (API 30~35) for Android UI, Services, and JNI Bridge; Objective-C++ reserved for future iOS adapter.

**Primary Dependencies**: None (Direct System SQLite3 `-lsqlite3` and Android Framework API to ensure minimal APK footprint and zero bloat).

**Storage**: Direct SQLite3 engine with relational indexing, foreign keys, and ACID transactional safety.

**Build Tool**: Bazel (`/opt/homebrew/bin/bazel`, v9.x) with `--keep_going` parameter enforced.

**Testing**: 
- Hermetic Bazel C++ unit tests (`cc_test`) running across all domain components and persistence repositories.
- Automated ADB-driven E2E test harness (`scripts/verify_emulator.sh`) running on `emulator-5554` (Android 14 / API 34).

**Target Platform**: Android 11+ (API Level 30 through 35), tested on `emulator-5554`, optimized for Samsung Galaxy S25 Ultra (One UI) and iQOO Z9 Turbo+ (OriginOS), with decoupled interfaces for Apple iOS.

**Project Type**: Cross-platform C++ core with Android Native Application layer.

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
- Complete platform independence of core engine logic (zero `<android/...>` dependencies in `core/` and `data/`).

**Scale/Scope**: Multi-module Bazel project (`core`, `data`, `app`, `tests`, `scripts`).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitutional Principle | Evaluation Status | Implementation Strategy |
| :--- | :---: | :--- |
| **I. Minimal Change & YAGNI** | **PASS** | Avoided heavy ORMs and multi-MB media frameworks. Utilized built-in SQLite3 and native audio routing. |
| **II. User Experience Priority** | **PASS** | 100% on-time alarm guarantee, crescendo volume ramp, advance skip notifications, single-tap quick nap, and vacation management. |
| **III. Robustness & Stability** | **PASS** | Multi-level audio fallback, offline holiday caching, Direct Boot awareness, and automated emulator verification. |
| **IV. Performance & Efficiency** | **PASS** | Zero persistent idle services or background polling; CPU stays asleep until kernel RTC alarm triggers. |
| **V. Design Patterns & Decoupling** | **PASS** | Pure C++ abstract platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`) enable iOS extension without changing domain code. |
| **VI. Test-First & Comprehensive Testing** | **PASS** | Automated Bazel unit tests (100% green) plus ADB-driven end-to-end verification on `emulator-5554`. |
| **VII. Google C++ Style & Documentation** | **PASS** | All C++ code strictly adheres to Google Style Guide, `#pragma once`, English comments, and `.clang-format`. |
| **Tooling & Build Enforcement** | **PASS** | Bazel builds and tests executed via `/opt/homebrew/bin/bazel` with mandatory `--keep_going` parameter. |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-android-smart-alarm/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: architectural decisions, iOS decoupling & emulator benchmarks
├── data-model.md        # Phase 1 output: SQLite schema, DDL, and state machine
├── quickstart.md        # Phase 1 output: build, test, and emulator validation guide
├── contracts/           # Phase 1 output: component interfaces
│   ├── alarm_scheduler_contract.md
│   ├── holiday_engine_contract.md
│   ├── audio_haptic_contract.md
│   ├── challenge_engine_contract.md
│   └── platform_adapter_contract.md  # Pure C++ cross-platform abstraction for iOS
└── checklists/
    └── requirements.md  # Quality validation checklist
```

### Source Code & Test Structure (repository root)

```text
MODULE.bazel
BUILD.bazel
.clang-format

core/
├── BUILD.bazel
├── include/edom/alarm/core/
│   ├── adapter/
│   │   ├── i_platform_scheduler.h    # Abstract scheduler interface (Android/iOS)
│   │   ├── i_platform_audio.h        # Abstract audio/crescendo interface
│   │   ├── i_platform_haptics.h      # Abstract linear haptics interface
│   │   └── i_platform_sensor.h       # Abstract sensor/gesture interface
│   ├── scheduler/
│   ├── holiday/
│   ├── audio/
│   └── challenge/
└── src/edom/alarm/core/

data/
├── BUILD.bazel
├── include/edom/alarm/data/
└── src/edom/alarm/data/

app/
├── BUILD.bazel
├── AndroidManifest.xml
├── src/jni/alarm_jni_bridge.cc       # Android JNI adapter linking C++ engine
└── src/com/edom/alarm/ui/            # Android UI & Services

tests/
├── BUILD.bazel                       # Hermetic C++ unit test suites
└── ...

scripts/
└── verify_emulator.sh                # Automated ADB E2E verification on emulator-5554
```

---

## Complexity Tracking

> **Constitution Check: All gates passed cleanly. No architectural violations or unwarranted complexities introduced.**

| Component | Selected Pattern | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| **Persistence** | Direct SQLite3 + Repository Pattern | Key-value stores cannot perform relational queries; heavy ORMs violate minimal APK size constraint. |
| **Cross-Platform Boundary** | Pure C++ Abstract Platform Interfaces | C-style ABI lacks polymorphism; Flutter/React Native adds 20MB+ bloat and latency. |
| **Wakeup** | `AlarmManager.setAlarmClock()` + Full-Screen Intent | Standard `setExact()` throttled by Doze; `WorkManager` lacks exact-second guarantee. |
| **Challenges** | Strategy Pattern (`IChallengeEngine`) | Hardcoding challenges into UI creates tight coupling and impedes modular testability. |
| **Verification** | Automated ADB E2E Script on `emulator-5554` | Manual UI clicking is non-reproducible and error-prone; unit tests alone miss real Android runtime integration. |
