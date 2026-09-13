---

description: "Dependency-ordered tasks for the application-exit alarm reliability increment"
---

# Tasks: Android Smart Alarm — Application-Exit Reliability Increment

**Input**: Design documents from `specs/001-android-smart-alarm/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Tests**: Required by the specification and project constitution. Policy tests MUST be written and observed failing before their corresponding implementation tasks begin. Android lifecycle claims require real registered AlarmManager delivery; injected activities or broadcasts are diagnostic-only.

**Scope**: This task list implements only the current plan's FR-025 and FR-049–FR-051 increment, mapped to User Story 6 scenarios 1, 2, 8, and 9. Existing US1–US5 and US7 behavior is regression scope, not new implementation scope. FR-026 powered-off/pre-unlock RTC delivery remains deferred.

**Working-tree safety**: `MainActivity.java`, `RingingActivity.java`, `AlarmTriggerReceiver.java`, `AdvanceNotificationManager.java`, `activity_main.xml`, both string tables, and both verification scripts already contain user changes. Every task touching them MUST patch the current content incrementally and MUST NOT replace or revert unrelated work.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: May run in parallel after its stated prerequisites because it owns different files.
- **[US6]**: Maps to the scoped User Story 6 reliability increment.
- Every checklist item names its exact production, test, script, or artifact path.

---

## Phase 1: Setup — Java Policy-Test Harness

**Purpose**: Establish a zero-new-dependency test path without changing the existing C++ Bazel targets or packaging test sources into the APK.

- [X] T001 Create `scripts/test_java.sh` to compile and run only the framework-free delivery policy sources plus `tests/android/**/*.java` with the local JDK 17 and Android 34 compile jar, fail on any compilation/test error, and keep `tests/android/` outside the APK source scan in `scripts/build_apk.sh`
- [X] T002 [P] Create reusable fake clock, fake alarm store/registration gateway, deterministic assertions, and a nonzero-exit test launcher in `tests/android/com/edom/alarm/core/scheduler/AlarmDeliveryTestSupport.java`

**Checkpoint**: The empty/skeleton policy suite can be invoked independently without altering `tests/BUILD.bazel` C++ targets or APK contents.

---

## Phase 2: Foundational — Delivery Contracts and Value Types

**Purpose**: Define the dependency-inversion seams and immutable values that all reliability work consumes.

**⚠️ CRITICAL**: Complete this phase before writing the US6 behavioral tests.

- [X] T003 First document one independent durable `MissedAlarmOutcome` queue—its identity, alarm/occurrence references, missed-state enum, creation time, pending/acknowledged lifecycle, persist-before-Quick-Nap-delete rule, and explicit exclusion from `ReconcileReport`—in `specs/001-android-smart-alarm/data-model.md` and `specs/001-android-smart-alarm/contracts/alarm_delivery_contract.md`; then create matching immutable nested types in `core/src/com/edom/alarm/core/scheduler/AlarmDeliveryModels.java`, preserving `next_trigger_at_ms`/`occurrence_generation` Long defaults of `0`, empty `occurrence_id`/`last_claimed_occurrence_id`, `alarmId:generation:triggerAtMs` identity, all four missed-state values, capability booleans, all three registration modes, and the unchanged completed/entries/failed-ID report shape
- [X] T004 [P] Define the narrow persistence and atomic-claim interface in `core/src/com/edom/alarm/core/scheduler/AlarmScheduleStore.java`, explicitly mapping the current production JSON keys `id`, `hour`, `minute`, `isEnabled`, `repeatMode`, `daysBitmask`, `label`, `isQuickNap`, `ringtoneUri`, `ringtoneTitle`, `vibrateEnabled`, and `skippedDates` separately from snake_case domain names; require lossless unknown-key round-trip, validation of hour/minute and repeatMode `0`/`1`/`2`, one synchronized compare-and-set claim, independent durable missed-outcome list/ack operations, persist-outcome-before-Quick-Nap-delete ordering, and no persisted permission/app-op truth
- [X] T005 [P] Define a core-only `NextOccurrenceCalculator` plus injected holiday-calendar adapter in `core/src/com/edom/alarm/core/scheduler/NextOccurrenceCalculator.java`, taking the complete stored alarm definition, current Instant, ZoneId, skip dates, and statutory holiday/workday answers so APP_LAUNCH, BOOT, TIME_CHANGED, and TIMEZONE_CHANGED never copy scheduling logic or depend on `ui/HolidaySyncManager`
- [X] T006 [P] Define the schedule/cancel abstraction consumed by reconciliation in `core/src/com/edom/alarm/core/scheduler/AlarmRegistrationGateway.java`, returning one explicit registration result per alarm and exposing no Android PendingIntent construction to callers

**Checkpoint**: Store, scheduler, reconciler, receiver, service, and UI can depend on narrow contracts rather than each other’s implementation details.

---

## Phase 3: User Story 6 — Reliable Delivery After App Exit (Priority: P3) 🎯 Increment MVP

**Goal**: A real OS alarm survives ordinary backgrounding, process reclamation, Doze, and Recent Tasks removal; force-stop and externally revoked exact access recover only after explicit relaunch; missing capabilities or failed registrations remain visibly limited.

**Independent Test**: Register a near-future alarm and retain `dumpsys alarm` evidence; without component injection, verify autonomous Receiver → ringing-service delivery within SC-001 timing after Home/lock, `am kill`, real Recents removal, and verified device idle. Separately force-stop or revoke exact access, verify no unsupported stopped-state claim, explicitly relaunch, and confirm every future enabled occurrence is re-registered or its alarm ID remains reported as limited.

### Tests for User Story 6 — Write and Observe Failing First

- [X] T007 [P] [US6] Write failing legacy-upgrade, `alarmId:generation:triggerAtMs` generation, stale payload, duplicate claim, and expired one-time/Quick-Nap/recurring policy cases in `tests/android/com/edom/alarm/core/scheduler/AlarmOccurrencePolicyTest.java`
- [X] T008 [P] [US6] Write failing next-occurrence cases for Custom Days weekday masks, statutory holiday/rest-day and make-up-workday answers from a fake adapter, skip dates, Quick Nap expiry, ZoneId changes, DST gaps, and DST overlaps in `tests/android/com/edom/alarm/core/scheduler/NextOccurrenceCalculatorTest.java`
- [X] T009 [P] [US6] Write failing exact-versus-best-effort decision and canonical identity descriptor cases covering explicit `AlarmTriggerReceiver`, action `com.edom.alarm.ACTION_ALARM_TRIGGER`, deterministic request code, data URI `alarm://trigger/{alarm_id}`, and immutable/update flags in `tests/android/com/edom/alarm/core/scheduler/AlarmRegistrationPolicyTest.java`
- [X] T010 [P] [US6] Write failing idempotent reconciliation, authoritative next-occurrence recomputation, one-trigger/at-most-one-advance-registration, partial failure continuation, expired occurrence enqueue into the independent missed-outcome queue, unchanged report shape, and `failed_alarm_ids` cases using fakes in `tests/android/com/edom/alarm/core/scheduler/AlarmReconciliationPolicyTest.java`
- [X] T011 [P] [US6] Write failing protection derivation and presentation cases proving `FULL` requires all three capabilities, `completed == true`, no failed alarm IDs, one current successful entry per enabled alarm, and API-appropriate force-stop reliability guidance remains reachable even while protection is `FULL` in `tests/android/com/edom/alarm/core/scheduler/AlarmProtectionPolicyTest.java`
- [X] T012 [P] [US6] Write failing framework-free store-contract tests against an injected synchronized in-memory key-value backend, using a captured current-production JSON fixture to verify every camelCase key and unknown-key preservation, independent missed-outcome persist/list/ack ordering, and a barrier-synchronized two-thread claim race with exactly one winner without invoking Android framework methods in `tests/android/com/edom/alarm/core/scheduler/AlarmScheduleStoreContractTest.java`
- [X] T013 [P] [US6] Write failing framework-free tests for PendingIntent identity descriptors and injected service-handoff success/rejection/fallback decisions, explicitly avoiding execution of `android.jar` stubs, in `tests/android/com/edom/alarm/core/scheduler/AlarmDeliveryBoundaryPolicyTest.java`
- [ ] T014 [US6] Add device-runtime checks for the real SharedPreferences adapter/current JSON fixture, PendingIntent create/query/cancel identity, manifest/component declarations, capability probes, Receiver-to-service handoff, and real registered-alarm acceptance cases for background, `am kill`, Recents removal, verified Doze, stale/duplicate/expired delivery, missed-outcome UI acknowledgement, force-stop/relaunch, capability denial/revocation, banner state, and cleanup to `scripts/verify_emulator.sh`; run the pre-implementation baseline and record each expected failure or unavailable API row without converting component injection into a pass under `build/verification/alarm-delivery/baseline-emulator/`
- [ ] T015 [US6] Define caller-supplied-serial Samsung One UI and iQOO/Vivo OriginOS real-alarm matrices for Home, process reclamation, Recents removal, force-stop/relaunch, OEM guidance, and capability degradation in `scripts/verify_device.sh`; record available pre-implementation failures and mark absent hardware unverified under `build/verification/alarm-delivery/baseline-device/`

