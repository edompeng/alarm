# Feature Specification: Android Smart Alarm (智能闹钟)

**Feature Branch**: `001-android-smart-alarm`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "帮我设计一款安卓手机闹钟，需要支持安卓11及以上版本，需要在三星S25 ultra和iqoo z9 turbo+手机上包含以下功能：本土化核心特色功能（法定工作日与调休智能识别、本地化在线音乐/流媒体铃声、提前跳过/临时关闭一次、跳过未来N天免打扰）、声音与触感（独立闹钟音量通道、音量渐强、耳机/扬声器智能路由、多样化线性马达振动波形、振动强度调节、音频与振动同步）、交互方式与防赖床机制（手势与传感器交互、小睡设置、计算题/摇晃手机趣味防赖床）、底层硬件与实用细节（关机闹钟、自动唤醒app、倒计时Toast、快速午休、息屏显示AOD联动、闹钟标签管理与TTS）、高稳定性与低资源占用。"

## Clarifications

### Session 2026-09-13
- Q: How should the core architecture decouple platform capabilities so that both Android and a future iOS app can share the business engine? → A: Pure C++ abstract platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`) implemented natively by Android (via JNI) and iOS (via Objective-C++/Swift), maximizing code reuse with zero external runtime overhead.
- Q: How should the end-to-end functional verification be executed and validated on the active local Android emulator? → A: Automated ADB-driven E2E verification script that exercises UI journeys, simulates sensor and alarm events, and validates live SQLite database state on the local Android emulator (`emulator-5554`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Statutory Workday & Holiday Smart Scheduling (Priority: P1)

As a working professional in mainland China with shifting work schedules,
I want my alarm clock to automatically recognize official Chinese holiday arrangements and compensatory working weekends (调休补班),
So that I wake up on time on compensatory workdays and enjoy uninterrupted rest on national holidays without repeatedly reconfiguring recurring alarms.

**Why this priority**:
Statutory holiday and compensatory workday management is the core foundation for mainland China localization. Missing an alarm on a weekend compensatory workday causes tardiness, while accidental ringing on a national holiday disrupts rest. This delivers immediate, high-frequency daily value as an MVP.

**Independent Test**:
Can be fully tested by configuring a recurring alarm set to "Statutory Workdays". When simulated calendar dates are set to a national holiday falling on a weekday (e.g., Wednesday of Golden Week), the alarm remains silent. When set to a weekend compensatory workday (e.g., Sunday before a holiday), the alarm triggers on schedule.

**Acceptance Scenarios**:
1. **Given** a recurring alarm set to "Statutory Workdays", **When** the current date is an official national holiday (e.g., Spring Festival, National Day) falling on Monday through Friday, **Then** the alarm does not ring and automatically advances its next scheduled occurrence to the subsequent valid working date.
2. **Given** a recurring alarm set to "Statutory Workdays", **When** the current date is an official compensatory working weekend day (Saturday or Sunday designated as a workday by the State Council), **Then** the alarm rings at the configured time.
3. **Given** the device has network connectivity, **When** new annual holiday/workday schedules are officially released, **Then** the system automatically synchronizes and updates the local holiday calendar rules without requiring manual user intervention.
4. **Given** the device is offline or in airplane mode, **When** evaluating whether to ring on a statutory workday, **Then** the system relies on locally cached holiday rules.

---

### User Story 2 - Guaranteed Audio, Haptic Wakeup & Audio Routing (Priority: P1)

As a sleeping user,
I want the alarm to ring reliably at the set time using an independent, high-priority audio channel and rich tactile vibration,
So that I wake up dependably even if my phone is in Do Not Disturb (DND) mode, muted, or connected to headphones left on the nightstand.

**Why this priority**:
The essential core mission of an alarm clock is waking the user up without fail. If audio fails to penetrate silence mode or plays exclusively through detached headphones, the alarm fails its primary purpose.

**Independent Test**:
Can be fully tested by enabling global mute and DND mode, plugging in or connecting Bluetooth headphones, setting an alarm with "Always ring via phone speaker" enabled, and verifying that the alarm rings loudly through the built-in speaker with progressive volume and haptic vibration.

**Acceptance Scenarios**:
1. **Given** the phone is set to total silence, vibration-only, or Do Not Disturb mode, **When** the alarm triggers, **Then** the alarm plays through an independent alarm audio stream at the configured alarm volume level, overriding system mute.
2. **Given** "Volume Crescendo" (音量渐强) is enabled, **When** the alarm begins ringing, **Then** audio volume starts at minimum and smoothly increases to the target volume over a configurable duration (10 to 30 seconds).
3. **Given** Bluetooth or wired headphones are connected and "Always ring via phone speaker" is active, **When** the alarm triggers, **Then** sound is routed to the phone's built-in loudspeaker (or simultaneously to both headphones and loudspeaker).
4. **Given** haptic feedback is enabled, **When** the alarm rings, **Then** the phone motor produces the selected vibration pattern (e.g., Heartbeat, Wave, Staccato) synchronized with the audio rhythm at the configured vibration intensity.

---

### User Story 3 - Advance Skip & Vacation Multi-Day Calendar Management (Priority: P2)

As a user who frequently wakes up early or takes annual leave,
I want to easily dismiss the upcoming alarm in advance or skip multiple specific vacation days directly from a calendar view,
So that I do not get disturbed while awake or on vacation, without accidentally disabling my long-term recurring schedule.

**Why this priority**:
A major pain point with standard recurring alarms is that turning off an alarm when waking early often leads to forgetting to re-enable it for the next day. A dedicated advance skip and multi-day calendar vacation manager solves this without compromising long-term habit tracking.

**Independent Test**:
Can be fully tested by creating a recurring Monday-Friday alarm. When the notification appears 45 minutes before the alarm, tap "Skip Today". Verify the alarm does not ring today, but is automatically scheduled for tomorrow. Then, long-press the alarm, open the calendar picker, deselect a 3-day range, confirm, and verify those 3 days are skipped while all other days ring as scheduled.

**Acceptance Scenarios**:
1. **Given** an upcoming alarm scheduled within 30 to 60 minutes, **When** the user views the lockscreen or notification tray, **Then** an ongoing advance card appears showing "Alarm rings in X minutes" with a prominent "Skip Today / Dismiss Once" action button.
2. **Given** the user taps "Skip Today", **When** the scheduled time arrives, **Then** the alarm remains silent for that specific instance, dismissed notification clears, and the alarm automatically resets its trigger for the subsequent scheduled cycle.
3. **Given** a recurring alarm, **When** the user long-presses the alarm item and selects "Skip Multiple Days / Vacation Mode", **Then** a calendar dialog opens displaying dates from today through the end of the selected month, with all normally scheduled ringing dates highlighted.
4. **Given** the multi-day calendar view is displayed, **When** the user taps on highlighted dates to deselect them and clicks Save, **Then** a secondary confirmation dialog lists the exact dates to be skipped.
5. **Given** the user confirms the secondary prompt, **When** the skipped dates arrive, **Then** the alarm does not ring on those dates, but rings normally on all non-skipped scheduled dates.

---

### User Story 4 - Natural Gestures, Hardware Keys & Anti-Oversleep Challenges (Priority: P2)

As a heavy sleeper prone to snoozing unconsciously,
I want intuitive physical gestures to silence or snooze the alarm, combined with optional math or shake challenges to ensure I am fully awake,
So that I can quickly silence minor interruptions when conscious, but avoid accidental oversleeping on important mornings.

**Why this priority**:
Provides the tactile bridge between the waking state and morning control. Heavy sleepers need strict anti-oversleep enforcement, while light sleepers need effortless flip-to-mute gestures.

**Independent Test**:
Can be fully tested by triggering an active alarm, turning the phone face down to observe automatic snoozing, picking it up to verify volume reduction, and attempting to dismiss an alarm configured with a math or shake challenge to verify it cannot be dismissed until the challenge is completed.

**Acceptance Scenarios**:
1. **Given** the alarm is ringing and "Flip to Mute/Snooze" is enabled, **When** the user turns the phone screen-down on a flat surface, **Then** the alarm immediately mutes or transitions into snooze mode based on the user preference.
2. **Given** the alarm is ringing at full volume and "Pick Up to Reduce Volume" is enabled, **When** the user lifts the phone, **Then** the volume automatically attenuates to a gentle background level.
3. **Given** custom hardware key mapping is configured, **When** the user presses the Power button or Volume buttons during ringing, **Then** the system executes the assigned action (Dismiss, Snooze, or Mute).
4. **Given** an alarm with a Math Challenge enabled is ringing, **When** the user attempts to dismiss the alarm, **Then** the system displays a mental arithmetic question matching the configured difficulty and only dismisses the alarm upon entering the correct answer.
5. **Given** an alarm with a Shake Challenge enabled is ringing, **When** the user attempts to dismiss the alarm, **Then** the system requires shaking the phone for the target count (e.g., 30 shakes) with real-time progress feedback before allowing dismissal.
6. **Given** Snooze is triggered, **When** the configured snooze interval (5, 10, 15, 30 min, or custom) elapses, **Then** the alarm rings again, up to the maximum configured repetition limit (1, 3, 5 times, or infinite).

---

### User Story 5 - Streaming Music & Dynamic Weather Soundscapes (Priority: P3)

As a user who appreciates an enjoyable morning routine,
I want to wake up to streaming music from mainstream music services (QQ Music, NetEase Cloud Music) or contextual weather soundscapes,
So that my wake-up experience is pleasant, varied, and tailored to the day's outdoor conditions.

**Why this priority**:
Enhances user emotional satisfaction and personalization. Positioned as P3 because offline default tones provide the necessary utility if streaming or weather services are unavailable.

**Independent Test**:
Can be fully tested by selecting "Dynamic Weather Ringtone" on a rainy day, verifying that rainy-day ambient acoustics play, and testing streaming music playback with offline simulation to verify graceful fallback to local sound.

**Acceptance Scenarios**:
1. **Given** a third-party music provider (QQ Music or NetEase Cloud Music) is selected, **When** setting a ringtone, **Then** the user can search online songs, select daily recommended tracks, or assign radar playlists.
2. **Given** an online streaming ringtone is assigned, **When** the alarm triggers without active network connectivity or if the stream buffer fails, **Then** the system immediately and seamlessly falls back to a high-quality local ringtone without delaying the alarm.
3. **Given** "Dynamic Weather Ringtone" is selected, **When** the alarm triggers, **Then** the system detects the local weather condition (Sunny, Overcast, Rain, Snow) and plays corresponding themed acoustic soundscapes.

---

### User Story 6 - Cold Boot / App Wakeup, AOD & Quick Nap Utilities (Priority: P3)

As a user managing busy daily routines,
I want the alarm to trigger even if the app was terminated or the phone was powered off, while having quick nap shortcuts and glanceable AOD indicators,
So that I have absolute peace of mind and frictionless daytime scheduling.

**Why this priority**:
Provides enterprise-grade reliability and convenience for quick daytime power naps and glanceable status checks.

**Independent Test**:
Can be fully tested by setting a Quick Nap alarm for 15 minutes with one tap, killing the app from the recents task manager, and confirming the alarm rings exactly 15 minutes later with TTS speech reciting the nap label.

**Acceptance Scenarios**:
1. **Given** the application has been closed or terminated from memory, **When** the scheduled alarm time arrives, **Then** the system automatically wakes the application and launches the full-screen ringing interface.
2. **Given** the device supports hardware power-off RTC wakeup, **When** an alarm is set and the phone is shut down, **Then** the hardware initiates cold boot 1 to 2 minutes prior to the scheduled time and rings on schedule.
3. **Given** the user opens the Quick Nap screen, **When** tapping a preset duration button (15, 30, 45, or 60 minutes), **Then** a one-off countdown alarm is created and activated instantly with a single tap.
4. **Given** any alarm is saved or toggled on, **When** the operation completes, **Then** a transient toast message appears indicating the precise time delta: "Alarm will ring in X days, Y hours, and Z minutes".
5. **Given** Always-On Display (AOD) is supported and active, **When** the screen is dark, **Then** the next alarm time and icon are displayed, transitioning to a breathing visual pulse when ringing is imminent.
6. **Given** an alarm has a text label assigned and "TTS Voice Readout" is enabled, **When** the alarm rings, **Then** speech synthesis vocalizes the alarm label alongside the ringtone.

---

### Edge Cases

- **Time Zone & Daylight Saving Transition**: What happens when the device crosses time zones or standard time transitions occur? The alarm recalculates its absolute epoch trigger time to preserve local wall-clock hour and minute fidelity.
- **Network Loss During Annual Holiday Refresh**: If the annual State Council holiday calendar update request fails due to server timeout or lack of network, the system preserves cached calendar data, logs a silent retry with exponential backoff, and alerts the user only if the local calendar has expired.
- **Audio Focus Conflict**: If another media application or a phone call is active when the alarm triggers, the alarm audio stream ducks or pauses conflicting media and rings over the call/headset channel according to emergency audio priority policies.
- **Sensor Obstruction in Pocket**: If the phone is inside a bag or pocket, "Pick up to lower volume" or "Flip to mute" must avoid accidental triggering by evaluating multi-sensor fusion (proximity sensor combined with accelerometer orientation).
- **Infinite Snooze Battery Exhaustion**: If snooze is set to infinite and the user does not respond for an extended period, the alarm automatically silences after a safety timeout (e.g., 20 minutes continuous ringing per snooze instance) to avoid thermal throttling and complete battery drain.
- **Deselecting All Dates in Multi-Day Skip**: If a user deselects all dates up to the end of the month, the system clarifies that this turns off the alarm for the remainder of the month while keeping the core recurrence pattern intact.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Statutory Calendar & Workday Scheduling
- **FR-001**: System MUST support a "Statutory Workday" recurring mode that rings on official workdays (Monday through Friday) and automatically rings on designated weekend compensatory workdays (调休补班).
- **FR-002**: System MUST automatically suppress/skip ringing on official statutory holidays (e.g., Spring Festival, National Day, Mid-Autumn Festival), even when they fall on normal weekdays.
- **FR-003**: System MUST automatically synchronize updated statutory holiday and compensatory workday schedules from a trusted calendar data service annually and cache them locally for offline operation.
- **FR-004**: System MUST allow users to view the current year's synchronized holiday/workday calendar within the application settings.

#### Advance Skip & Vacation Management
- **FR-005**: System MUST display an advance notification card 30 to 60 minutes prior to a scheduled alarm offering a single-tap "Skip Today / Dismiss Once" action.
- **FR-006**: Tapping "Skip Today" MUST suppress only the upcoming scheduled occurrence without altering the overall recurring schedule.
- **FR-007**: System MUST provide a "Skip Multiple Days / Vacation Mode" accessible via long-press on any recurring alarm.
- **FR-008**: The multi-day skip interface MUST display a month-based calendar with month and year navigation, highlighting all scheduled ringing dates between the current date and the end of the selected month.
- **FR-009**: The multi-day skip interface MUST allow users to tap highlighted dates to deselect them, and display a secondary confirmation modal detailing the exact dates that will be skipped before persisting changes.
- **FR-010**: Dates not deselected by the user MUST remain scheduled to ring normally.

#### Audio, Haptics & Device Routing
- **FR-011**: System MUST route alarm audio through an independent alarm channel capable of penetrating system-wide silent and Do Not Disturb (DND) modes.
- **FR-012**: System MUST provide a configurable "Volume Crescendo" option that smoothly ramps alarm volume from silent to target volume over a 10 to 30 second window.
- **FR-013**: System MUST provide an "Always Ring via Phone Speaker" toggle that forces audio playback through the device loudspeaker even when wired or Bluetooth headphones are connected.
- **FR-014**: System MUST support selectable linear motor haptic vibration waveforms (including Heartbeat, Wave, Staccato, and Continuous) with an adjustable vibration intensity slider.
- **FR-015**: System MUST support synchronizing the vibration cadence with the tempo and beat of the selected audio track.

#### Ringtones & Streaming
- **FR-016**: System MUST integrate with mainstream online music platforms (QQ Music, NetEase Cloud Music) allowing users to select individual tracks, daily recommendation playlists, or radar playlists as alarm audio.
- **FR-017**: System MUST provide automatic fallback to a local audio file whenever streaming music fails to buffer or device is offline.
- **FR-018**: System MUST support "Dynamic Weather Ringtone" that queries local weather status (sunny, overcast, rainy, snowy) and matches audio soundscapes accordingly.

#### Gestures, Hardware Keys & Anti-Oversleep
- **FR-019**: System MUST detect when the device is flipped face-down during ringing and trigger the configured action (Mute or Snooze).
- **FR-020**: System MUST detect when the ringing device is lifted and automatically reduce the alarm volume to an ambient level.
- **FR-021**: System MUST allow custom mapping of physical hardware keys (Power button, Volume Up, Volume Down) to alarm actions (Dismiss, Snooze, or Mute).
- **FR-022**: System MUST support configurable Snooze intervals (5, 10, 15, 30 minutes, or custom) and maximum snooze repetitions (1, 3, 5 times, or unlimited).
- **FR-023**: System MUST provide an optional Math Challenge requiring users to solve mental arithmetic problems before an alarm can be dismissed.
- **FR-024**: System MUST provide an optional Shake Challenge requiring users to shake the phone a specified number of times before an alarm can be dismissed.

#### Reliability, Background Wakeup & Auxiliary UX
- **FR-025**: System MUST wake the application and present the active alarm interface on time even when the application is not running or has been killed by system memory management.
- **FR-026**: System MUST interface with device hardware RTC power-off wakeup where supported by the OEM platform to boot the device and ring if powered down.
- **FR-027**: System MUST display a transient toast notification showing the exact remaining time delta ("Rings in X days, Y hours, Z minutes") whenever an alarm is saved or toggled active.
- **FR-028**: System MUST provide a Quick Nap interface allowing instant one-tap creation of 15, 30, 45, and 60-minute countdown alarms.
- **FR-029**: System MUST display next alarm timing information on compatible Always-On Display (AOD) surfaces and trigger visual breathing pulses near the alarm time.
- **FR-030**: System MUST allow setting text labels for alarms and support reading the label aloud using text-to-speech (TTS) synthesis during alarm playback.
- **FR-031**: System MUST be optimized to run with minimal battery consumption, low idle memory footprint, and compact installation package size.
- **FR-032**: System architecture MUST decouple core business logic, holiday evaluation, and SQLite persistence from platform-specific APIs via abstract C++ platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`), providing ready extensibility for future iOS application implementations.
- **FR-033**: System MUST support automated end-to-end functional verification on a local Android emulator (API 30+) using an ADB-driven harness that automates UI journeys, simulates sensor/time triggers, and verifies SQLite persistence state.

