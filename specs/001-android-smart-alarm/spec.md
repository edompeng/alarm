# Feature Specification: Android Smart Alarm (智能闹钟)

**Feature Branch**: `001-android-smart-alarm`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "帮我设计一款安卓手机闹钟，需要支持安卓11及以上版本，需要在三星S25 ultra和iqoo z9 turbo+手机上包含以下功能：本土化核心特色功能（法定工作日与调休智能识别、本地化在线音乐/流媒体铃声、提前跳过/临时关闭一次、跳过未来N天免打扰）、声音与触感（独立闹钟音量通道、音量渐强、耳机/扬声器智能路由、多样化线性马达振动波形、振动强度调节、音频与振动同步）、交互方式与防赖床机制（手势与传感器交互、小睡设置、计算题/摇晃手机趣味防赖床）、底层硬件与实用细节（关机闹钟、自动唤醒app、倒计时Toast、快速午休、息屏显示AOD联动、闹钟标签管理与TTS）、高稳定性与低资源占用。"

## Clarifications

### Session 2026-09-13
- Q: How should the core architecture decouple platform capabilities so that both Android and a future iOS app can share the business engine? → A: Pure C++ abstract platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`) implemented natively by Android (via JNI) and iOS (via Objective-C++/Swift), maximizing code reuse with zero external runtime overhead.
- Q: How should the end-to-end functional verification be executed and validated on the active local Android emulator? → A: Automated ADB-driven E2E verification script that exercises UI journeys, simulates sensor and alarm events, and validates live SQLite database state on the local Android emulator (`emulator-5554`).
- Q: How should the standard alarm creation and list management interactions be structured in the user interface? → A: Main screen shows a scrollable card list of all created alarms with formatted time (HH:mm), recurrence summary, custom label, and an instant On/Off toggle switch; tapping the Add button (`+`) or an existing alarm opens a dedicated edit screen with standard time picker (hour/minute), recurrence selector, and label field.
- Q: When adding an alarm, what should be the default recurrence mode and behavior if the user only sets the time (hour and minute)? → A: "Ring Once" by default (rings on the next upcoming occurrence of the configured time, then automatically switches to Off after ringing); recurrence (Daily, Custom Days, or Statutory Workdays) is activated only when selected.
- Q: How should long-pressing an alarm card trigger the date skip functionality? → A: Long-pressing an alarm item opens a context menu containing "Skip Dates / Vacation Mode" (跳过日期/休假模式), "Edit Alarm", and "Delete Alarm"; selecting Skip Dates opens a monthly calendar dialog allowing users to browse months and manually toggle/cancel alarm reminders for specific calendar dates, with skipped dates displayed as an active status badge on the card and ringing suppressed on those dates.
- Q: What underlying system mechanism should be used to wake up the phone, play audio, and present the full-screen alert when the alarm time arrives? → A: Use Android's `AlarmManager.setAlarmClock()` with an explicit immutable broadcast PendingIntent targeting `AlarmTriggerReceiver` and a separate `MainActivity` show intent. The receiver validates and atomically claims the persisted occurrence, then hands audio, haptics, and bounded wake-lock ownership to a foreground ringing service. Its `CATEGORY_ALARM` notification may present `RingingActivity` via full-screen intent when permitted; the Activity is presentation, not the only ringing owner.
- Q: How should Quick Nap countdown alarms be represented in the alarm list and handled across their lifecycle? → A: Tapping a Quick Nap button (15m, 30m, 45m, 60m) immediately creates an active countdown alarm card in the main list displaying its target wake-up time, an exclusive Quick Nap badge, and dynamic remaining duration; once the nap alarm finishes ringing, is dismissed by the user, or is manually toggled off, it automatically destroys and removes itself from the list without leaving residual inactive records.
- Q: How should individual ringtone and vibration settings be configured per alarm? → A: In AlarmEditDialog, each alarm provides dedicated rows for ringtone and vibration; tapping Ringtone invokes the native Android system RingtoneManager (TYPE_ALARM) allowing users to select standard system alarm tones, while Vibration provides an independent toggle switch for enabling or disabling tactile alerts, storing the selected ringtone URI and vibration preference independently for each alarm.

### Session 2026-09-14
- Q: How should the application ensure alarms reliably trigger and wake the screen even after the user manually swipes away or closes the app from the Recent Tasks list? → A: Persist the intended occurrence, register one OS-level broadcast alarm, and let `AlarmTriggerReceiver` recreate the process and hand off to the bounded ringing service. The notification presents full-screen UI only when permitted. No standing service or polling is used before trigger, so Recent Tasks removal does not remove the authoritative OS registration.
- Q: How should the new Settings screen and in-app language switching (Chinese/English) be accessed and applied across the application? → A: Dedicated Settings screen opened via a header button (⚙️), providing a language selector ("Follow System / 跟随系统", "简体中文", "English") that dynamically reapplies the app locale and refreshes all active UI components immediately.
- Q: When the user taps the quick-dismiss action in the advance notification ("Upcoming Alarm in N minutes"), how should that dismissal affect the alarm schedule? → A: Single-occurrence skip: Suppress only today's trigger; recurring alarms (Statutory Workdays, Custom Days) remain enabled and advance to the next cycle, while one-time alarms are marked disabled, preserving recurring routines.
- Q: Where and how should users configure the values and units (minutes vs. hours) for the four Quick Nap preset slots? → A: Dual access: Configurable in the global Settings screen (all 4 slots with number input + minutes/hours unit selector) and directly accessible via long-press on any dashboard nap button, updating dashboard labels (e.g. 15m, 1h).
- Q: What configuration controls should be provided in the Settings screen for setting the advance notification time window (N minutes before ringing)? → A: A dedicated setting row with an advance notification toggle (default: On) and an interval selector with standard presets ("15 minutes", "30 minutes (Default)", "45 minutes", "60 minutes", and "Custom...").
- Q: When an automatic background holiday sync attempt fails during app launch, how should the failure be communicated to the user? → A: Display a modal error alert dialog ("Update Failed / 更新失败") when a user manually triggers sync in Settings; keep automatic startup sync completely silent, preserving existing cached rules and silently scheduling a retry on the next app launch to prevent morning UX interruption.
- Q: How should the user view and modify an alarm's active skip configuration? → A: Dual entry points: Tapping the "Vacation (X skipped)" badge directly on the alarm card or selecting "Skip Dates / Vacation Mode" from the long-press context menu opens VacationCalendarDialog pre-populated with currently skipped dates, allowing users to toggle dates, save updates, or tap a dedicated "Clear Skips" button to purge all skips at once.
- Q: What default URL and editing controls should be provided in Settings for the statutory holiday synchronization endpoint? → A: An editable URL text field in Settings pre-filled with a default GitHub raw / CDN endpoint hosting official holiday JSON (formatted as { "year": 2026, "holidays": [...], "workdays": [...] }), including a "Reset to Default" button and an immediate "Sync Now / 立即同步" action button.
- Q: Where should the holiday configuration generation script output the generated files, and how should it verify if next year's schedule is officially announced? → A: Implement `scripts/generate_holiday_config.py` to query official/open holiday feeds with fallback; verify if next year's State Council announcement is gazetted (omitting next year if unannounced); output standard JSON to both `core/src/assets/holidays_<YEAR>.json` and an export distribution directory.
- Q: For iQOO and Vivo (OriginOS) devices, what wakeup strategy and system permission guidance should the app implement to guarantee alarms ring after the app is closed? → A: Use the same canonical broadcast-alarm pipeline for ordinary backgrounding, Recent Tasks removal, and process death; detect Vivo/iQOO devices and guide users to enable "Allow Background High Power Consumption" (允许后台高耗电) and "Autostart" (自启动). This later clarification supersedes any direct-Activity design. Android package force-stop and equivalent OEM deep-stop are separate lifecycle states governed by FR-049 and cannot be bypassed by intent flags.
- Q: Which app-exit scope must still guarantee that the alarm rings and presents its alert? → A: Level C was initially requested, covering normal backgrounding, removal from Recent Tasks, system process reclamation, Android Settings "Force stop", and equivalent OEM deep-stop actions; the force-stop portion is subject to the explicit platform-limitation rule clarified below.
- Q: If Android or an OEM cancels all pending alarms after force-stop, which product acceptance rule should apply? → A: Accept the platform limitation: warn the user that alarms cannot trigger while the application remains force-stopped and require the user to reopen the application before alarm delivery is guaranteed again.
- Q: May users enable an alarm when exact-alarm or full-screen-alert permission is unavailable or revoked? → A: Yes. Missing either permission must not block alarm activation; the application uses the system capabilities still available and delivers the alarm on a best-effort basis.
- Q: How should the application communicate missed-alarm risk while permission limitations force best-effort delivery? → A: Keep a persistent "Alarm protection limited / 闹钟保护受限" warning banner or status indicator on the main screen with a one-tap permission-settings action; remove it automatically when all required capabilities are restored.

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
3. **Given** the device has network connectivity, **When** new annual holiday/workday schedules are officially released, **Then** the system automatically synchronizes and updates the local holiday calendar rules on app launch (retrying on subsequent launches upon failure, or waiting 7 days after a successful sync) or on demand via Settings.
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
1. **Given** an upcoming alarm scheduled within N minutes (configurable in Settings, defaulting to 30 minutes), **When** the user views the lockscreen or notification tray, **Then** an advance notification appears showing "Upcoming alarm at HH:mm" with a prominent "Dismiss / 快捷关闭" action button.
2. **Given** the user taps "Dismiss / 快捷关闭" on the advance notification, **When** the scheduled time arrives, **Then** the alarm remains silent for that specific instance, dismissed notification clears, and recurring alarms automatically reset their trigger for the subsequent scheduled cycle (or disable for one-time alarms).
3. **Given** a recurring alarm, **When** the user long-presses the alarm item and selects "Skip Multiple Days / Vacation Mode", **Then** a calendar dialog opens with Sunday as the first column, displaying a day-of-week header ("Sun, Mon, Tue, Wed, Thu, Fri, Sat" / "日 一 二 三 四 五 六") and scaled month navigation buttons that do not overlap or get clipped.
4. **Given** the multi-day calendar view is displayed, **When** the user taps on dates to toggle them and clicks Save, **Then** a secondary confirmation dialog lists the exact dates to be skipped.
5. **Given** the user confirms the secondary prompt, **When** the skipped dates arrive, **Then** the alarm does not ring on those dates, but rings normally on all non-skipped scheduled dates.
6. **Given** an alarm already has skipped dates set, **When** the user taps the `Vacation (X skipped)` badge on the alarm card directly or selects "Skip Dates / Vacation Mode" from the long-press context menu, **Then** `VacationCalendarDialog` opens with all previously skipped dates highlighted, allowing the user to view, modify (toggle dates), or tap "Clear Skips / 清除跳过" to purge all skips at once.

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

> **Increment scope note (2026-09-14)**: The current application-exit reliability plan covers scenarios 1, 2, 8, and 9 and FR-025/FR-049–FR-051. Hardware power-off RTC wakeup in scenario 3 / FR-026 remains a separate OEM-capability feature; this increment does not claim pre-unlock or powered-off delivery.

As a user managing busy daily routines,
I want the alarm to trigger even if the app was terminated or the phone was powered off, while having quick nap shortcuts and glanceable AOD indicators,
So that I have absolute peace of mind and frictionless daytime scheduling.

**Why this priority**:
Provides enterprise-grade reliability and convenience for quick daytime power naps and glanceable status checks.

**Independent Test**:
Can be fully tested by setting a Quick Nap alarm, separately exercising normal backgrounding, removal from Recent Tasks, and system process reclamation, and confirming the alarm rings at the configured time with its full-screen alert and TTS label. Force-stop and equivalent OEM deep-stop are tested separately by verifying that the limitation warning is present and that reopening the app restores the alarm schedule.

**Acceptance Scenarios**:
1. **Given** the application is backgrounded, removed from Recent Tasks, or killed by system process reclamation (including on Vivo/iQOO OriginOS), **When** the scheduled alarm time arrives, **Then** the device wakes and presents `RingingActivity` over the lockscreen at the configured time without requiring the user to reopen the application.
2. **Given** Android Settings "Force stop" or an equivalent OEM deep-stop action places the package in a stopped state, **When** the user reviews alarm-reliability guidance or next reopens the application, **Then** the application clearly warns that alarms cannot trigger while force-stopped and reschedules all enabled alarms after reopening restores execution eligibility.
3. **Given** the device supports hardware power-off RTC wakeup, **When** an alarm is set and the phone is shut down, **Then** the hardware initiates cold boot 1 to 2 minutes prior to the scheduled time and rings on schedule.
4. **Given** the user views the Quick Nap section on the main dashboard, **When** tapping a preset duration button, **Then** a one-off countdown alarm is created and activated instantly with a single tap, using the user-configured duration and unit (minutes or hours).
5. **Given** any alarm is saved or toggled on, **When** the operation completes, **Then** a transient toast message appears indicating the precise time delta: "Alarm will ring in X days, Y hours, and Z minutes".
6. **Given** Always-On Display (AOD) is supported and active, **When** the screen is dark, **Then** the next alarm time and icon are displayed, transitioning to a breathing visual pulse when ringing is imminent.
7. **Given** an alarm has a text label assigned and "TTS Voice Readout" is enabled, **When** the alarm rings, **Then** speech synthesis vocalizes the alarm label alongside the ringtone.
8. **Given** exact-alarm permission, full-screen-alert permission, or both are unavailable or revoked, **When** the user enables an alarm, **Then** the application accepts the alarm and attempts delivery using the system capabilities still available instead of blocking activation.
9. **Given** an enabled alarm is operating with limited exact-alarm or full-screen-alert capability, **When** the user views the main screen, **Then** a persistent "Alarm protection limited / 闹钟保护受限" warning and one-tap permission-settings action remain visible until all required capabilities are restored.

---

### User Story 7 - Application Settings, Language Switching & Notification Preferences (Priority: P2)

As an international or bilingual user,
I want a dedicated Settings screen to switch the interface language (Chinese/English), configure pre-alarm advance notification timing, and customize the 4 Quick Nap slots,
So that the application accommodates my native language, waking preferences, and daytime rest habits.

**Why this priority**:
Settings provide the central control hub for localization, notification frequency, and daytime nap customization, directly resolving user-reported workflow friction.

**Independent Test**:
Can be fully tested by opening the Settings screen via the header button (⚙️), changing the language to English (verifying immediate interface update without reboot), setting advance notification lead time to 30 minutes, customizing Quick Nap Slot 3 to "1 hour", and confirming the dashboard button reflects "1h".

**Acceptance Scenarios**:
1. **Given** the user is on the main screen, **When** tapping the Settings button (⚙️) in the header, **Then** the application opens a dedicated Settings screen displaying Language, Advance Notification, and Quick Nap Presets.
2. **Given** the user changes language between "Follow System / 跟随系统", "简体中文", and "English", **When** an option is selected, **Then** the application immediately applies the chosen locale to all screens, dialogs, and cards without requiring a device restart.
3. **Given** the user configures the Advance Notification window (default 30 minutes), **When** an alarm is within N minutes of ringing, **Then** an advance notification appears in the status bar with a "Dismiss / 快捷关闭" button that suppresses only that single upcoming instance without disrupting recurring schedules.
4. **Given** the user configures Quick Nap presets in Settings (or via long-press on dashboard buttons), **When** changing a slot value or unit (minutes/hours), **Then** the dashboard buttons immediately update their labels and durations.
5. **Given** the user configures the Statutory Holiday Sync URL in Settings, **When** tapping "Sync Now / 立即同步", **Then** the system requests the latest holiday configuration JSON from the configured URL; if the request succeeds, it updates local holiday rules and displays a success message; if the request fails, it discards changes, preserves existing rules, and presents a modal error dialog ("Update Failed / 更新失败").
6. **Given** automatic holiday sync is enabled, **When** the application is launched, **Then** it evaluates the sync schedule: executing an automatic sync on the first app launch, silently retrying on subsequent launches if the previous attempt failed, or waiting 7 days after a successful sync before initiating the next automatic update on launch.

---

### Edge Cases

- **Time Zone & Daylight Saving Transition**: What happens when the device crosses time zones or standard time transitions occur? The alarm recalculates its absolute epoch trigger time to preserve local wall-clock hour and minute fidelity.
- **Network Loss or Invalid Format During Holiday Sync**: If the remote holiday JSON request fails (network timeout, invalid JSON, or server 404/500), the system preserves existing local cached calendar rules with zero corruption. On manual sync, an error modal dialog informs the user of failure; on automatic startup sync, the failure is handled silently and scheduled to retry on the next app cold start.
- **Audio Focus Conflict**: If another media application or a phone call is active when the alarm triggers, the alarm audio stream ducks or pauses conflicting media and rings over the call/headset channel according to emergency audio priority policies.
- **Sensor Obstruction in Pocket**: If the phone is inside a bag or pocket, "Pick up to lower volume" or "Flip to mute" must avoid accidental triggering by evaluating multi-sensor fusion (proximity sensor combined with accelerometer orientation).
- **Infinite Snooze Battery Exhaustion**: If snooze is set to infinite and the user does not respond for an extended period, the alarm automatically silences after a safety timeout (e.g., 20 minutes continuous ringing per snooze instance) to avoid thermal throttling and complete battery drain.
- **Deselecting All Dates in Multi-Day Skip**: If a user deselects all dates up to the end of the month, the system clarifies that this turns off the alarm for the remainder of the month while keeping the core recurrence pattern intact.
- **Forced-Stop Package State**: Android Settings "Force stop" and equivalent OEM deep-stop actions can cancel or suppress pending alarms. The application does not claim delivery while it remains force-stopped; it must warn the user of this limitation and restore all enabled schedules when the user reopens it. Recent Tasks removal remains a distinct, fully supported state.
- **Missing or Revoked Alarm Permissions**: If exact-alarm or full-screen-alert permission is unavailable or revoked, alarm activation remains allowed. Delivery is best-effort: timing can be inexact and the alert may be limited to the notification surfaces permitted by the system. The main screen must retain a visible limited-protection warning and permission-settings action until capability is restored.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Statutory Calendar & Workday Scheduling
- **FR-001**: System MUST support a "Statutory Workday" recurring mode that rings on official workdays (Monday through Friday) and automatically rings on designated weekend compensatory workdays (调休补班).
- **FR-002**: System MUST automatically suppress/skip ringing on official statutory holidays (e.g., Spring Festival, National Day, Mid-Autumn Festival), even when they fall on normal weekdays.
- **FR-003**: System MUST automatically synchronize updated statutory holiday and compensatory workday schedules from a trusted calendar data service annually and cache them locally for offline operation.
- **FR-004**: System MUST allow users to view the current year's synchronized holiday/workday calendar within the application settings.

#### Advance Skip & Vacation Management
- **FR-005**: System MUST display an advance notification card N minutes prior to a scheduled alarm (configurable in Settings, defaulting to 30 minutes) offering a single-tap "Dismiss / 快捷关闭" action.
- **FR-006**: Tapping "Dismiss / 快捷关闭" MUST suppress only the upcoming scheduled occurrence without altering the overall recurring schedule (or disabling one-time alarms).
- **FR-007**: System MUST provide a "Skip Multiple Days / Vacation Mode" accessible via long-press on any recurring alarm.
- **FR-008**: The multi-day skip interface MUST display a month-based calendar with Sunday as the first column (Sun–Sat), a visible day-of-week header ("Sun, Mon, Tue, Wed, Thu, Fri, Sat" / "日 一 二 三 四 五 六"), scaled non-overlapping `<` and `>` month navigation buttons, and highlight all scheduled ringing dates between the current date and the end of the selected month.
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

- **FR-025**: When the required exact-alarm and full-screen-alert capabilities are available, the system MUST wake the device and present the active alarm interface on time without requiring the user to reopen the application when the application is in the background, has been killed by system memory management, or has been removed from Recent Tasks (including on Vivo/iQOO OriginOS). Recent Tasks removal and Android package force-stop MUST be treated as distinct lifecycle states.
- **FR-026** *(deferred from the current application-exit reliability increment)*: System MUST interface with device hardware RTC power-off wakeup where a documented OEM interface is available to third-party applications. This future capability requires its own device-protected occurrence store and powered-off/pre-unlock device validation; `BOOT_COMPLETED` recovery from the current credential-protected store does not satisfy it.
- **FR-027**: System MUST display a transient toast notification showing the exact remaining time delta ("Rings in X days, Y hours, Z minutes") whenever an alarm is saved or toggled active.
- **FR-028**: System MUST provide a Quick Nap interface allowing instant one-tap creation of countdown alarms across 4 user-customizable preset slots (defaulting to 15m, 30m, 45m, 60m; user-configurable in value and unit: minutes or hours).
- **FR-029**: System MUST display next alarm timing information on compatible Always-On Display (AOD) surfaces and trigger visual breathing pulses near the alarm time.
- **FR-030**: System MUST allow setting text labels for alarms and support reading the label aloud using text-to-speech (TTS) synthesis during alarm playback.
- **FR-031**: System MUST be optimized to run with minimal battery consumption, low idle memory footprint, and compact installation package size.
- **FR-032**: System architecture MUST decouple core business logic, holiday evaluation, and SQLite persistence from platform-specific APIs via abstract C++ platform interfaces (`IPlatformScheduler`, `IPlatformAudio`, `IPlatformHaptics`, `IPlatformSensor`), providing ready extensibility for future iOS application implementations.
- **FR-033**: System MUST support automated end-to-end functional verification on a local Android emulator (API 30+) using an ADB-driven harness that automates UI journeys, simulates sensor/time triggers, and verifies SQLite persistence state.
- **FR-034**: System MUST display all configured alarms in a scrollable list on the main screen, where each alarm card presents the formatted trigger time (HH:mm), recurrence summary, custom label, and an instant On/Off toggle switch.
- **FR-035**: System MUST provide a dedicated alarm creation and editing interface featuring a standard time picker for hour and minute selection, recurrence configuration (once, specific days of week, or statutory workdays), label text entry, and delete capability.
- **FR-036**: System MUST default new alarms to "Ring Once" mode when no repeat days are selected; upon completing ringing or dismissal of a "Ring Once" alarm, the system MUST automatically transition the alarm's state to disabled (Off).
- **FR-037**: System MUST provide a dedicated Application Settings screen accessible via a header icon (⚙️) on the main dashboard, offering controls for language selection, advance notification timing, and Quick Nap presets.
- **FR-038**: System MUST support in-app dynamic language switching between "Follow System / 跟随系统", "简体中文 (Simplified Chinese)", and "English", immediately updating all screens, dialogs, and notifications without requiring a device restart.
- **FR-039**: System MUST allow users to customize the 4 Quick Nap slots (both numerical duration and unit: minutes or hours) from the Settings screen or via long-pressing any Quick Nap dashboard button, updating dashboard labels and countdown durations dynamically.
- **FR-040**: System MUST provide a configurable advance notification lead time (toggleable On/Off, with options for 15, 30 [Default], 45, 60 minutes, or custom) in Settings, displaying a heads-up status bar notification prior to scheduled alarms.
- **FR-041**: Advance notification MUST include a prominent "Dismiss / 快捷关闭" action that suppresses only the upcoming ringing instance; recurring alarms MUST remain enabled and automatically advance to the next cycle, while one-time alarms are marked disabled.
- **FR-042**: The vacation mode monthly calendar layout MUST display Sunday in the first column (`Sun`–`Sat`), show clear day-of-week header labels ("Sun, Mon, Tue, Wed, Thu, Fri, Sat" / "日 一 二 三 四 五 六"), and scale month navigation buttons (`<`, `>`) to eliminate visual clipping or overlap.
- **FR-043**: Settings MUST provide a Statutory Holiday Sync section featuring an editable URL text field for the remote holiday configuration endpoint, pre-populated with a valid working default CDN/GitHub raw URL, a "Reset to Default" button, and a manual "Sync Now / 立即同步" action button.
- **FR-044**: When manual holiday sync is triggered, the system MUST issue an HTTP GET request to the configured URL; upon success, it MUST parse, validate, and update the local active holiday/workday rules and display a success confirmation; upon failure (network error, timeout, or schema mismatch), it MUST abort the update, preserve existing cached rules, and present a modal error dialog ("Update Failed / 更新失败").
- **FR-045**: System MUST execute automatic holiday synchronization upon cold application launch: performing a sync attempt on first launch, silently retrying on subsequent launches if the previous attempt failed, and waiting at least 7 days after a successful sync before initiating the next automatic check on launch. Automatic sync failures MUST be completely silent without blocking modal popups.
- **FR-046**: System MUST support viewing and modifying an alarm's active skip configuration: tapping the `Vacation (X skipped)` badge on the alarm card directly or choosing "Skip Dates / Vacation Mode" from the long-press menu MUST open `VacationCalendarDialog` pre-populated with all currently skipped dates, allowing users to toggle dates, save changes, or tap a dedicated "Clear Skips / 清除跳过" button to remove all skips at once.
- **FR-047**: System repository MUST provide a script `scripts/generate_holiday_config.py` that queries authoritative/open Chinese holiday announcement feeds with fallback, checks whether the State Council holiday arrangements for the following calendar year have been gazetted (omitting the next year if not yet published), and outputs standardized JSON configuration files matching `{ "year": YYYY, "holidays": [...], "workdays": [...] }` into `core/src/assets/holidays_<YEAR>.json` and an export distribution folder.
- **FR-048**: System MUST detect Vivo and iQOO devices (`Build.MANUFACTURER.equalsIgnoreCase("vivo")`) and provide a one-tap system guidance banner/dialog in Settings and initial startup directing users to OriginOS "Allow Background High Power Consumption" (允许后台高耗电), "Autostart" (自启动), and Battery Optimization whitelist settings.
- **FR-049**: System MUST clearly warn users that scheduled alarms cannot be guaranteed while the application is in the Android package stopped state after Settings "Force stop" or an equivalent OEM deep-stop action. On the first subsequent explicit application launch, the system MUST reconcile every enabled alarm and MUST report full protection only if every current future occurrence registers successfully. A persisted one-time or Quick Nap occurrence already due before relaunch MUST NOT be silently shifted to tomorrow: the one-time alarm is marked missed and disabled, the expired Quick Nap is marked missed and removed, and recurring alarms advance to their next future occurrence.
- **FR-050**: System MUST allow users to enable alarms even when exact-alarm, notification, or full-screen-alert permission/capability is unavailable. If exact permission is already unavailable while the app is running, the system MUST immediately attempt best-effort registration. If exact permission is revoked externally and Android stops the package and removes exact alarms, fallback reconciliation is required on the next explicit resume/relaunch; autonomous conversion while the package remains stopped is not promised. In either case, delivery uses only remaining platform capabilities and MUST NOT be represented as satisfying FR-025.
- **FR-051**: While any enabled alarm lacks exact-alarm, notification, or full-screen-alert capability, or while reconciliation is incomplete or any current enabled occurrence is not registered, the main screen MUST continuously display an "Alarm protection limited / 闹钟保护受限" warning banner or status indicator with a one-tap corrective action. The warning MUST disappear automatically only after all required capabilities are available and every enabled alarm's current future occurrence has registered successfully.

---

### Key Entities

- **AlarmItem**: Represents a user-configured alarm.
  - Attributes: identifier, title/label, time of day (hour, minute), enabled status, repeat mode (once, day-of-week bitmask, statutory workdays), volume, crescendo duration, vibration waveform type, vibration intensity, audio routing mode (speaker-only vs default), snooze policy id, challenge policy id, ringtone configuration id.
- **HolidayCalendarRule**: Represents official annual holiday and workday arrangements.
  - Attributes: year, date, classification (statutory holiday, compensatory workday, regular weekend, regular weekday), official announcement reference, cached timestamp, source URL.
- **SkipRule**: Represents temporary dismissal records.
  - Attributes: alarm identifier, skip type (single next occurrence, multi-day explicit date list), list of skipped calendar dates, expiration timestamp; supports inspection, date addition/removal, and full clearance.
- **RingtoneConfig**: Represents audio source settings.
  - Attributes: source type (local preset, user local file, streaming platform track/playlist, dynamic weather soundscape), provider identifier, resource URI, offline fallback resource URI.
- **SnoozePolicy**: Represents snooze behavior configuration.
  - Attributes: interval duration (minutes), remaining repetitions, maximum allowed repetitions, safety auto-silence timeout.
- **ChallengeConfig**: Represents anti-oversleep dismissal constraints.
  - Attributes: challenge type (none, math arithmetic, physical shake), difficulty level / target count, completed status.
- **AppSettings**: Represents global user preferences and configuration settings.
  - Attributes: selected language / locale ("system", "zh-CN", "en-US"), advance notification enabled (boolean), advance notification window (minutes, default 30), quick nap slot 1 (duration, unit: minutes/hours), quick nap slot 2 (duration, unit: minutes/hours), quick nap slot 3 (duration, unit: minutes/hours), quick nap slot 4 (duration, unit: minutes/hours), holiday sync URL (string), last holiday sync timestamp (epoch millis), last holiday sync status (boolean), oem whitelist guided (boolean).
- **AlarmOccurrence**: Durable delivery identity for the next intended occurrence of one enabled alarm.
  - Attributes: alarm identifier, generation, occurrence identifier, intended trigger epoch, last claimed occurrence identifier, and missed status. It is updated before AlarmManager registration so receivers can reject stale or duplicate PendingIntents after process recreation.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With the exact-alarm and full-screen-alert capabilities required by FR-025 available, 100% of scheduled alarms ring on time within 1 second of the designated minute under standard system sleep and Doze states and after each supported exit state: backgrounding, Recent Tasks removal, and system process reclamation.
- **SC-002**: Zero missed alarms due to statutory holiday shifts: 100% of statutory holidays are correctly skipped and 100% of compensatory workdays are correctly triggered when using "Statutory Workday" mode.
- **SC-003**: 100% of users can complete setting a Quick Nap alarm in under 5 seconds with 2 taps or fewer from app launch.
- **SC-004**: When streaming audio or dynamic weather soundscapes fail or have no network, fallback to local sound occurs in under 300 milliseconds with zero audible gap or alarm failure.
- **SC-005**: Advance skip and multi-day vacation skip operations maintain 100% schedule integrity without causing subsequent unskipped recurring alarms to fail.
- **SC-006**: Idle background battery consumption attributed to the alarm service remains below 1.5% of total battery drain over a 24-hour monitoring period.
- **SC-007**: Cold start time from user tap to interactive main alarm list remains under 600 milliseconds on target devices (Samsung S25 Ultra, iQOO Z9 Turbo+).
- **SC-008**: User task completion rate for configuring multi-day vacation skip exceeds 95% on the first attempt without user error.
- **SC-009**: 100% of core domain models, holiday calculations, and SQLite data access logic are isolated behind pure C++ interfaces with zero Android runtime dependencies, enabling direct compilation and reuse on iOS.
- **SC-010**: 100% of end-to-end functional journeys (creation, statutory calculation, advance skip, challenge resolution, and persistence) pass successfully in the automated ADB verification suite executed against the local Android emulator.
- **SC-011**: 100% of newly created alarms appear immediately in the main alarm list in chronological order, and toggling an alarm's On/Off switch updates its system schedule state in under 100 milliseconds.
- **SC-012**: In 100% of force-stop and equivalent OEM deep-stop test cases, the application communicates that alarm delivery is unavailable while stopped and, on the first subsequent explicit launch, either registers the current future occurrence of every enabled alarm or reports limited protection with the failed alarm IDs. Expired one-time/Quick Nap occurrences follow FR-049 rather than being moved to tomorrow.
- **SC-013**: In 100% of exact-alarm, notification, and full-screen-alert denial tests, users can still enable an alarm and the application attempts the remaining system-supported paths without crashing. External exact-permission revocation is reconciled on the next explicit resume/relaunch because the stopped package cannot convert canceled alarms autonomously. The main screen reports limited protection with a working corrective action until capabilities and all registrations are restored.

---

## Assumptions

- **Target OS Baseline**: The application targets Android 11 (API Level 30) and above. This build uses the user-granted `SCHEDULE_EXACT_ALARM` model on API 31+ (not `USE_EXACT_ALARM`) plus the applicable notification and full-screen controls.
- **Device Vendor Specifics (Samsung One UI & Vivo/iQOO OriginOS)**:
  - Linear motor haptic waveforms leverage standard Android haptic feedback constants and vendor-specific vibration effects where available, with graceful fallback to standard waveforms.
  - On Vivo/iQOO OriginOS devices (e.g. iQOO Z9 Turbo+), Recent Tasks removal, OEM deep cleanup, and Android package force-stop MUST be treated as distinct states. The application pairs its supported alarm delivery mechanism with a one-tap system guide to enable "Allow Background High Power Consumption" (允许后台高耗电) and "Autostart" (自启动). Delivery while the package remains force-stopped or equivalently deep-stopped is an explicit platform limitation governed by FR-049 rather than an FR-025 guarantee.
  - Power-off alarm capability is a deferred OEM-specific feature. It may be implemented only where a documented third-party RTC interface exists, with device-protected occurrence storage and powered-off/pre-unlock evidence; ordinary `BOOT_COMPLETED` registration is not represented as equivalent.
  - AOD integration utilizes standard Android lockscreen/AOD notification surfaces and vendor lockscreen widget APIs.
- **Streaming Music Licensing & SDK Availability**: Online streaming through QQ Music and NetEase Cloud Music utilizes official open API/SDK integrations or system audio provider intents; user account authorization is handled via standard OAuth/App-Link flows.
- **Statutory Calendar Authority**: The annual holiday and compensatory workday dataset adheres to the official State Council (国务院办公厅) annual announcement, synchronized via an HTTPS JSON endpoint with bundled fallback rules updated with app releases.
- **Language & Region**: Primary locale is Simplified Chinese (`zh-CN`), with English fallback for international system settings.
