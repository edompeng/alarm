# Interface Contract: Audio & Haptic Engine (`IAudioHapticController`)

**Package**: `com.edom.alarm.core.audio`
**Contract Type**: Hardware Interfacing & Media Playback Interface

---

## 1. Overview
The `IAudioHapticController` controls high-priority audio playback through `STREAM_ALARM`, speaker routing overrides, volume crescendo fading, and linear motor vibration synchronization.

---

## 2. API Methods

```java
public interface IAudioHapticController {

    enum VibrationPattern {
        HEARTBEAT,
        WAVE,
        STACCATO,
        CONTINUOUS
    }

    /**
     * Starts alarm playback and motor vibration for an active alarm.
     *
     * @param ringtoneUri Audio resource URI (content:// or android.resource://)
     * @param fallbackUri Fallback local audio URI if primary fails
     * @param targetVolume Final volume scale (0 to 100)
     * @param crescendoSeconds Fade-in duration in seconds (0 = immediate full volume)
     * @param forceSpeaker True to force output through built-in loudspeaker even with headphones
     * @param pattern Vibration waveform pattern
     * @param vibrationIntensity Motor intensity scale (0 to 100)
     * @param syncHaptics True to synchronize vibration ticks with audio beats
     */
    void startAlarm(
        String ringtoneUri,
        String fallbackUri,
        int targetVolume,
        int crescendoSeconds,
        boolean forceSpeaker,
        VibrationPattern pattern,
        int vibrationIntensity,
        boolean syncHaptics
    );

    /**
     * Dynamically attenuates volume to a low ambient level.
     * Triggered by sensor fusion when user picks up the ringing device.
     */
    void attenuateVolumeForPickup();

    /**
     * Immediately silences the audio playback and stops vibration.
     */
    void stopAlarm();

    /**
     * Temporarily pauses audio/haptics when user flips phone to snooze.
     */
    void pause();

    /**
     * Vocalizes the alarm text label using Text-To-Speech (TTS).
     *
     * @param label Text to vocalize
     */
    void speakAlarmLabel(String label);

    /**
     * Checks whether the audio playback is currently active.
     */
    boolean isPlaying();
}
```

---

## 3. Hardware Route Specification
- **Audio Stream**: `AudioAttributes.USAGE_ALARM` with `AudioAttributes.CONTENT_TYPE_SONIFICATION`.
- **Loudspeaker Enforcement**: When `forceSpeaker == true`, bind output to `AudioDeviceInfo.TYPE_BUILTIN_SPEAKER`.
- **Volume Crescendo Timing**: Interpolated via linear curve $V(t) = V_{\text{target}} \times \frac{t}{T_{\text{crescendo}}}$, refreshed at $200\text{ ms}$ intervals.