### Pure Policy and Persistence Implementation

- [ ] T016 [US6] Implement deterministic occurrence creation/validation, exact-or-best-effort selection, expired-occurrence transitions, reconciliation aggregation, and protection derivation to satisfy T007, T009–T011 in `core/src/com/edom/alarm/core/scheduler/AlarmDeliveryPolicy.java`
- [ ] T017 [US6] Implement the sole next-occurrence authority to satisfy T008 in `core/src/com/edom/alarm/core/scheduler/DefaultNextOccurrenceCalculator.java`, adapting existing holiday data behind T005 without a core-to-UI dependency and deterministically handling weekday masks, statutory work/rest days, skip dates, Quick Nap, ZoneId changes, and DST gaps/overlaps
- [ ] T018 [US6] Implement backward-compatible `smart_alarm_prefs` / `alarms_list` JSON loading to satisfy T012 and T014, preserving the exact T004 camelCase schema and unknown keys, performing legacy occurrence upgrade before registration, synchronously durably writing claims and the independent missed-outcome queue, allowing exactly one concurrent claimant, persisting a Quick Nap missed outcome before deleting its alarm record, and retaining that outcome until UI acknowledgement in `core/src/com/edom/alarm/core/scheduler/SharedPreferencesAlarmScheduleStore.java`
- [ ] T019 [P] [US6] Implement API-aware exact-alarm, notification/channel, and full-screen capability evaluation plus the highest-impact settings Intent in `core/src/com/edom/alarm/core/scheduler/AlarmCapabilityEvaluator.java`, using API 30 exact availability, API 31+ exact access, API 33+ notification permission, and API 34+ full-screen app-op checks
- [ ] T020 [US6] Implement the sole AlarmManager/PendingIntent factory in `core/src/com/edom/alarm/core/scheduler/AlarmSystemScheduler.java`: persist-before-register input, canonical explicit immutable broadcast identity for schedule/query/cancel, `setAlarmClock()` when exact is available, `setAndAllowWhileIdle()` otherwise, separate stable advance-notification identity, and `NOT_REGISTERED` on invalid/failing framework calls
- [ ] T021 [US6] Implement injected, idempotent APP_LAUNCH/APP_RESUME/BOOT_COMPLETED/TIME_CHANGED/TIMEZONE_CHANGED/PACKAGE_REPLACED/EXACT_CAPABILITY_GRANTED reconciliation in `core/src/com/edom/alarm/core/scheduler/AlarmScheduleReconciler.java`, using only T005 for next-occurrence computation, enqueueing missed outcomes through T004 before destructive alarm transitions, continuing after per-alarm errors, and returning the unchanged completed/entries/failed-ID `ReconcileReport`