---

### Key Entities

- **AlarmItem**: Represents a user-configured alarm.
  - Attributes: identifier, title/label, time of day (hour, minute), enabled status, repeat mode (once, day-of-week bitmask, statutory workdays), volume, crescendo duration, vibration waveform type, vibration intensity, audio routing mode (speaker-only vs default), snooze policy id, challenge policy id, ringtone configuration id.
- **HolidayCalendarRule**: Represents official annual holiday and workday arrangements.
  - Attributes: year, date, classification (statutory holiday, compensatory workday, regular weekend, regular weekday), official announcement reference, cached timestamp.
- **SkipRule**: Represents temporary dismissal records.
  - Attributes: alarm identifier, skip type (single next occurrence, multi-day explicit date list), list of skipped calendar dates, expiration timestamp.
- **RingtoneConfig**: Represents audio source settings.
  - Attributes: source type (local preset, user local file, streaming platform track/playlist, dynamic weather soundscape), provider identifier, resource URI, offline fallback resource URI.
- **SnoozePolicy**: Represents snooze behavior configuration.
  - Attributes: interval duration (minutes), remaining repetitions, maximum allowed repetitions, safety auto-silence timeout.
- **ChallengeConfig**: Represents anti-oversleep dismissal constraints.
  - Attributes: challenge type (none, math arithmetic, physical shake), difficulty level / target count, completed status.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of scheduled alarms ring on time within 1 second of the designated minute under standard system sleep and Doze states.
