# Research & Architectural Decisions: Android Smart Alarm

**Feature**: `001-android-smart-alarm`
**Date**: 2026-09-14
**Status**: Completed

## 1. Build System Architecture: Bazel for Android

### Decision
Use **Bazel** (version 9.x / rules_android) with an optimized, lightweight multi-module structure:
- Core domain and logic modules compiled as standard libraries.
- Android application target `android_binary` utilizing aggressive **R8/ProGuard** code and resource shrinking.
- Comprehensive unit testing via `android_local_test` and `java_test`.

### Rationale
- Bazel provides hermetic, reproducible, ultra-fast incremental builds and multi-target orchestration.
- Enables strict decoupling between domain business rules, SQLite persistence, sensor engines, and Android UI.
- Direct control over DEX packaging, proguard optimization rules, and resource trimming to satisfy the "minimal APK size" mandate.

### Alternatives Considered
- **Gradle**: Standard Android build tool, but user explicitly commanded Bazel. Gradle also tends to introduce heavier default dependencies and longer cold builds.
- **CMake alone**: Suitable for native C++ code, but cannot package Android manifests, resources, and DEX bytecode into APKs standalone without Gradle or Bazel.

---

## 2. Persistence Architecture: Pure SQLite vs. Room / Heavy ORMs

### Decision
Adopt **Direct Android SQLite (`android.database.sqlite.SQLiteOpenHelper`)** with a clean Repository Pattern (`AlarmRepository`, `HolidayCalendarRepository`, `ConfigRepository`).

### Rationale
- **Zero Overhead**: Direct SQLite adds **0 KB** of third-party library bloat to the APK, compared to Room + KSP/Annotation Processors + Kotlin Coroutines + AndroidX SQLite, which can inflate APK size by several megabytes.
- **Extreme Performance & Low RAM**: Direct SQLite statements execute in bare C-level SQLite3 within the Android runtime. Zero reflection overhead, near-zero garbage collection pressure, and microsecond-level query latencies.
- **Rock-Solid Stability**: Full ACID compliance and transactional safety without mysterious ORM runtime behavior. Perfect for an alarm app where persistence failures mean missed alarms.

### Alternatives Considered
- **Room ORM**: Adds significant JAR/AAR dependencies and annotation processor footprint, violating the minimal APK size rule.
- **SharedPreferences / DataStore**: Inadequate for relational queries like multi-day date range matching, holiday calendar lookups, and compound bitmask queries.
- **Embedded C++ SQLite3 via JNI**: Unnecessary duplication since the Android framework already wraps the latest OS-level SQLite3 library with zero binary penalty.

---

## 3. High-Precision Wakeup & Background Resilience on Android 11+

