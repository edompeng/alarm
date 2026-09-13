# Interface Contract: Cross-Platform Adapter Interfaces

**Location**: `core/include/edom/alarm/core/adapter/`
**Contract Type**: Cross-Platform Abstraction Boundary (C++17/20)

---

## 1. Overview

To guarantee clean separation between portable business logic and OS-specific hardware/system capabilities, the core engine communicates with the host operating system exclusively through pure virtual C++ interfaces.
- **Android**: Implemented by native JNI adapters interfacing with Android SDK services (`AlarmManager`, `AudioTrack`, `Vibrator`, `SensorManager`).
- **iOS**: Implemented by Objective-C++ (`.mm`) adapters interfacing with Apple frameworks (`UserNotifications`, `AVFoundation`, `CoreHaptics`, `CoreMotion`).

```
┌─────────────────────────────────────────────────────────────┐
│                 Core Business Engine (C++)                  │
│   (Holiday Engine, Skip Rule Resolver, SQLite Repository)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Abstract Platform Interfaces (C++)
       ┌───────────────────────┼───────────────────────┐
       ▼                       ▼                       ▼
┌──────────────┐        ┌──────────────┐        ┌──────────────┐
│  IScheduler  │        │ IAudioPlayer │        │   IHaptics   │
└──────┬───────┘        └──────┬───────┘        └──────┬───────┘
       │                       │                       │
┌──────┴───────────────────────┴───────────────────────┴──────┐
│                  Platform Implementations                    │
│   Android (JNI / NDK)        │       iOS (Obj-C++ / Swift)  │
│   - AlarmManager             │       - UNUserNotification   │
│   - AudioTrack/MediaPlayer  │       - AVAudioPlayer / DND  │
│   - Vibrator / Waveform      │       - CoreHaptics Engine   │
│   - SensorManager            │       - CoreMotion (CMDevice)│
└──────────────────────────────┴──────────────────────────────┘
```

---

## 2. Abstract Interface Definitions

### 2.1 `IPlatformScheduler`
```cpp
#pragma once

#include <cstdint>
#include <string>

namespace edom::alarm::core {

class IPlatformScheduler {
 public:
  virtual ~IPlatformScheduler() = default;

  // Schedules exact wakeup at trigger_time_ms (UTC epoch ms).
  virtual bool ScheduleExactAlarm(int64_t alarm_id, int64_t trigger_time_ms,
                                  const std::string& title) = 0;

  // Cancels a scheduled exact alarm.
  virtual bool CancelAlarm(int64_t alarm_id) = 0;

  // Schedules advance notification 30-60 minutes prior to alarm.
  virtual bool ScheduleAdvanceNotification(int64_t alarm_id,
                                           int64_t advance_time_ms) = 0;

  // Cancels advance notification.
  virtual bool CancelAdvanceNotification(int64_t alarm_id) = 0;
};

}  // namespace edom::alarm::core
```

### 2.2 `IPlatformAudio`
```cpp
#pragma once

#include <string>

namespace edom::alarm::core {

enum class AudioRoutingMode {
  kSystemDefault = 0,
  kForceSpeaker = 1,
};

class IPlatformAudio {
 public:
  virtual ~IPlatformAudio() = default;

  // Starts ringtone playback with optional audio routing policy.
  virtual bool StartRingtone(const std::string& audio_uri,
                             AudioRoutingMode routing_mode) = 0;

  // Smoothly ramps volume from current to target over duration_ms.
  virtual void RampVolume(float target_volume, int32_t duration_ms) = 0;

  // Immediately mutes or ducks the audio stream.
  virtual void AttenuateVolume(float level) = 0;

  // Stops audio playback.
  virtual void StopRingtone() = 0;
};

}  // namespace edom::alarm::core
```

### 2.3 `IPlatformHaptics`
```cpp
#pragma once

#include <cstdint>

namespace edom::alarm::core {

enum class VibrationPattern {
  kHeartbeat = 0,
  kWave = 1,
  kStaccato = 2,
  kContinuous = 3,
};

class IPlatformHaptics {
 public:
  virtual ~IPlatformHaptics() = default;

  // Starts haptic vibration with pattern and intensity (0.0f - 1.0f).
  virtual bool StartVibration(VibrationPattern pattern, float intensity) = 0;

  // Stops haptic vibration immediately.
  virtual void StopVibration() = 0;
};

}  // namespace edom::alarm::core
```

### 2.4 `IPlatformSensor`
```cpp
#pragma once

namespace edom::alarm::core {

class ISensorObserver {
 public:
  virtual ~ISensorObserver() = default;
  virtual void OnDeviceFlippedFaceDown() = 0;
  virtual void OnDevicePickedUp() = 0;
  virtual void OnShakeProgress(int32_t current_count, int32_t target_count) = 0;
};

class IPlatformSensor {
 public:
  virtual ~IPlatformSensor() = default;

  // Registers sensor listener for active alarm gestures and shake challenges.
  virtual bool StartListening(ISensorObserver* observer, int32_t target_shake_count) = 0;

  // Unregisters sensors to consume 0% idle battery.
  virtual void StopListening() = 0;
};

}  // namespace edom::alarm::core
```

---

## 3. Implementation Matrix

| Capability | Android Implementation | iOS Future Implementation |
| :--- | :--- | :--- |
| **Exact Alarm** | `AlarmManager.setAlarmClock()` | `UNUserNotificationCenter` with critical alert entitlement |
| **Audio Routing** | `AudioManager.setPreferredDevice(BUILTIN_SPEAKER)` | `AVAudioSessionCategoryPlayback` with `.defaultToSpeaker` |
| **Volume Crescendo** | `ValueAnimator` stepping `MediaPlayer` volume | `NSTimer` ramping `AVAudioPlayer.volume` |
| **Haptics** | `Vibrator.vibrate(VibrationEffect)` | `CHHapticEngine` / `UIImpactFeedbackGenerator` |
| **Flip / Pick Up** | `SensorManager` (`TYPE_ACCELEROMETER`, `TYPE_PROXIMITY`) | `CMMotionManager` (`CMAccelerometerData`, device proximity) |
| **Persistence** | Direct SQLite3 via native repository | Direct SQLite3 via native repository (identical C++ code) |
