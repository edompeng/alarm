# Implementation Plan: Android Smart Alarm (智能闹钟)

**Git Branch**: `master` | **Feature Context**: `001-android-smart-alarm` | **Date**: 2026-09-14 | **Spec**: [specs/001-android-smart-alarm/spec.md](spec.md)

**Input**: Feature specification from `specs/001-android-smart-alarm/spec.md` with user constraints: Bazel build system, direct SQLite persistence, minimal APK size, maximum robustness, low CPU/RAM consumption, optimized for Samsung S25 Ultra and iQOO Z9 Turbo+.

---

### Summary

Deliver a lightweight, China-localized Android alarm application with a single, testable delivery pipeline that remains functional after ordinary backgrounding, process death, Doze, and removal from Recent Tasks. A backward-compatible durable occurrence is scheduled through an explicit immutable broadcast `PendingIntent`; the receiver atomically validates/claims it, then hands off to a bounded foreground ringing owner that posts a high-priority `CATEGORY_ALARM` notification whose full-screen intent is used only when permitted. A capability evaluator plus per-alarm reconciliation report selects exact or best-effort delivery and prevents the UI from reporting full protection when any registration failed. Android force-stop and equivalent OEM deep-stop remain an explicit boundary: the package cannot self-recover until reopened, when expired occurrences follow an explicit missed-alarm policy and future ones are reconciled.

---

## Technical Context

**Language/Version**: C++ core/data targets built through `rules_cc` (the repository does not currently pin a `-std=` level); Java compiled by the local JDK 17 toolchain; Android min SDK 30, compile/target SDK 34. Future iOS adaptation remains outside this repair.

**Primary Dependencies**: `rules_cc` 0.2.17, system SQLite3 (`-lsqlite3`), and Android framework APIs only. No new third-party runtime dependency is introduced.

**Storage**: Native data module links system SQLite3; the current Android UI stores its alarm list as SharedPreferences JSON. This repair keeps that store but performs a backward-compatible JSON schema evolution for durable `next_trigger_at_ms`, generation/`occurrence_id`, `last_claimed_occurrence_id`, and missed state. A SQLite migration remains out of scope.

**Build Tool**: Local Bazel 9.1.0 for C++ libraries/tests with `--keep_going`; `//app:alarm_debug_apk` brings the framework-only Java APK assembly into the Bazel graph through `scripts/build_apk.sh` and Android build-tools 34.0.0 (AAPT2, `javac`, D8, zipalign, and apksigner). Release signing remains explicit and credential-supplied.

**Testing**:
- Ten Bazel C++ `cc_test` targets under `//tests` for domain, persistence, audio/haptics, challenges, formatting, and platform adapters.
- Framework-free Java policy tests (JDK 17, no new test dependency) for capability-to-scheduling decisions and reconciliation; Android PendingIntent/notification integration remains an on-device ADB assertion.
- ADB-driven runtime matrices in `scripts/verify_emulator.sh` and `scripts/verify_device.sh` that wait for the registered OS alarm; injected broadcasts/activities are diagnostic-only and cannot count as exit-state proof.

**Target Platform**: Android 11+ (runtime API 30 and newer); current compile/target SDK 34. Validation covers representative API 30, 31, 33, 34, and 35+ runtimes plus Samsung Galaxy S25 Ultra (One UI) and iQOO Z9 Turbo+ (OriginOS).

**Project Type**: C++ core/data libraries plus a framework-only Java Android application assembled by the repository build script.

**Performance Goals**:
- Cold launch to interactive alarm list: < 600ms on the named target devices.
- Background idle battery consumption attributed to the application: < 1.5% over 24 hours.
- Exact-capability alarm arrival: within 1 second of the designated minute.
- No persistent polling, standing wake lock, or always-running service while no alarm is ringing.

**Constraints**:
- Exact timing/full-screen guarantees apply only when the relevant capabilities are available and the package is not force-stopped.
- Missing exact-alarm, notification, or full-screen capability must not block activation; delivery becomes best-effort and the dashboard must remain visibly limited.
- Use `SCHEDULE_EXACT_ALARM` as the single API 31+ permission strategy for this build and remove the unbounded `USE_EXACT_ALARM` declaration; this makes the user-grant, denial, and revocation behavior testable without assuming Play-policy eligibility.
- Force-stop/deep-stop cannot be bypassed by a normal third-party application; the next explicit launch reconciles enabled schedules.
- DND and global mute behavior uses alarm audio attributes and remains subject to user/OEM policy.
- Credential-protected alarm storage is authoritative. Remove `LOCKED_BOOT_COMPLETED` handling/`directBootAware` claims for this repair and reconcile after `BOOT_COMPLETED`; pre-unlock delivery is not claimed without a device-protected occurrence store.
- No new third-party library; no persistent background polling; no SQLite migration in this repair.
- Portable core logic remains free of Android framework dependencies.