### Decision
1. Persist the logical alarm before registering its OS operation.
2. For a user-configured wake alarm with exact-alarm capability, call `AlarmManager.setAlarmClock()` with an explicit immutable `PendingIntent.getBroadcast()` targeting the manifest-declared `AlarmTriggerReceiver`. Android documents `setAlarmClock()` as a user-visible exact alarm whose delivery time is not adjusted and which leaves low-power modes when necessary.
3. Keep the receiver short: load the alarm by stable ID, reject disabled/skipped/stale occurrences, claim the valid occurrence, and hand off to the bounded foreground ringing owner. That owner acquires only a bounded wake lock and posts its high-importance `CATEGORY_ALARM` foreground notification with a full-screen intent when permitted.
   - Because the application currently targets Android 14, declare `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `android:foregroundServiceType="mediaPlayback"`; this type has no runtime prerequisite and keeps the permission-denied best-effort path viable.
4. On Android 14+, evaluate `NotificationManager.canUseFullScreenIntent()` before representing the alert as full-screen capable. Without that capability, retain the heads-up/content-intent path and mark protection limited.
5. On Android 12+, evaluate exact-alarm capability before registration. If unavailable, honor the clarified best-effort requirement using `setAndAllowWhileIdle()` and surface limited protection; do not call an exact API and catch `SecurityException` as normal control flow.
6. Re-register persisted enabled alarms after boot, time/timezone change, package replacement, explicit application launch, and exact-alarm capability recovery. Boot handling only schedules alarms; it does not start ringing media.

### Rationale
- `PendingIntent` alarms survive ordinary process death; listener-based alarms do not provide that contract.
- A broadcast entry point separates OS delivery from UI visibility and allows audio/haptic execution to continue when full-screen UI is unavailable.
- `setAlarmClock()` is the correct Doze-resistant API for explicit alarm-clock semantics, while permission-denied delivery must be labeled best-effort.
- Reconciliation makes persisted logical state authoritative and repairs missing/stale OS registrations idempotently.

Official references: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms), [AlarmManager](https://developer.android.com/reference/android/app/AlarmManager), [time-sensitive notifications](https://developer.android.com/develop/ui/views/notifications/time-sensitive), [Android 14 full-screen intent changes](https://developer.android.com/about/versions/14/behavior-changes-14#full-screen-intent-notifications).

Foreground-service reference: [Android 14 foreground-service types](https://developer.android.com/about/versions/14/changes/fgs-types-required#media).

### Alternatives Considered
- `PendingIntent.getActivity()` as the alarm operation: rejected because it couples delivery to background activity-launch policy and bypasses the receiver-owned fallback path.
- `OnAlarmListener`: rejected because Android may cancel listener alarms after the owning process components are gone.
- `WorkManager`: rejected because it does not provide exact user-alarm timing.
- Persistent foreground service while idle: rejected because the OS alarm can wake the process only when needed and avoids continuous resource use.

---

## 4. Audio Routing, DND Penetration & Linear Motor Haptics

### Decision
1. **DND Penetration & Stream Priority**:
   - Configure `AudioAttributes.USAGE_ALARM` and `AudioAttributes.CONTENT_TYPE_SONIFICATION`.
   - Direct audio stream to `AudioManager.STREAM_ALARM`, ensuring full volume penetration through system global mute and Do Not Disturb.
2. **Smart Audio Routing (Speaker vs. Headphone)**:
   - When "Always ring via phone speaker" is active: Query `AudioManager.getDevices(GET_DEVICES_OUTPUTS)` and bind playback to `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER` via `AudioTrack` or `MediaPlayer.setPreferredDevice()`.
3. **Volume Crescendo**:
   - Implemented via a lightweight timer or `ValueAnimator` linearly stepping volume scalar from `0.0f` to `1.0f` over the user-selected interval (10 to 30s) at 200ms increments.
4. **Linear Motor Haptics**:
   - Use `Vibrator` / `VibratorManager` with `VibrationEffect.createWaveform()` for custom waveforms (Heartbeat, Wave, Staccato).
   - On Samsung and iQOO devices with advanced haptic actuators, query `Vibrator.areAllPrimitivesSupported()` to trigger crisp haptic primitives (`PRIMITIVE_CLICK`, `PRIMITIVE_TICK`, `PRIMITIVE_THUD`) synchronized with audio beats.

### Alternatives Considered
- External audio frameworks (e.g., ExoPlayer): ExoPlayer adds over 3MB of libraries and native codecs. Standard Android `MediaPlayer` and `AudioTrack` handle local MP3/AAC and streaming audio with zero added size.

---

## 5. China Statutory Holiday & Workday Engine

### Decision
- **Local Embedded Baseline**: Pre-pack a compact JSON dataset containing the current and following year's known calendar rules.
- **Cloud Silent Sync**: Lightweight HTTP client fetches updated holiday rules published annually by the State Council.
- **Persistence**: Store in a dedicated SQLite table `holiday_calendar` with indexed date keys (`YYYY-MM-DD`).
- **Classification Engine**:
  - `0`: Regular workday (Mon-Fri)
  - `1`: Regular weekend (Sat-Sun)
  - `2`: Statutory holiday (Paid public holiday, skip ringing)
  - `3`: Weekend compensatory workday (调休补班, must ring)

### Alternatives Considered
- Pure online API lookup on every alarm: Unacceptable due to network latency, offline failures, and battery drain.
- Manual user holiday input: Burdensome and prone to human error.

---

## 6. Interaction, Sensor Fusion & Anti-Oversleep Challenges

### Decision
1. **Sensor Management**:
   - Only register `Sensor.TYPE_ACCELEROMETER` and `Sensor.TYPE_PROXIMITY` listeners when the alarm is actively ringing. Unregister immediately upon dismiss/snooze to consume 0% idle battery.
   - **Flip to Mute**: Accelerometer detects $Z \approx -9.8 \text{ m/s}^2$ combined with proximity sensor reporting near distance.
   - **Pick Up to Lower Volume**: Dynamic acceleration delta $> 1.2 \text{ m/s}^2$ with proximity sensor clearing.
2. **Challenge Extensibility**:
   - Apply the **Strategy Pattern** (`ChallengeStrategy`):
     - `MathChallenge`: Generates random arithmetic questions with customizable difficulty (Easy: 2 digits addition; Medium: 2 digits multiplication; Hard: compound arithmetic).
     - `ShakeChallenge`: Tracks peak-to-peak acceleration reversals exceeding threshold until target count (e.g., 30) is reached.

---

## 7. APK Size & Performance Optimization Tactics

### Decision
1. **Aggressive Code Shrinking**:
   - Configure R8 in full mode (`android.enableR8.fullMode=true`).
   - Enable dead code elimination and class repackaging.
2. **Asset & Resource Optimization**:
   - All icons and illustrations strictly vector XML (`VectorDrawable`). No multi-density PNGs (mdpi, hdpi, xhdpi, xxhdpi, xxxhdpi).
   - Bundled ringtones compressed in high-efficiency AAC/Ogg Vorbis format (~50KB each).
3. **Memory & CPU Guardrails**:
   - Zero standing background services or persistent foreground notifications when no alarm is imminent.
   - No polling loops or wakelocks when idle.
   - All SQLite queries executed with parameterized statements and projection column filtering.

---

## 8. Cross-Platform Decoupled Architecture for iOS Extensibility

### Decision
Implement the core business engine, holiday evaluation rules, anti-oversleep challenge logic, and SQLite persistence in portable modern C++ (C++17/20, Google C++ Style Guide), isolated behind abstract C++ platform interfaces:
- `IPlatformScheduler`: Platform alarm scheduling (`AlarmManager` on Android, `UNUserNotificationCenter` / BGTask on iOS).
- `IPlatformAudio`: Audio stream control, crescendo volume ramping, and headphone routing (`AudioTrack`/`MediaPlayer` on Android, `AVAudioPlayer`/`AVAudioSession` on iOS).
- `IPlatformHaptics`: Linear motor vibration waveforms (`Vibrator` on Android, `UIImpactFeedbackGenerator`/`CHHapticEngine` on iOS).
- `IPlatformSensor`: Gesture and orientation sensor listeners (`SensorManager` on Android, `CoreMotion` on iOS).

### Rationale
- **100% Core Code Reuse**: Business logic, China statutory holiday algorithms, vacation date range calculations, and SQLite schema operations are completely identical across Android and iOS.
- **Zero Runtime Overhead**: C++ executes at bare metal speed with zero cross-runtime bridge penalties (e.g., no JavaScript bridges, no Dart VMs).
- **Direct Native Interoperability**: Android bridges via JNI (`alarm_jni_bridge.cc`); iOS interfaces directly using Objective-C++ (`.mm`), providing seamless native Swift/SwiftUI interoperability.
- **Minimal Footprint**: Links directly against system `sqlite3` and POSIX APIs, adding zero third-party dependencies.

### Alternatives Considered
- **Flat C ABI (`extern "C"`)**: Functional but lacks object-oriented polymorphism, requiring verbose manual function pointer tables.
- **Cross-Platform Frameworks (Flutter, React Native)**: Adds 15–30 MB to installation package size, increases cold-launch latency, and complicates low-level power-off alarm/sensor access.

---

## 9. Local Android Emulator E2E Verification Pipeline

### Decision
Establish an automated ADB-driven test harness (`scripts/verify_emulator.sh`) targeting active local Android emulators, with API 30, 31, 33, 34, and 35+ coverage where available:
1. **Automated Package Lifecycle**: Deploy the built APK, record exact-alarm/notification/full-screen capability state, and verify a clean explicit launch without assuming that every special access can be granted by `pm grant`.
2. **UI Journey Automation**: Simulates user interactions via `adb shell input` and `am start` (alarm creation, quick nap triggering, multi-day skip dialog navigation).
3. **OS Delivery Matrix**: Register a near-future alarm and wait for autonomous OS delivery after backgrounding, process death, Recent Tasks removal, Doze, boot/time reconciliation, and each permission state. Direct activity starts and injected broadcasts are retained only as component diagnostics.
4. **Database State Verification**: Directly queries the app's SQLite database on the emulator via `adb shell sqlite3` or content query to assert relational record accuracy.

### Rationale
- Separates framework component diagnostics from evidence that a registered system alarm actually survived an exit state.
- Ensures fast, automated, repeatable verification before the smaller physical-device matrix.
- Provides immediate feedback in CI/local development on whether alarms, skips, and holiday calculations persist and trigger properly on Android 11+ runtime targets.

### Alternatives Considered
- **Manual GUI Clicking Only**: Time-consuming, error-prone, and lacks automated regression protection.
- **Pure Unit Tests**: Tests isolated logic but misses Android framework integration, manifest permission gates, and real SQLite file I/O.

---

## 10. Standard Alarm Creation & List UI Architecture

### Decision
Implement standard alarm creation and list management using native Android framework widgets:
1. **Creation / Edit Interface**:
   - TimePicker (`android.widget.TimePicker` in 24-hour mode).
   - Recurrence selector supporting "Once" (default), Day-of-week bitmask toggles (Mon–Sun), and "Statutory Workdays" toggle.
   - Text input for alarm label and quick shortcuts for Crescendo, Vibration, and Challenge selection.
2. **Main List View**:
   - Scrollable card view where each card displays:
     - Prominent trigger time in `HH:mm` format.
     - Recurrence summary (e.g. "Once", "Every Day", "Mon, Wed, Fri", or "Statutory Workdays").
     - User label / memo.
     - Instant `android.widget.Switch` for enabling/disabling the alarm without entering the edit screen.
   - Empty state placeholder with friendly prompt when no alarms exist.
   - Floating Action Button (`btn_add_alarm`) for one-tap creation.

### Rationale
- Familiar, friction-free UX meeting standard alarm expectations.
- Native widgets add 0 KB overhead and deliver sub-100ms switch responsiveness.
- Seamlessly blends baseline alarm utility with advanced China localization features.

### Alternatives Considered
- **Third-party wheel picker libraries**: Adds unnecessary binary footprint and custom styling overhead.
- **Text input only**: Error-prone and poor touch ergonomics.

---

## 11. Long-Press Context Menu & Monthly Calendar Skip Management

### Decision
1. **Long-Press Menu Flow**:
   - Long-pressing any alarm card opens a contextual dialog offering three distinct operations:
     - **"Skip Dates / Vacation Mode" (跳过日期 / 休假模式)**
     - **"Edit Alarm" (编辑闹钟)**
     - **"Delete Alarm" (删除闹钟)**
2. **Monthly Calendar Date Picker**:
   - Selecting "Skip Dates" launches `VacationCalendarDialog`.
   - Displays a calendar grid for the selected month with month/year navigation controls.
   - Users can tap specific dates to toggle/cancel alarm reminders.
   - Persists a set of skipped dates formatted as ISO-8601 strings (`YYYY-MM-DD`) attached to that alarm.
3. **Status Badging & Trigger Suppression**:
   - When skipped dates exist, the alarm card displays an active status badge (e.g., `"Vacation Mode (X days skipped)"`).
   - When the scheduled time arrives, the scheduling engine evaluates whether the current calendar date matches an entry in the alarm's skipped dates set:
     - If matched, ringing is suppressed for that day, and the scheduler advances directly to the subsequent cycle.
     - If not matched, the alarm rings normally.

### Rationale
- Replaces raw deletion on long-press with a user-friendly contextual menu.
- Empowers users to handle vacation periods and shifts without altering their underlying recurrence schedule.

---

## 12. System AlarmManager Scheduling, Quick Nap Lifecycle & Per-Alarm Audio/Haptics

### Decision
1. **System AlarmManager Integration** (superseded and tightened by the later delivery clarification in Section 14):
   - When alarms are created, toggled, or modified, persist the precise next occurrence before registering it through the single scheduler gateway.
   - Invoke `AlarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent), broadcastPendingIntent)` when exact capability is available.
   - Schedule, replace, and cancel only through the canonical broadcast PendingIntent factory.
   - `AlarmTriggerReceiver` validates and atomically claims the occurrence, then hands bounded WakeLock, alarm-channel audio, and haptics to `AlarmRingingService`. The service notification may present `RingingActivity` via full-screen intent; neither receiver nor Activity is the long-lived audio owner.
2. **Quick Nap Lifecycle in Main List**:
   - Tapping 15m, 30m, 45m, or 60m instantly creates an active alarm card in `MainActivity` with time set to `now + N minutes`, labeled `"Quick Nap (Nm)"`, and flagged with `isQuickNap = true`.
   - The card displays dynamic remaining countdown and an exclusive Quick Nap badge.
   - Upon alarm trigger completion, user dismissal, or manual switch toggle Off, the quick nap alarm automatically self-destructs and removes itself from the list and persistent storage.
3. **Per-Alarm Ringtone & Vibration Configuration**:
   - In `AlarmEditDialog`, provide dedicated rows for "Ringtone" and "Vibration".
   - Tapping Ringtone invokes Android's native system `RingtoneManager` (`ACTION_RINGTONE_PICKER` with `TYPE_ALARM`) for choosing system alarm tones, defaulting to system default if unassigned.
   - Vibration provides an independent `Switch` toggle controlling whether haptic feedback is triggered.
   - `AlarmItemModel` and persistent JSON store `ringtoneUri` and `vibrateEnabled` fields per alarm.

---

## 13. In-App Dynamic Language Switching (Simplified Chinese & English)

### Decision
1. **Multi-Locale Architecture**:
   - Support three settings: `"system"` (Follow System / 跟随系统), `"zh"` (简体中文), and `"en"` (English).
   - On Android 13+ (API 33+), invoke `LocaleManager.setApplicationLocales(LocaleList.forLanguageTags(tag))`.
   - For Android 11–12 (API 30–32), persist selected locale in `SharedPreferences`, override base context in `Application.attachBaseContext()` and `Activity.attachBaseContext()` using `context.createConfigurationContext(config)`, and call `activity.recreate()` when the locale changes.
2. **Resource Organization**:
   - Maintain string tables in `res/values/strings.xml` (English / base) and `res/values-zh-rCN/strings.xml` (Simplified Chinese).
   - Ensure all UI text across `MainActivity`, `AlarmEditDialog`, `VacationCalendarDialog`, `SettingsActivity` / `SettingsDialog`, notifications, and `RingingActivity` are referenced via string resources (`R.string.*`).

### Rationale
- Standard Android resource system guarantees zero runtime performance overhead and instant language switching.
- Dual compatibility covers Android 11 through Android 15 seamlessly without external localization libraries.

### Alternatives Considered
- **Dynamic text replacement map**: Cumbersome, error-prone, violates Android best practices, and lacks automated pluralization and system dialog alignment.

---

## 14. Swiped-From-Recents & Task-Kill Wakeup Resilience

### Decision
1. **Canonical PendingIntent Identity**:
   - Use one explicit immutable broadcast PendingIntent factory for schedule, reschedule, snooze, and cancel.
   - Identity is defined by receiver component, action, stable alarm request code, and an alarm-specific data URI; extras are payload only and MUST NOT be relied upon to distinguish or cancel registrations.
2. **Supported Exit States**:
   - Ordinary backgrounding, OS process reclamation, and Recent Tasks removal use the same persisted alarm and OS registration. No Activity, Handler, or standing Service is required before the trigger.
   - Recent Tasks removal is not defined by Android as force-stop and MUST be validated separately on Samsung and Vivo/iQOO rather than inferred from `ApplicationExitInfo` alone.
3. **Receiver-Owned Handoff**:
   - `AlarmTriggerReceiver` verifies the persisted occurrence, obtains bounded execution time, starts the foreground ringing owner, and posts the alarm notification/full-screen intent.
   - `RingingActivity` displays controls and lockscreen UI but is not the sole owner of audio/haptics, so loss or denial of full-screen UI does not automatically silence the alarm.
4. **Recurring Continuity**:
   - After a valid occurrence is claimed, the scheduler atomically records/derives the next occurrence and registers it. Dismiss, snooze, skip, cancel, and repeat completion all use the same scheduler gateway.

### Rationale
- A single identity factory fixes mismatched schedule/cancel PendingIntents and makes reconciliation idempotent.
- Separating ringing execution from Activity presentation supports the clarified permission-degraded behavior.
- Real state-matrix tests prevent `am force-stop` or injected component starts from being mislabeled as Recent Tasks evidence.

### Alternatives Considered
- **Direct Activity PendingIntent**: Rejected because it bypasses receiver validation and lacks an independent audio path when background UI launch is unavailable.
- **Persistent Foreground Service**: Rejected because ringing execution only needs to exist during an active occurrence.
- **Different PendingIntent shapes for schedule and cancel**: Rejected because Android compares operation identity rather than extras, leaving stale alarms if they do not match.

---

## 15. Pre-Alarm Advance Notification & Single-Occurrence Dismissal

### Decision
1. **Advance Timing Schedule**:
   - Schedule a secondary system alarm for `triggerTime - N * 60 * 1000L` where $N$ is configured in `AppSettings` (default $N = 30$ minutes).
   - If lead time $N$ is 0 or advance notification is disabled, skip scheduling.
2. **Notification Presentation & Quick Action**:
   - Post a notification on a dedicated `ALARM_ADVANCE_CHANNEL` with `PRIORITY_DEFAULT` / `IMPORTANCE_LOW` (non-intrusive heads-up).
   - Content: "Upcoming Alarm at HH:mm" / "即将响铃: HH:mm".
   - Include action button: "Dismiss / 快捷关闭" triggering `ACTION_SKIP_TODAY` via a broadcast `PendingIntent`.
3. **Single-Occurrence Skip Semantics**:
   - When user taps "Dismiss":
     - Dismiss the advance notification immediately.
     - For recurring alarms (`repeat_mode != 0`): Mark today's ISO date string (`YYYY-MM-DD`) in `alarm_skip_rules` and immediately recalculate and reschedule `AlarmManager` for the next cycle.
     - For one-time alarms (`repeat_mode == 0`): Cancel the upcoming alarm and mark `is_enabled = 0`.
   - Show confirmation Toast: "Upcoming alarm dismissed / 已快捷关闭今日闹钟".

### Rationale
- Solves the common user frustration of waking up early and having to disable the alarm, then forgetting to re-enable it tomorrow.

---

## 16. Vacation Calendar Grid Layout Polish

### Decision
1. **Sunday-First Grid Alignment**:
   - Set the first day of the week to Sunday (`Calendar.SUNDAY = 1`).
   - Calculate month start offset: `int firstDayOffset = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7;`.
2. **Day-of-Week Header Row**:
   - Add a dedicated 7-column header above the calendar grid:
     - Chinese: `日  一  二  三  四  五  六`
     - English: `Sun Mon Tue Wed Thu Fri Sat`
   - Center-align text in compact 12sp font with muted secondary color.
3. **Scaled Month Navigation Controls**:
   - Replace oversized buttons with compact 40dp $\times$ 40dp touch targets for `<` (previous month) and `>` (next month).
   - Month/Year title (e.g., "2026年9月" / "September 2026") centered between buttons without overlap or clipping.

### Rationale
- Adheres to standard calendar visual hierarchy, prevents header truncation on narrow screens (e.g. 1080p density), and provides unambiguous date alignment.

---

## 17. Customizable Quick Nap Preset Slots

### Decision
1. **Slot Data Model & Unit Support**:
   - 4 customizable slots: Slot 1, Slot 2, Slot 3, Slot 4.
   - Each slot has a numeric value ($> 0$) and a unit (`MINUTES` or `HOURS`).
   - Default values: Slot 1 = 15m, Slot 2 = 30m, Slot 3 = 45m, Slot 4 = 60m.
2. **Dual-Entry Configuration**:
   - **Settings Screen**: Full configuration panel allowing input of duration value and unit selector (radio/spinner/chips: Minutes vs. Hours) for each of the 4 slots.
   - **Dashboard Long-Press**: Long-pressing any nap button on `MainActivity` opens a quick dialog allowing immediate modification of that specific slot's value and unit.
3. **Dynamic UI Synchronization**:
   - Updating slot values updates the dashboard button text dynamically (e.g., "15m", "30m", "1h", "2h").
   - Tapping the button schedules an alarm for `now + durationInMinutes`.

### Rationale
- Offers maximum flexibility for short power naps (e.g., 20m) and extended afternoon rest (e.g., 1.5h or 2h) without cluttering the main screen.

---

## 18. Vivo / iQOO OriginOS Exit-State Resilience

### Decision
1. **Do Not Equate Recent Tasks Removal with Force-Stop**:
   - Use the same explicit broadcast PendingIntent delivery pipeline on AOSP, Samsung, and Vivo/iQOO.
   - Verify Recent Tasks removal on each named device as its own runtime state. Do not assert that it sets Android's package `FLAG_STOPPED` without direct device evidence.
2. **Force-Stop Boundary**:
   - Android force-stop removes runtime alarm/notification state and keeps the package stopped until the user explicitly interacts with it. A normal third-party app has no public API to bypass this control.
   - `FLAG_INCLUDE_STOPPED_PACKAGES` on an Intent does not recreate canceled AlarmManager registrations and is not a force-stop recovery mechanism.
   - Keep the force-stop limitation in initial/reliability guidance on every supported API level. With the current compile SDK 34, use unconditional launch reconciliation and MUST NOT guess force-stop from Recent Tasks/process-exit signals.
   - On every explicit cold launch, run the idempotent schedule reconciler before reporting protection as active, whether or not force-stop can be identified.
3. **One-Tap OriginOS System Guidance**:
   - Implement `OemPermissionHelper` detecting `Build.MANUFACTURER.equalsIgnoreCase("vivo") || Build.BRAND.equalsIgnoreCase("iqoo")`.
   - Provide direct intent navigation to OriginOS system management pages:
     - "Allow Background High Power Consumption" (`com.vivo.abe` / `moneysavingdetail.HighPowerManageActivity`).
     - "Autostart / Background Launch" (`com.iqoo.secure` / `BgStartUpManager`).
     - Battery Optimization Whitelist (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   - Present guidance in Settings and initial onboarding, but do not represent whitelist navigation as a guarantee that OEM policy cannot later suppress delivery.

### Rationale
- Preserves the user-selected product boundary: normal exit states are supported; force-stop/deep-stop is a visible platform limitation until explicit relaunch.
- Avoids relying on an unsupported claim that one Intent flag can defeat stopped-package semantics.
- Keeps the Samsung/iQOO test matrix evidence-driven without introducing vendor SDK dependencies.

Official references: [ApplicationInfo.FLAG_STOPPED](https://developer.android.com/reference/android/content/pm/ApplicationInfo#FLAG_STOPPED), [Android 15 stopped-state changes](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state), [AOSP Android 15 force-stop contract](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/Intent.java).

### Alternatives Considered
- Direct `PendingIntent.getActivity()` plus `FLAG_INCLUDE_STOPPED_PACKAGES`: rejected because it cannot restore alarms canceled by force-stop and couples delivery to activity launch policy.
- Persistent service/OEM keep-alive tricks: rejected because they cannot override force-stop, increase idle cost, and are not portable guarantees.
- Privileged/OEM-signed installation: rejected because the clarified product remains an ordinary third-party application.

---

## 19. Remote Statutory Holiday Synchronization & 7-Day Auto-Retry Cadence

### Decision
1. **Configurable Endpoint in Settings**:
   - Provide an editable URL text field in `SettingsDialog` pre-filled with a default CDN / GitHub Raw URL hosting official holiday JSON:
     `https://raw.githubusercontent.com/edom/alarm/main/core/src/assets/holidays_2026.json` (or customizable by user).
   - Provide a "Reset to Default" button and a "Sync Now / 立即同步" action button.