### Ringing Execution and Android Lifecycle Integration

- [ ] T022 [P] [US6] Implement bounded wake-lock, immediate `mediaPlayback` foreground notification, alarm-channel audio/haptics, safety timeout, and stop/snooze/dismiss command ownership in `core/src/com/edom/alarm/core/scheduler/AlarmRingingService.java`, with no standing service or wake lock outside an active occurrence
- [ ] T023 [P] [US6] Update `app/AndroidManifest.xml` to remove `USE_EXACT_ALARM`, `LOCKED_BOOT_COMPLETED`, and all `directBootAware` claims; retain `SCHEDULE_EXACT_ALARM`; add `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, the `mediaPlayback` ringing service, `MY_PACKAGE_REPLACED`, and exact-capability-granted recovery declarations without claiming FR-026 pre-unlock delivery
- [ ] T024 [US6] Wire one application-context store, holiday adapter, next-occurrence calculator, capability evaluator, scheduler gateway, reconciler, ringing dependencies, and injectable service-handoff seam without Activity lifetime ownership in `app/src/com/edom/alarm/AlarmApplication.java`
- [ ] T025 [US6] Refactor `core/src/com/edom/alarm/core/scheduler/AlarmTriggerReceiver.java` into the canonical scheduled entry: atomically reject missing/disabled/skipped/stale/already-claimed payloads, persist the claim before handoff, start `AlarmRingingService` for valid exact delivery, catch the deterministically testable foreground-service rejection seam, use only permitted notification/full-screen fallback, and never directly call `startActivity()`
- [ ] T026 [US6] Replace the logging stub in `core/src/com/edom/alarm/core/scheduler/BootCompletedReceiver.java` with reconciler dispatch for boot-after-unlock, time/timezone change, package replacement, and exact-capability grant, while never starting media without a validated due occurrence
- [ ] T027 [US6] Restrict `core/src/com/edom/alarm/core/scheduler/AdvanceNotificationManager.java` to advance-notification presentation/actions and route skip, cancel, and next-occurrence changes through `AlarmSystemScheduler` and `AlarmScheduleReconciler` so each alarm retains at most one advance registration

### Protection UI and Activity Composition

- [ ] T028 [US6] Implement capability-plus-reconciliation presentation in `app/src/com/edom/alarm/ui/AlarmProtectionPresenter.java`, hiding the warning banner only when no alarms are enabled or all capabilities/current registrations satisfy `FULL`, loading pending missed outcomes independently from `ReconcileReport` and acknowledging them only after presentation, and keeping API-appropriate force-stop reliability guidance continuously reachable from the protection surface even when `FULL`
- [ ] T029 [P] [US6] Add a persistent, accessible protection-warning container, corrective-action control, and always-reachable protection-help affordance without disturbing the existing header, nap, list, empty, or add-alarm views in `app/res/layout/activity_main.xml`
- [ ] T030 [P] [US6] Add complete English and Simplified Chinese strings for limited exact/notification/full-screen protection, failed registration IDs/retry, API-appropriate force-stop guidance, missed one-time/Quick-Nap/recurring outcomes, and best-effort wording in `app/res/values/strings.xml` and `app/res/values-zh-rCN/strings.xml`
- [ ] T031 [US6] Refactor `app/src/com/edom/alarm/ui/MainActivity.java` incrementally to remove private JSON/AlarmManager/PendingIntent ownership, load/save through `AlarmScheduleStore`, route create/edit/toggle/nap/skip/delete through the shared scheduler/reconciler, reconcile before protection reporting on create/resume, present then acknowledge durable missed outcomes before Quick Nap removal, keep force-stop guidance reachable, and preserve all existing US1–US5/US7 UI behavior
- [ ] T032 [US6] Refactor `app/src/com/edom/alarm/ui/RingingActivity.java` incrementally into lockscreen presentation and user-command dispatch only, removing its duplicate JSON claim/skip mutation and sole MediaPlayer/Vibrator/WakeLock ownership while preserving dismiss, snooze, hardware-key, challenge, and visible ringing behavior

**Checkpoint**: The scoped US6 increment is independently functional and testable; ordinary exit states deliver autonomously, unsupported stopped states are accurately communicated, and degraded/failed registration never appears fully protected.

---

## Phase 4: Polish and Cross-Cutting Quality Gates

**Purpose**: Prove the increment without upgrading diagnostics or unavailable-device rows into successful acceptance evidence.

- [ ] T033 Run `scripts/test_java.sh`, including T012's framework-free concurrent double-claim and T013's deterministic service-rejection seam, prove no test executes Android framework stubs, and retain its command, exit code, and per-suite case counts under `build/verification/alarm-delivery/java-policy-tests.txt`
- [ ] T034 Run `/opt/homebrew/bin/bazel test --keep_going //...`, `/opt/homebrew/bin/bazel build --keep_going //...`, and `scripts/build_apk.sh`; retain exit codes and verify the debug-signed `bazel-bin/app/alarm_debug_apk.apk` exists under `build/verification/alarm-delivery/build-and-tests.txt`; release-named output requires caller-supplied release credentials
- [ ] T035 Execute the pre-authored `scripts/verify_emulator.sh emulator-5554` plus available representative API 30/31/33/34/35+ runtimes; record registration, idle state, trigger/receiver/service timestamps, latency, notification/window, missed-outcome presentation, force-stop guidance, fallback behavior, cleanup, and every matrix result under `build/verification/alarm-delivery/emulator-matrix/`, marking naturally unproducible FGS rejection or unavailable runtimes unverified rather than passed
- [ ] T036 Execute the pre-authored `scripts/verify_device.sh` on iQOO Z9 Turbo+ serial `10CEAF0KDX000CK` and on a connected Samsung Galaxy S25 Ultra serial supplied at execution time; retain separate OEM evidence under `build/verification/alarm-delivery/device-matrix/` and leave absent devices explicitly unverified
- [ ] T037 Audit `app/src/com/edom/alarm/ui/MainActivity.java`, `app/src/com/edom/alarm/ui/RingingActivity.java`, `core/src/com/edom/alarm/core/scheduler/`, and `app/AndroidManifest.xml` for competing Activity alarm PendingIntents, unmatched cancel identities, divergent next-occurrence algorithms, idle services/wake locks, direct-boot claims, unbounded permissions, swallowed registration failures, unreachable force-stop guidance, dropped missed outcomes, and regressions; record findings plus cold-launch/idle-battery observations under `build/verification/alarm-delivery/final-audit.txt`

