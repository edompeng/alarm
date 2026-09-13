# Research & Architectural Decisions: Android Smart Alarm

**Feature**: `001-android-smart-alarm`
**Date**: 2026-09-13
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

## 3. High-Precision Wakeup & Background Resilience on Android 11+ (API 30~35)

### Decision
1. **Precision Alarm Dispatch**:
   - Utilize `AlarmManager.setAlarmClock(new AlarmClockInfo(triggerTime, pendingIntent), operationIntent)`.
   - On Android, `setAlarmClock()` is the highest-priority alarm API: it is explicitly exempt from aggressive Doze mode throttling, displays an official alarm icon on the status bar and AOD, and guarantees sub-second firing accuracy.
   - Declare `USE_EXACT_ALARM` (Android 13+) and fallback to `SCHEDULE_EXACT_ALARM` (Android 12).
2. **Immediate Wakeup UI**:
   - Launch via high-priority `Notification` with `setFullScreenIntent(fullScreenPendingIntent, true)` attached to a dedicated `IMPORTANCE_HIGH` notification channel.
   - Wake lock acquired safely via `WakefulBroadcastReceiver` pattern / `PARTIAL_WAKE_LOCK` with an automatic 10-minute timeout safeguard.
3. **Reboot & Direct Boot Resilience**:
   - Declare `android:directBootAware="true"` for the alarm receiver and SQLite database storage in device-protected storage context (`createDeviceProtectedStorageContext()`), allowing alarms to ring even if the device reboots and remains locked at the lockscreen.
4. **OEM Cold Shutdown / Power-Off Alarm**:
   - On **Samsung One UI (S25 Ultra)** and **Vivo/iQOO OriginOS (Z9 Turbo+)**, vendor ROMs support RTC wake if registered with the system alarm clock or OEM-specific broadcast hooks.
   - For standard app space, register `android.intent.action.BOOT_COMPLETED` and `android.intent.action.LOCKED_BOOT_COMPLETED` to reschedule immediately if the phone boots up. On devices supporting OEM RTC auto-power-on, dispatch vendor alarm actions.

### Alternatives Considered
- `WorkManager`: Inadequate. WorkManager does not guarantee exact-second execution and is subject to Doze battery saving and job batching.
- Normal `AlarmManager.setExact()`: Subject to user-level battery optimization restrictions unless battery optimization is whitelisted; `setAlarmClock()` is far superior.

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
Establish an automated ADB-driven test harness (`scripts/verify_emulator.sh`) targeting active local Android emulators (`emulator-5554` running Android 14 / API 34):
1. **Automated Package Lifecycle**: Deploys the built APK, grants runtime permissions (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`), and verifies clean process launch.
2. **UI Journey Automation**: Simulates user interactions via `adb shell input` and `am start` (alarm creation, quick nap triggering, multi-day skip dialog navigation).
3. **Sensor & Time Event Simulation**: Simulates orientation/shake sensor triggers via ADB broadcasts and manipulates system clocks to trigger statutory holiday and Doze wakeup evaluations.
4. **Database State Verification**: Directly queries the app's SQLite database on the emulator via `adb shell sqlite3` or content query to assert relational record accuracy.

### Rationale
- Ensures fast, automated, repeatable verification without requiring physical manual interaction.
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
