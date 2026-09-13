# Quickstart & Validation Guide: Android Smart Alarm

**Feature**: `001-android-smart-alarm`
**Date**: 2026-09-13
**Build Tool**: Bazel (`/opt/homebrew/bin/bazel`)

---

## 1. Prerequisites & Environment

1. **Bazel**: `/opt/homebrew/bin/bazel` (Bazel 9.x).
2. **Android SDK**: API 34/35 platform and build-tools installed (`ANDROID_HOME=/Users/edom/code/android/android_sdk` or `/Users/edom/Library/Android/sdk`).
3. **JDK**: OpenJDK 17 or 21.
4. **Target Hardware**: Samsung Galaxy S25 Ultra (One UI) or iQOO Z9 Turbo+ (OriginOS), running Android 11+ (API 30+).

---

## 2. Bazel Build & Compilation Commands

In accordance with project constitution rules, all Bazel builds must include `--keep_going`:

```bash
# 1. Clean build all targets
/opt/homebrew/bin/bazel build --keep_going //...

# 2. Build production release APK (minimized with R8/ProGuard)
/opt/homebrew/bin/bazel build --keep_going //app:alarm_release_apk

# 3. Build debug APK for development testing
/opt/homebrew/bin/bazel build --keep_going //app:alarm_debug_apk
```

---

## 3. Running Automated Tests

```bash
# Run all unit test suites (Domain logic, SQLite persistence, Holiday Engine, Schedulers)
/opt/homebrew/bin/bazel test --keep_going //...

# Run specific domain test suites
/opt/homebrew/bin/bazel test --keep_going //core:holiday_engine_test
/opt/homebrew/bin/bazel test --keep_going //core:alarm_scheduler_test
/opt/homebrew/bin/bazel test --keep_going //data:sqlite_repository_test
```

---

## 4. Device Deployment & ADB Installation

```bash
# Install to connected device (Samsung S25 Ultra or iQOO Z9 Turbo+)
adb install -r bazel-bin/app/alarm_release_apk.apk

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
   - Select "Skip Dates / Vacation Mode" -> `VacationCalendarDialog` opens with a monthly grid.
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
- **Package Deployment**: Installs debug/release APK, verifies zero native crash or missing symbol errors.
- **Permission Grant**: Pre-grants `POST_NOTIFICATIONS` and verifies exact alarm privileges (`SCHEDULE_EXACT_ALARM`).
- **UI & Lifecycle Activation**: Launches `MainActivity`, simulates setting an alarm, tests quick-nap trigger.
- **SQLite Database Integrity**: Queries internal SQLite database to assert table schema, holiday records, and alarm configurations.
- **Background Alarm Firing**: Simulates trigger intent via `adb shell am broadcast` to verify `RingingActivity` launch and audio-haptic service activation.
- **Gesture/Challenge Completion**: Injects shake count broadcasts and verifies clean dismissal and next-alarm rescheduling.