---

## Dependencies and Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies; T001 and T002 can proceed in parallel.
- **Phase 2 (Foundational)**: Starts after T001; T004–T006 can proceed in parallel after T003. This phase blocks US6 work.
- **Phase 3 (US6)**: T007–T015 start after Phase 2; every automated test and acceptance matrix must be written and its failing/unverified baseline recorded before T016–T032 implementation. Android integration starts only when its named collaborators exist.
- **Phase 4 (Quality Gates)**: Starts after T007–T032 are complete; T033 and T034 may run in parallel, then the pre-authored runtime matrices run, followed by the final audit.

### User Story Dependency Graph

```text
Setup (T001–T002)
  └── Foundational contracts (T003–T006)
        └── failing unit/integration/acceptance tests (T007–T015)
              ├── occurrence/calculator/store path (T016–T018)
              ├── capability/scheduler/reconciler path (T019–T021)
              ├── ringing service + manifest (T022–T023)
              └── UI resources (T029–T030)
                    └── lifecycle/component integration (T024–T032)
                          └── quality gates and pre-authored matrices (T033–T037)
```

### Within User Story 6

- T007–T013 depend on T001–T006 and are parallel failure-first tests in different files; T014 and T015 then establish serialized emulator/device acceptance baselines before production implementation.
- T016 satisfies the pure-policy expectations shared by T007 and T009–T011; T017 satisfies T008.
- T018 depends on T003, T004, T007, T012, T016, and T017.
- T019 depends on T003 and T013 and can run in parallel with T016–T018.
- T020 depends on T003, T006, T009, T013, T018, and T019.
- T021 depends on T003–T006, T010, and T016–T020.
- T022, T023, T029, and T030 own different files and can run in parallel after T007–T015 have established the failing baselines.
- T024 depends on T017–T023; T025 depends on T013, T018, T020–T024; T026 depends on T017, T021, and T024; T027 depends on T020, T021, T024, and T025.
- T028 depends on T003, T004, T011, T018, T019, and T021; T031 depends on T017–T021, T024, and T028–T030; T032 depends on T022, T025, and T030.
- T033–T037 depend on the complete production integration T016–T032; T035 and T036 execute rather than rewrite the T014/T015 scripts.