**Scale/Scope**: Targeted Android delivery/recovery change across `app`, Java scheduler code under `core/src`, Android resources/manifest, verification scripts, and tests. It covers FR-025 and FR-049–FR-051. FR-026 hardware power-off RTC wakeup remains a separate OEM-capability feature requiring device-protected storage and powered-off/pre-unlock validation; no such delivery is claimed here. Existing unrelated feature work remains untouched.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Constitutional Principle | Evaluation Status | Implementation Strategy |
| :--- | :---: | :--- |
| **I. Minimal Change & YAGNI** | **PASS** | Evolve the existing JSON only with required occurrence/claim fields; isolate delivery, reconciliation, and capability presentation. |
| **II. User Experience Priority** | **PASS** | Do not silently claim protection: alarms remain activatable, but limited capability is persistent and actionable. |
| **III. Robustness & Stability** | **PASS** | Separate logical alarm state from OS registrations, reconcile idempotently, and keep force-stop as an explicit unsupported runtime state. |
| **IV. Performance & Efficiency** | **PASS** | OS alarms remain idle until delivery; wake locks are bounded; ringing service exists only for an active alarm. |
| **V. Design Patterns & Decoupling** | **PASS** | Capability evaluator, scheduler gateway, reconciler, and presenter each own one responsibility and depend on narrow interfaces. |
| **VI. Test-First & Comprehensive Testing** | **PASS (design gate)** | The plan requires unit and real OS-timer tests before implementation is considered complete; no claim of current green status is made. |
| **VII. Google C++ Style & Documentation** | **PASS** | This repair does not require C++ changes; any later C++ edit must use `.clang-format`, Google naming, and English intent comments. |
| **Tooling & Build Enforcement** | **PASS** | Native targets and the Java/debug APK packaging target are verified by Bazel `--keep_going`; the packaging action reuses the dependency-free Android build-tools script. |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-android-smart-alarm/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output: platform behavior and delivery decisions
├── data-model.md        # Phase 1 output: persisted entities and runtime protection state
├── quickstart.md        # Phase 1 output: build, test, and emulator validation guide
├── contracts/           # Phase 1 output: component interfaces
│   ├── alarm_scheduler_contract.md
│   ├── alarm_delivery_contract.md   # Exit-state, capability, delivery, and reconciliation contract
│   ├── holiday_engine_contract.md
│   ├── audio_haptic_contract.md
│   ├── challenge_engine_contract.md
│   ├── platform_adapter_contract.md  # Pure C++ cross-platform abstraction for iOS
│   ├── alarm_ui_contract.md          # Standard TimePicker creation, card list UI & skip badge
│   ├── settings_contract.md          # Settings, locale switching, nap & holiday sync config
│   └── holiday_sync_contract.md      # Remote holiday sync JSON schema & endpoint contract
└── checklists/
    └── requirements.md  # Quality validation checklist
```

### Source Code & Test Structure (repository root)

```text
MODULE.bazel
app/
├── AndroidManifest.xml                    # Exact permission, receiver/service, credential-unlock boot policy
├── res/layout/activity_main.xml           # Persistent protection-status banner
├── res/values*/strings.xml                # Localized capability/risk messages
└── src/com/edom/alarm/ui/
    ├── MainActivity.java                  # Composition root; delegates scheduling/reconciliation
    ├── RingingActivity.java               # User controls only; not the sole audio owner
    └── AlarmProtectionPresenter.java      # Capability + reconciliation/registration status binding

core/src/com/edom/alarm/core/scheduler/
├── AlarmTriggerReceiver.java              # Canonical explicit PendingIntent target
├── BootCompletedReceiver.java             # Reconciliation trigger only
├── AlarmSystemScheduler.java              # Planned stable PI creation/cancel + exact/fallback selection
├── AlarmCapabilityEvaluator.java          # Planned exact/notification/full-screen snapshot
├── AlarmScheduleReconciler.java           # Planned idempotent restore of enabled alarms
└── AlarmRingingService.java               # Planned bounded foreground ringing owner

data/                                           # Existing C++ SQLite module; no SQLite migration in this repair
tests/
├── BUILD.bazel                             # Existing ten C++ tests
└── android/                                # Planned framework-free scheduling/reconciliation policy tests