- **SC-002**: Zero missed alarms due to statutory holiday shifts: 100% of statutory holidays are correctly skipped and 100% of compensatory workdays are correctly triggered when using "Statutory Workday" mode.
- **SC-003**: 100% of users can complete setting a Quick Nap alarm in under 5 seconds with 2 taps or fewer from app launch.
- **SC-004**: When streaming audio or dynamic weather soundscapes fail or have no network, fallback to local sound occurs in under 300 milliseconds with zero audible gap or alarm failure.
- **SC-005**: Advance skip and multi-day vacation skip operations maintain 100% schedule integrity without causing subsequent unskipped recurring alarms to fail.
- **SC-006**: Idle background battery consumption attributed to the alarm service remains below 1.5% of total battery drain over a 24-hour monitoring period.
- **SC-007**: Cold start time from user tap to interactive main alarm list remains under 600 milliseconds on target devices (Samsung S25 Ultra, iQOO Z9 Turbo+).
- **SC-008**: User task completion rate for configuring multi-day vacation skip exceeds 95% on the first attempt without user error.
- **SC-009**: 100% of core domain models, holiday calculations, and SQLite data access logic are isolated behind pure C++ interfaces with zero Android runtime dependencies, enabling direct compilation and reuse on iOS.
- **SC-010**: 100% of end-to-end functional journeys (creation, statutory calculation, advance skip, challenge resolution, and persistence) pass successfully in the automated ADB verification suite executed against the local Android emulator.