2. **Manual Sync Workflow**:
   - Tapping "Sync Now" triggers asynchronous HTTP GET using Android standard `HttpURLConnection`.
   - On HTTP 200: validate JSON schema (`year`, `holidays` array, `workdays` array). If valid, save to local SharedPreferences (`holiday_rules_<YEAR>`), reload active holiday engine, update `last_holiday_sync_timestamp` and `last_holiday_sync_status = true`, and display a success Toast.
   - On HTTP error / timeout / invalid JSON: abort update, keep existing local rules completely untouched, and display a modal AlertDialog ("Update Failed / 更新失败") explaining the error.
3. **Automatic Startup Sync Cadence**:
   - On cold app launch (`MainActivity.onCreate()`):
     - If `last_holiday_sync_timestamp == 0` (first launch) OR `last_holiday_sync_status == false` (previous attempt failed): execute background sync.
     - If `System.currentTimeMillis() - last_holiday_sync_timestamp >= 7 * 24 * 60 * 60 * 1000L` (7 days elapsed since last successful sync): execute background sync.
     - Otherwise: skip sync.
   - All automatic startup sync failures are completely silent (logged only, no popup modals or toasts) to guarantee zero interruption during morning app interactions.

### Rationale
- Balances up-to-date statutory holiday compliance with offline reliability and battery conservation, preventing annoying popups on device launch while ensuring resilient retries.