### Regression Boundaries

- US1–US5 and US7 are not dependencies for new implementation; T033–T034 retain automated build/test regressions, while T035–T037 retain runtime and final-audit regression evidence.
- FR-026 is neither a prerequisite nor an acceptance criterion for this increment.
- No task may migrate the alarm store to SQLite, introduce a third-party library, add persistent polling, or create an always-running service.

---

## Parallel Execution Examples

### Example A: Failure-First Policy Tests

```text
Worker A: T007 + T008 — occurrence and next-time tests
Worker B: T009 — registration decision/identity tests
Worker C: T010 — reconciliation tests
Worker D: T011 + T012 — protection and store-adapter tests
```

### Example B: Independent Android Surfaces After Policies Stabilize

```text
Worker A: T018 — SharedPreferences occurrence store
Worker B: T019 — capability evaluator
Worker C: T022 — bounded ringing service
Worker D: T029 + T030 — warning layout and localized strings
```

### Example C: Serialized High-Conflict Integration

```text
Single owner: T025 → T027 → T031 → T032
Reason: receiver, scheduling actions, MainActivity, and RingingActivity share occurrence lifecycle and must not be rewritten concurrently.
```

---

## Implementation Strategy

### Increment MVP

1. Complete Phase 1 and Phase 2.
2. Write T007–T015 and record their failing or explicitly unverified baselines.
3. Complete T016–T032 in dependency order.
4. Pass T033–T035 on the available emulator/API matrix.
5. Stop and report device rows as unverified if the named hardware is unavailable; do not substitute component injection.

