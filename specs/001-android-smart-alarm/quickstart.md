# Quickstart & Validation Guide: Android Smart Alarm

**Feature**: `001-android-smart-alarm`
**Date**: 2026-09-14
**Build Tool**: Bazel (`/opt/homebrew/bin/bazel`)

Delivery-state definitions and PendingIntent invariants are authoritative in [contracts/alarm_delivery_contract.md](contracts/alarm_delivery_contract.md); runtime capability/state fields are defined in [data-model.md](data-model.md#7-runtime-alarm-delivery-model).

---

## 1. Prerequisites & Environment

1. **Bazel**: `/opt/homebrew/bin/bazel` (currently 9.1.0; the repository does not pin `.bazelversion`).
2. **Android SDK**: platform/build-tools 34 installed. `scripts/build_apk.sh` defaults to `ANDROID_HOME=/Users/edom/code/android/android_sdk`.
3. **JDK**: the current build environment uses OpenJDK 17.
4. **Target Hardware**: Samsung Galaxy S25 Ultra (One UI) or iQOO Z9 Turbo+ (OriginOS), running Android 11+ (API 30+).

---

## 2. Bazel Build & Compilation Commands

In accordance with project constitution rules, all Bazel builds must include `--keep_going`:

```bash
# 1. Clean build all targets
/opt/homebrew/bin/bazel build --keep_going //...

# 2. Assemble the debug APK through the Bazel graph
/opt/homebrew/bin/bazel build --keep_going //app:alarm_debug_apk

# Output
test -f bazel-bin/app/alarm_debug_apk.apk

# Optional release signing. Passwords are read from the named environment
# variables by apksigner and are never checked into the repository.
ALARM_RELEASE_KEYSTORE=/absolute/path/release.jks \
ALARM_RELEASE_KEY_ALIAS=release \
ALARM_RELEASE_KEYSTORE_PASSWORD='...' \
ALARM_RELEASE_KEY_PASSWORD='...' \
./scripts/build_apk.sh
```

---

## 3. Running Automated Tests

```bash
# Run all unit test suites (Domain logic, SQLite persistence, Holiday Engine, Schedulers)
/opt/homebrew/bin/bazel test --keep_going //...

# Run specific existing test targets
/opt/homebrew/bin/bazel test --keep_going //tests:holiday_engine_test
/opt/homebrew/bin/bazel test --keep_going //tests:alarm_scheduler_test
/opt/homebrew/bin/bazel test --keep_going //tests:alarm_database_test

# After implementation (the implementation tasks create this runner):
# run framework-free Java scheduling/reconciliation policy tests
./scripts/test_java.sh
```

---

## 4. Device Deployment & ADB Installation

```bash
# Install to connected device (Samsung S25 Ultra or iQOO Z9 Turbo+)
adb install -r bazel-bin/app/alarm_debug_apk.apk

# Launch main activity
adb shell am start -n com.edom.alarm/.ui.MainActivity
```

---

## 5. End-to-End Validation Scenarios

### Scenario A: Statutory Workday & Holiday Skip Verification
1. Open the app and create an alarm set to **"Statutory Workdays" (法定工作日)** for 08:00 AM.
2. Advance device date via ADB to an official statutory holiday (e.g. `2026-10-01` National Day):
   ```bash
   adb shell "date 100107592026.00"
   ```
3. Observe: Status bar shows no active alarm for today; next trigger is scheduled for the next valid working date.
4. Advance device date to a weekend compensatory workday (调休补班 Sunday, e.g. `2026-09-27`):
   ```bash
   adb shell "date 092707592026.00"
   ```
5. Observe: Alarm triggers precisely at 08:00 AM.

### Scenario B: Advance Skip & Multi-Day Vacation Skip
1. Set an alarm to trigger in 45 minutes.
2. Verify lockscreen and notification card appears: "Alarm rings in 45 minutes" with "Skip Today".
3. Tap "Skip Today": Notification dismisses, alarm does not ring, but tomorrow's recurrence remains intact.
4. Long-press the alarm in the list -> tap "Skip Multiple Days / Vacation Mode".
5. In the calendar dialog, deselect 3 scheduled days and save. Confirm the secondary prompt.
6. Verify the 3 deselected days are skipped while all other scheduled days ring normally.

### Scenario C: Audio Crescendo, Loudspeaker Override & Sensor Actions
1. Connect Bluetooth headphones to the device. Enable "Always ring via phone speaker" and "Volume Crescendo (15s)".
2. Set an alarm for 1 minute in the future. Put the phone into silent/DND mode.
3. When alarm fires: Sound erupts directly from the **phone's built-in speaker**, smoothly ramping from silent to full volume over 15 seconds, and motor pulses in Heartbeat rhythm.
4. Lift the device: Volume attenuates immediately to ambient background level.
5. Place device face down: Alarm transitions instantly into Snooze mode.

### Scenario D: Standard Alarm Creation with TimePicker & List Management
1. Launch the app and tap the Add Alarm button (`+`).
2. In the creation dialog, adjust the native TimePicker to `07:15`, leave repeat set to "Ring Once" (default), enter label "Morning Standup", and tap Save.
3. Observe: Toast appears showing delta: "Alarm will ring in X hours, Y minutes".
4. Observe: Alarm card appears in the main list displaying `07:15`, label "Morning Standup", "Once", and the switch is ON.
5. Tap the switch to toggle OFF: Toast indicates alarm cancelled, switch updates to OFF.
6. Tap the switch to toggle ON: Alarm recalculates next trigger and reschedules.
7. Long-press the card and tap Delete: Alarm is permanently removed from the database and list.

### Scenario E: Long-Press Calendar Skip, Quick Nap In-List Lifecycle & System AlarmManager
1. **Quick Nap In-List Lifecycle**:
   - Tap the `15m` nap button on the dashboard.
   - Observe: A new alarm card labeled `Quick Nap (15m)` appears immediately in the list with active countdown and switch ON.
   - Toggle switch OFF or dismiss after ringing: The card automatically destroys itself from the list and storage.
2. **Long-Press Calendar Skip (Vacation Mode)**:
   - Long-press any recurring alarm card -> context menu appears with "Skip Dates / Vacation Mode", "Edit Alarm", and "Delete Alarm".
   - Select "Skip Dates / Vacation Mode" -> `VacationCalendarDialog` opens with a monthly grid (Sunday-first, 7-column header `Sun`-`Sat`).
   - Tap dates to toggle/cancel alarm reminders -> save -> observe card displays `"Vacation Mode (X days skipped)"`.
   - On skipped dates, ringing is completely suppressed and advances to next cycle.
3. **Per-Alarm Ringtone & Vibration**:
   - Open edit dialog for an alarm -> tap Ringtone to select system alarm sound via native `RingtoneManager` -> toggle Vibrate switch -> save.
   - When alarm triggers, it plays the selected ringtone and vibrates according to the per-alarm toggle.
4. **Real System AlarmManager Wakeup**:
   - Set an alarm for 1 minute in the future.
   - Lock screen or send app to background.
   - Observe: Android system status bar displays alarm clock icon.
   - When time arrives: CPU wakes via WakeLock, `STREAM_ALARM` audio plays with crescendo, and `RingingActivity` appears full-screen over the lockscreen.

### Scenario F: Settings Screen & Dynamic Language Switching
1. Tap the Settings icon (⚙️) in the main dashboard header.
2. Verify Settings screen displays Language, Advance Notification, and Quick Nap Presets.
3. Select "English" from the language options -> all labels immediately switch to English without restarting the app.
4. Select "简体中文" -> all labels switch to Simplified Chinese.

### Scenario G: Pre-Alarm Advance Notification & Quick Dismiss Action
1. Set an alarm to ring in 30 minutes.
2. In Settings, ensure Advance Notification is ON with 30-minute lead time.
3. Advance system time or trigger `ACTION_ADVANCE_NOTIFICATION` broadcast.
4. Observe: Status bar displays heads-up notification: "即将响铃: HH:mm" with "快捷关闭" button.
5. Tap "快捷关闭": Notification clears, upcoming alarm instance is skipped, Toast confirms dismissal, recurring schedule remains intact.

### Scenario H: Supported Exit-State Delivery Verification
Run each case from a fresh alarm scheduled at least 2 minutes in the future. Confirm the registered operation first with `adb shell dumpsys alarm`; then wait for the OS timer without sending an alarm broadcast or starting `RingingActivity` manually.

1. **Background**: press Home and lock the device. Expect autonomous audio/haptic delivery and the permitted visual alert.
2. **Process reclamation**: after backgrounding, use `adb shell am kill com.edom.alarm` (not `force-stop`). Expect the persisted PendingIntent to recreate the process and enter `AlarmTriggerReceiver`.
3. **Recent Tasks removal**: remove the task through the device Recents UI. Expect autonomous delivery; record this result separately for the emulator, Samsung One UI, and Vivo/iQOO OriginOS.
4. For every case, retain timestamped evidence from `dumpsys alarm`, receiver/service logs, notification state, foreground window, and observed audio/haptics. A direct `am broadcast` or `am start` result is diagnostic evidence only and does not satisfy this scenario.

### Scenario H1: Verified Doze Delivery
1. Schedule and confirm a real alarm at least 3 minutes in the future; save its persisted `trigger_at_ms` and matching `dumpsys alarm` entry.
2. Background the app, run `adb shell dumpsys battery unplug`, then `adb shell dumpsys deviceidle force-idle` (or the API-specific supported test equivalent).
3. Read back `adb shell dumpsys deviceidle` and retain evidence that the device is actually idle before the trigger. A locked screen alone does not establish Doze.
4. Send no component injection. Wait for the OS alarm, then calculate latency from the receiver timestamp minus persisted `trigger_at_ms`; require the SC-001 bound when exact/full capabilities are present.
5. Restore state with `adb shell dumpsys deviceidle unforce` and `adb shell dumpsys battery reset`, and retain before/after state in the case result.

### Scenario H2: Force-Stop Boundary & Explicit-Launch Recovery
1. Set and confirm a future registered alarm.
2. Confirm initial/reliability guidance explicitly says force-stop disables alarm delivery until the application is reopened.
3. Run `adb shell am force-stop com.edom.alarm` or use Settings > Apps > Smart Alarm > Force stop.
4. Confirm the package is stopped and the pending runtime alarm is absent/cannot autonomously deliver. The expected result is **no claim that the alarm will ring while force-stopped**.
5. Explicitly launch `MainActivity`.
6. Verify the application applies the expired-occurrence policy, reconciles every still-enabled alarm, recreates current future registrations, and reports full protection only when every registration succeeds. The current compile-SDK-34 design uses general force-stop guidance and unconditional launch reconciliation; it must not mislabel a Recents swipe as force-stop.
7. Schedule a new near-future alarm and verify autonomous delivery. Do not inject the receiver or activity as a substitute.

### Scenario I: Customized Quick Nap Slots (Minutes/Hours)
1. In Settings (or by long-pressing a dashboard nap button), change Slot 3 to value `1` and unit `Hours`.
2. Return to dashboard: Button 3 now reads `1h` (or `1小时`).
3. Tap the `1h` button: A nap countdown alarm is scheduled for 60 minutes from now.
4. Verify remaining duration badge displays "1h remaining".

### Scenario J: Statutory Holiday & Workday Remote Sync
1. In Settings, verify the "Holiday Sync URL / 假日同步地址" field shows the default URL.
2. Tap "Sync Now / 立即同步":
   - **Success**: Status indicator/Toast confirms "假日配置更新成功 (X天节假日, Y天补班)".
   - **Failure**: Set URL to an invalid endpoint or disconnect network, tap "Sync Now" -> Modal `AlertDialog` appears with title "更新失败" and error details. Existing holiday rules remain 100% active and untouched.
3. **Auto-Sync Cadence**:
   - Cold app launch: If never synced or previous sync failed -> attempts sync automatically in background.
   - If sync succeeded -> cooldown of 7 days before next auto-sync attempt.
   - Any auto-sync failure is completely silent (no popups).
4. **Holiday Config Generator**:
   - Run `python3 scripts/generate_holiday_config.py` in terminal.
   - Outputs valid JSON format matching `HolidaySyncModel` for current year and next year (if available).

### Scenario K: Skip Inspection & Single-Tap Clear Skips
1. When an alarm has active skipped dates, observe its card displays the clickable badge `Vacation (X skipped)` / `已跳过 X 天`.
2. Tap directly on the `Vacation (X skipped)` badge -> `VacationCalendarDialog` opens pre-populated with active skips.
3. Tap the "Clear Skips / 清除跳过" button (`btn_clear_calendar`) -> all skipped dates for this alarm are cleared immediately, dialog dismisses, and the card updates.

### Scenario L: OriginOS / Vivo Swiped-Away Alarm Wakeup (Physical Device: iQOO Z9 Turbo+)
1. Target device: iQOO Z9 Turbo+ (Vivo V2417A, Android 16 / API 36, serial `10CEAF0KDX000CK`).
2. In Settings, tap "OriginOS Background Settings / 允许后台高耗电" (`btn_oem_whitelist_guide`) to open OriginOS power/autostart management.
3. Create an alarm scheduled for 1 minute in the future.
4. Swipe the Smart Alarm task away using the Recents UI. Do **not** substitute `am force-stop`; these are distinct states.
5. Lock the screen and wait without issuing any ADB start/broadcast command.
6. Expected: the explicit broadcast PendingIntent enters `AlarmTriggerReceiver`, the bounded ringing owner starts audio/haptics, and the alarm notification presents a full-screen or heads-up surface according to current capability.
7. Tap Dismiss and verify the next recurring occurrence is registered through the same scheduler gateway.
8. Run Scenario H2 separately for OriginOS deep-stop/force-stop, expecting explicit-launch reconciliation rather than autonomous ringing.

### Scenario M: Permission Degradation & Persistent Protection Warning
Run on each applicable API level and restore the original app-op/permission state after the test.

1. Create and enable an alarm with all capabilities available. Verify the dashboard has no limited-protection banner and `setAlarmClock()` is registered.
2. Test **initial denial** while the app is active: deny exact-alarm capability through Alarms & reminders, then enable a new alarm. Verify immediate best-effort registration.
3. Separately test **external revocation** of an already registered exact alarm. Confirm Android removes/stops the exact-alarm state as applicable, then explicitly resume/relaunch the app. Do not require autonomous conversion while the package remains stopped.
4. After either active denial or the explicit return following revocation, verify the alarm remains enabled, the dashboard continuously shows `Alarm protection limited / 闹钟保护受限`, its action opens the relevant setting, and the scheduler uses the documented best-effort path without crashing. If background foreground-service start is denied for that inexact occurrence, verify the permitted notification/full-screen fallback is attempted and the result is not reported as full protection.
5. Restore exact-alarm capability. Verify enabled alarms are reconciled to `setAlarmClock()` and the warning reflects any other capability or registration failure still present.
6. On Android 13+, deny notification permission and verify the alarm still attempts its ringing execution path while notification visibility is reported as limited.
7. On Android 14+, deny full-screen intent access and verify audio/haptics still attempt to run, the alert falls back to the permitted notification surface, and the persistent warning remains.
8. Restore all capabilities and verify the warning disappears automatically only after re-evaluation and a completed reconciliation with successful current registration for every enabled alarm.
9. Force one AlarmManager registration failure and verify the banner remains visible with the failed alarm ID/retry action even though all three capability booleans are true.

---

## 6. Automated Android Emulator Verification (`emulator-5554`)

To execute full end-to-end automated verification against the local Android emulator:

```bash
# 1. Ensure emulator-5554 is attached and ready
/opt/homebrew/bin/adb -s emulator-5554 wait-for-device

# 2. Run the automated E2E emulator test suite
./scripts/verify_emulator.sh emulator-5554
```

### Automated Verification Pipeline Coverage:
- **Package Deployment**: Installs the debug APK (or an explicitly credential-signed release APK) and verifies zero native crash or missing symbol errors.
- **Capability Matrix**: Records exact-alarm, notification, and full-screen capability before each case; tests both available and unavailable states where the API level exposes them.
- **UI & Lifecycle Activation**: Launches `MainActivity`, simulates setting an alarm, tests quick-nap trigger.
- **Persistence Integrity**: Verifies the current persisted alarm representation and holiday records through the application-owned store; do not claim SQLite alarm persistence unless the runtime path actually uses it.
- **Autonomous Alarm Delivery**: Registers a real near-future OS alarm and waits after backgrounding/process kill/Recent Tasks removal; injected receiver or Activity calls are reported separately as diagnostics.
- **Force-Stop Recovery**: Verifies no unsupported autonomous-delivery claim while stopped, then explicitly launches the app and confirms enabled alarms are reconciled.
- **Cancellation & Recurrence**: Confirms schedule/cancel PendingIntent identity matches and recurring completion leaves exactly one next registration.
- **Gesture/Challenge Completion**: Injects shake count broadcasts and verifies clean dismissal and next-alarm rescheduling.

---

## 7. Automated Physical Device Verification (`10CEAF0KDX000CK`)

To execute automated end-to-end verification against the attached physical iQOO Z9 Turbo+:

```bash
# 1. Check physical device readiness
/opt/homebrew/bin/adb -s 10CEAF0KDX000CK get-state

# 2. Execute physical device verification suite
./scripts/verify_device.sh 10CEAF0KDX000CK
```

### Physical Device Verification Scope:
- **App Installation & Launch**: Validates ABI compatibility (arm64-v8a) and startup on Android 16 (OriginOS 4).
- **OriginOS Whitelist Intent**: Verifies `OemPermissionHelper` resolves OEM background high-power management intent without crashing.
- **Remote Holiday Sync**: Verifies manual sync button triggers HTTP GET and validates JSON against `HolidaySyncModel`.
- **Supported Exit-State Wakeup**: Uses a real registered OS alarm after Home/background, `am kill`, and Recents swipe; no injected component call may satisfy the result.
- **Force-Stop Boundary**: Expects no autonomous ring while stopped, then verifies explicit-launch reconciliation and a subsequent real alarm.
- **Capability Warning**: Verifies limited-protection banner, settings action, best-effort registration, and automatic clearing after restoration.
- **Skip Inspection & Clearance**: Tests skip date pre-population and `btn_clear_calendar` clearing action.