---

## 20. Statutory Holiday Generator Script (`scripts/generate_holiday_config.py`)

### Decision
1. **Data Sourcing & Gazette Verification**:
   - Standalone Python 3 script in `scripts/generate_holiday_config.py`.
   - Queries open Chinese holiday APIs (e.g. `timor.tech/api/holiday` or government gazette open feeds) with fallback to local rule templates.
   - Automatically checks whether the State Council (国务院办公厅) annual announcement for next year ($Y+1$) has been gazetted:
     - If gazetted: parses and generates both $Y$ and $Y+1$ configs.
     - If unannounced: generates only year $Y$ config and prints a clear informational message: `"Next year (Y+1) holiday schedule has not yet been announced by the State Council; generated current year only."`
2. **Standard Output Targets**:
   - Generates formatted JSON matching `{ "year": YYYY, "holidays": ["YYYY-MM-DD", ...], "workdays": ["YYYY-MM-DD", ...] }`.
   - Writes directly to `core/src/assets/holidays_<YEAR>.json` (for bundling into release APK).
   - Writes to an export distribution folder `build/holidays/` (ready for deployment to GitHub / CDN servers).

### Rationale
- Eliminates manual JSON crafting errors and enables automated CI/CD regeneration whenever official holiday shifts are proclaimed.