### Incremental Delivery

1. **Executable contracts**: T003–T015 establishes authoritative occurrence inputs, failure-first tests, and acceptance baselines.
2. **Durable state**: T016–T018 implements occurrence identity, next-time calculation, claim, legacy upgrade, and durable missed outcomes.
3. **System delivery**: T019–T027 implements capability-aware registration, reconciliation, and service-owned ringing.
4. **Truthful UI and evidence**: T028–T037 exposes limitations/missed outcomes, removes duplicate Activity ownership, proves supported states, and documents platform boundaries.

### Ownership Guidance

- Keep one writer at a time for `MainActivity.java`, `RingingActivity.java`, `AlarmTriggerReceiver.java`, `AdvanceNotificationManager.java`, `scripts/verify_emulator.sh`, and `scripts/verify_device.sh`.
- Parallel workers may own distinct new policy/service files or the resource-only pair `activity_main.xml` plus both string tables.
- Before editing any dirty file, re-read its current content and preserve user changes; never restore an older snapshot from this task list.

---

## Notes

- `[P]` means different files and no dependency on another unfinished task in that wave.
- `[US6]` maps every story task to the scoped reliability scenarios in `spec.md`.
- Tests precede implementation and must cover nominal plus critical boundary behavior.
- Runtime evidence must identify API/device, exit state, capability state, registered operation, timestamps, visual surface, audio/haptic outcome, and cleanup.
- A successful APK build, injected component start, or one device row is not equivalent to the complete SC-001/SC-012/SC-013 matrix.