scripts/
├── build_apk.sh                            # Current APK assembly path
├── test_java.sh                            # Planned JDK-only policy test runner
├── verify_emulator.sh                      # Real OS alarm/capability matrix
└── verify_device.sh                        # Samsung/iQOO exit-state verification
```

---

## Complexity Tracking

> **Post-design Constitution Check**: The design passes the functional, robustness, efficiency, decoupling, and testability gates. One pre-existing tooling gap remains scoped: APK assembly is not yet a Bazel Android target. This repair neither depends on nor expands that gap.

| Component | Selected Pattern | Simpler Alternative Rejected Because |
| :--- | :--- | :--- |
| **Alarm store boundary** | Backward-compatible JSON occurrence fields behind one store interface | A SQLite migration would increase risk; omitting durable occurrence identity would make stale/duplicate rejection impossible. |
| **Cross-Platform Boundary** | Pure C++ Abstract Platform Interfaces | C-style ABI lacks polymorphism; Flutter/React Native adds 20MB+ bloat and latency. |
| **Standard Alarm UI** | Native `TimePicker` + Card List Adapter | Custom wheel views add external bloat; text input is error-prone and poor touch ergonomics. |
| **Wakeup & Process Kill** | Explicit immutable broadcast PI → receiver → bounded ringing service + alarm notification/full-screen intent | Direct Activity PI couples audio to UI, bypasses the receiver fallback, and cannot model notification/full-screen capability cleanly. |
| **Capability degradation** | Evaluator + reconciliation report + derived `FULL`/`LIMITED` state + persistent actionable banner | Capability booleans alone could report full protection when registration failed. |
| **Schedule recovery** | Idempotent reconciler on explicit launch, boot/time/timezone/package-replace, and capability recovery | Duplicated reschedule loops in activities/receivers cause inconsistent PI identity and stale registrations. |
| **Force-stop boundary** | Document unsupported stopped state; reconcile only after explicit relaunch | `FLAG_INCLUDE_STOPPED_PACKAGES` cannot restore runtime state or bypass the user's force-stop action. |
| **Boot storage boundary** | Credential-protected store + `BOOT_COMPLETED`; no locked-boot claim | A `directBootAware` receiver cannot read the current credential-protected alarm JSON before first unlock. |
| **Power-off RTC (FR-026)** | Explicitly deferred to a separate OEM-capability increment | It requires a documented third-party OEM interface, device-protected occurrence storage, and powered-off/pre-unlock evidence beyond this exit-state repair. |
| **Exact alarm permission** | `SCHEDULE_EXACT_ALARM` on API 31+ for this build | `USE_EXACT_ALARM` has a different distribution eligibility model and would invalidate the denial/revocation acceptance matrix. |
| **OEM Whitelist Guide** | `OemPermissionHelper` detecting `vivo` / `iqoo` with direct Settings intents | User has no way of knowing OriginOS kills background apps without explicit user-facing guidance. |
| **Holiday Sync** | Async HTTP GET + JSON parser + local cache fallback | Hardcoded assets become obsolete when State Council shifts dates; heavy cloud sync libraries add MBs of bloat. |
| **Auto-Sync Cadence** | Launch check: fail -> retry next launch; success -> 7-day cooldown | Periodic background AlarmManager / WorkManager jobs get frozen by battery optimizers on Chinese OEM ROMs. |
| **Holiday Generator** | Standalone Python script `generate_holiday_config.py` in `scripts/` | Hardcoding dates in Java/C++ requires app rebuilds for new years; manual JSON writing is error-prone. |
| **Skip Inspection** | Clickable `Vacation (X skipped)` badge directly reopens calendar with "Clear Skips" | Sub-menus increase cognitive load; users cannot easily cancel existing vacation plans without clearing. |
| **Verification** | Real registered-alarm matrix on emulator and named devices, with diagnostic injection reported separately | The current force-stop-then-`am start`/broadcast flow proves component entry points, not autonomous OS delivery. |

---

## Post-Design Constitution Re-check

- **Minimal change**: PASS. The design retains SharedPreferences JSON with only a backward-compatible occurrence schema evolution and limits new responsibilities to Android delivery, reconciliation, capability evaluation, and presentation.
- **Functionality and stability**: PASS. Supported exit states, permission degradation, force-stop recovery, cancellation identity, and recurring continuity have explicit contracts and validation cases.
- **Performance**: PASS. No idle service or polling is introduced; the foreground ringing owner exists only for an active occurrence and all wake locks are bounded.
- **Decoupling**: PASS. Android framework state is isolated behind scheduler/capability collaborators; portable recurrence and holiday logic remain unchanged.
- **Testing**: PASS at the design gate. Unit seams and autonomous OS-delivery matrices are defined; implementation completion requires evidence rather than injected-component substitutes.
- **Style/tooling**: PASS for this Java/documentation-scoped design. Native targets still require Bazel `--keep_going`; the pre-existing non-Bazel APK packaging path remains a separately tracked tooling gap and is not presented as resolved.

No unresolved technical-context question remains in this plan or its Phase 0/1 artifacts.