---

## 21. Skip Configuration Dual-Entry Inspection & Clear Workflow

### Decision
1. **Dual Entry Points**:
   - **Card Badge Tap**: When an alarm has active skips, tapping the `Vacation (X skipped)` badge on the alarm card directly opens `VacationCalendarDialog` pre-populated with those skipped dates.
   - **Context Menu**: Long-pressing the card and choosing "Skip Dates / Vacation Mode" continues to open `VacationCalendarDialog`.
2. **Inspection & Modification in Calendar**:
   - All previously selected skipped dates are highlighted in dark orange with light orange background.
   - Users can tap highlighted dates to deselect them, or tap unhighlighted dates to add them.
   - Tapping "Save Skips" validates and updates the alarm's skipped dates via confirmation dialog.
3. **Clear All Skips**:
   - Add a dedicated "Clear Skips / 清除跳过" button to `dialog_vacation_calendar.xml`.
   - Tapping "Clear Skips" instantly purges all skipped dates from the alarm, dismisses the dialog, updates the card badge (removing the vacation badge), and saves the updated configuration.

### Rationale
- Intuitive and frictionless: users can glance at the badge, tap it to see the exact dates, and remove or adjust skips with a single tap.

---

## 22. Physical Device Testing on iQOO Z9 Turbo+ (`10CEAF0KDX000CK`)