---

## Assumptions

- **Target OS Baseline**: The application targets Android 11 (API Level 30) and above, ensuring compatibility with modern Android permission architectures (including precise alarm scheduling permissions `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` and notification permissions).
- **Device Vendor Specifics (Samsung One UI & Vivo/iQOO OriginOS)**:
  - Linear motor haptic waveforms leverage standard Android haptic feedback constants and vendor-specific vibration effects where available, with graceful fallback to standard waveforms.
  - Power-off alarm capability relies on device OEM RTC wake broadcast mechanisms; on devices where cold-boot RTC is restricted to pre-installed system apps, the app registers system reboot receivers to trigger immediately upon device boot, while informing users of hardware limitations.
  - AOD integration utilizes standard Android lockscreen/AOD notification surfaces and vendor lockscreen widget APIs.
- **Streaming Music Licensing & SDK Availability**: Online streaming through QQ Music and NetEase Cloud Music utilizes official open API/SDK integrations or system audio provider intents; user account authorization is handled via standard OAuth/App-Link flows.
- **Statutory Calendar Authority**: The annual holiday and compensatory workday dataset adheres to the official State Council (国务院办公厅) annual announcement, synchronized via an HTTPS JSON endpoint with bundled fallback rules updated with app releases.
- **Language & Region**: Primary locale is Simplified Chinese (`zh-CN`), with English fallback for international system settings.