### Decision
1. **Device Identification**:
   - Device Serial: `10CEAF0KDX000CK`.
   - Model: Vivo V2417A (iQOO Z9 Turbo+), running Android 16 (API Level 36).
2. **Automated Physical Device Verification**:
   - Provide a dedicated script `scripts/verify_device.sh` (or parameterized `scripts/verify_emulator.sh 10CEAF0KDX000CK`).
   - Verify APK installation, capability states, AlarmManager registration, Settings interaction, and autonomous OS delivery after backgrounding and Recent Tasks removal.
   - Run force-stop as a negative boundary test: expect no autonomous ring while stopped, explicitly relaunch, then verify all enabled alarms are reconciled and the next registered occurrence rings.

### Rationale
- Physical-device evidence is required for OEM task-removal and settings behavior; emulator component injection cannot establish OriginOS delivery guarantees.

### Alternatives Considered
- Treating `am start` or an injected broadcast after `am force-stop` as wakeup proof: rejected because those commands explicitly remove the stopped state or invoke the component and do not test the pending alarm.

---

## 23. Alarm Capability Degradation & Protection Status

### Decision
1. Use `SCHEDULE_EXACT_ALARM` as this build's single API 31+ exact-alarm permission strategy and remove the unbounded `USE_EXACT_ALARM` declaration. This keeps denial/revocation behavior and the settings recovery flow testable without assuming store-policy eligibility. API 30 requires neither special-access permission.
2. Evaluate a runtime capability snapshot whenever the dashboard resumes and before each schedule/reconcile operation:
   - `exact_alarm_available`: true on API 30; on API 31+ use the platform exact-alarm capability check appropriate to the declared permission.
   - `notifications_available`: use notification enablement plus the Android 13+ runtime permission state.
   - `full_screen_available`: true where the platform has no user-controlled full-screen app-op; on Android 14+ use `NotificationManager.canUseFullScreenIntent()`.
3. Derive `AlarmProtectionLevel.FULL` only when exact timing, notifications, and full-screen alert are available **and** the latest reconciliation completed with a current successful registration for every enabled alarm. Any missing capability, incomplete reconciliation, or failed/unregistered occurrence produces `LIMITED`, while alarm activation remains allowed.
4. Scheduling policy:
   - exact available: `setAlarmClock()` with the canonical broadcast PendingIntent;
   - exact unavailable: `setAndAllowWhileIdle()` with the same broadcast PendingIntent and no exact-timing claim.
5. Presentation policy: while at least one enabled alarm exists and protection is `LIMITED`, keep a localized dashboard banner visible. Its action opens the highest-impact missing system setting (exact alarm first, then notifications, then full-screen intent), or retries/reports failed registration when capabilities are present; returning to the app re-evaluates and reconciles.
6. Notification/full-screen denial does not stop the receiver from attempting the bounded ringing execution path. It removes the guarantee that a visual alert surface is available.
7. An exact user alarm is exempt from the background foreground-service start restriction. A best-effort inexact fallback does not receive that same guarantee: if starting the ringing service is rejected, handle the exception without crashing, post the permitted alarm notification/full-screen fallback, and retain `LIMITED` status. If no permitted execution or visual surface remains, record the failed attempt; best-effort does not become a false success claim.
8. If exact-alarm access is externally revoked, Android may stop the package and remove exact registrations. Because no revoke callback can run in that stopped interval, conversion to best-effort occurs on the next explicit resume/relaunch; the application does not promise autonomous fallback while stopped.

### Rationale
- Matches the user's clarified choice to allow activation while refusing to label best-effort delivery as fully protected.
- Separates three independent platform controls that fail differently: timing, notification visibility, and lockscreen/full-screen presentation.
- A derived runtime state avoids persisting stale permission truth and disappears automatically after capability recovery.

Official references: [exact-alarm permissions](https://developer.android.com/develop/background-work/services/alarms#exact-permission), [notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission), [Android 14 full-screen intent changes](https://developer.android.com/about/versions/14/behavior-changes-14#full-screen-intent-notifications).

Background execution reference: [foreground-service background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

### Alternatives Considered
- Declare both `USE_EXACT_ALARM` and `SCHEDULE_EXACT_ALARM` without API/policy scoping: rejected because Android documents them as alternative permission models with different grant and distribution rules.
- Block alarm activation until all capabilities are granted: rejected by the clarified requirement.
- Silent best-effort fallback: rejected because a missed-alarm risk must remain visible and actionable.
- Persist capability flags as configuration: rejected because permissions and app-ops can change outside the application and must be read from the system.

---

## 24. Durable Occurrence, Recovery, and Boot Storage

### Decision
1. Extend the current SharedPreferences alarm JSON backward-compatibly with `next_trigger_at_ms`, monotonic `occurrence_generation`, string `occurrence_id` (`alarmId:generation:triggerAtMs`), `last_claimed_occurrence_id`, and `missed_state`. Persist the new occurrence before AlarmManager registration.
2. The receiver performs one synchronized store transaction: load by alarm ID, validate enabled/skip/`occurrence_id`/`trigger_at_ms`, compare `last_claimed_occurrence_id`, then record the claim before starting ringing. Duplicate or stale delivery is a no-op with an auditable result.
3. Legacy JSON without occurrence fields is upgraded on load. Reconciliation computes and persists a fresh occurrence; it never trusts a payload that predates the upgrade. Tests cover legacy decode, process reconstruction, duplicate trigger, cancel/replace identity, and registration failure.
4. If reconciliation finds an unclaimed persisted occurrence whose trigger is already due, it does not ring retroactively or shift a one-off alarm to tomorrow. Standard one-time alarms become missed+disabled, expired Quick Naps become missed+deleted after surfacing the missed result, and recurring alarms record the miss then compute the next strictly future valid occurrence.
5. Alarm records remain in credential-protected storage. Remove `LOCKED_BOOT_COMPLETED` and `directBootAware` delivery claims for this repair; restore schedules from `BOOT_COMPLETED` after unlock. Pre-unlock delivery requires a future, separately designed device-protected occurrence store.
6. A reconciliation report lists every enabled alarm, intended occurrence, registration mode/result, and failure. Protection is `FULL` only when required capabilities are present, reconciliation is complete, and every enabled alarm has a current non-`NOT_REGISTERED` occurrence.
7. FR-026 hardware power-off RTC wakeup is not part of this application-exit repair. It remains a separate feature gated on a documented OEM API available to third-party apps, a device-protected occurrence subset, and powered-off/pre-unlock evidence on each supported device.

### Rationale
- Durable identity is required to reject old PendingIntents after edits, process death, and repeated broadcasts.
- Explicit expired-occurrence rules avoid silently turning yesterday's one-time alarm into tomorrow's alarm.
- The chosen boot boundary matches what the current credential-protected store can actually provide.

Official references: [AlarmManager exact alarm permission behavior](https://developer.android.com/reference/android/app/AlarmManager), [Direct Boot storage](https://developer.android.com/privacy-and-security/direct-boot).

---

## 25. Existing Delivery Gaps & Minimal Repair Boundary

### Decision
Replace the current split delivery behavior with four narrow collaborators:

1. `AlarmSystemScheduler` owns canonical PendingIntent construction, exact/best-effort selection, registration, and cancellation.
2. `AlarmTriggerReceiver` becomes the sole scheduled alarm entry point and hands valid occurrences to a bounded `AlarmRingingService` plus the alarm notification/full-screen UI path.
3. `AlarmScheduleReconciler` loads enabled alarms and idempotently restores their next occurrences on app launch/resume, boot/time/timezone/package-replace, and exact-capability recovery.
4. `AlarmCapabilityEvaluator` and `AlarmProtectionPresenter` derive and display `FULL` versus `LIMITED` protection without embedding permission logic in `MainActivity`.

The repair also makes recurring completion use the same scheduler gateway and makes cancel construct an operation identical to schedule. Existing alarm JSON remains the source with the backward-compatible occurrence fields defined in Section 24; SQLite migration and unrelated feature completion are excluded.

### Rationale
- Current scheduling uses a direct Activity PendingIntent, while the receiver's full-screen notification is a separate path; this prevents one coherent degradation model.
- Current boot recovery only logs, application loading does not restore existing enabled alarms, and recurring delivery does not consistently register the next occurrence.
- Current cancellation constructs an Activity PendingIntent without the scheduled action, risking a non-matching operation.
- Current ADB scripts force-stop and then explicitly start/broadcast components, which proves entry points rather than autonomous OS delivery.

### Alternatives Considered
- Patch only `MainActivity` to reschedule on launch: rejected because it leaves split delivery, mismatched cancellation, boot recovery, recurring continuity, and permission state unresolved.
- Keep the direct Activity operation and add a second broadcast fallback: rejected because two authoritative PendingIntents create duplicate/stale occurrence risk.
- Migrate all alarm persistence to SQLite in the same change: rejected as unrelated risk under the minimal-change principle.
