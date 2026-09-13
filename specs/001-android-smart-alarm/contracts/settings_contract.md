# Interface Contract: Settings & Localization (`SettingsContract`)

**Package**: `com.edom.alarm.ui`
**Contract Type**: Application Settings & Configuration Interface

---

## 1. Overview

Defines the contract for global user settings management:
1. **Language Switching**: Real-time interface language selection ("Follow System", "Simplified Chinese", "English").
2. **Advance Notification Preferences**: Toggle pre-alarm notice and configure lead time ($N$ minutes, default 30).
3. **Quick Nap Slot Customization**: Configure value and time unit (minutes vs. hours) across 4 preset slots.
4. **Alarm Protection Guidance**: Explain exact-alarm, notification, full-screen, battery/OEM, and force-stop boundaries without representing guidance as a guarantee.

---

## 2. Component Layout & Interaction Contract

### 2.1 Settings View (`activity_settings.xml` / `dialog_settings.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `spinner_language` / `rg_language` | `Spinner` / `RadioGroup` | Selects locale: "Follow System / 跟随系统", "简体中文", "English". Changing triggers immediate locale reapplication. |
| `sw_advance_notification` | `Switch` | Enables/disables pre-alarm heads-up notifications. |
| `spinner_advance_lead_time` | `Spinner` | Selects lead time: 15m, 30m (Default), 45m, 60m, Custom. |
| `et_nap_slot_1_val`, `sp_nap_slot_1_unit` | `EditText`, `Spinner` | Slot 1 duration value and unit (`MINUTES` / `HOURS`). |
| `et_nap_slot_2_val`, `sp_nap_slot_2_unit` | `EditText`, `Spinner` | Slot 2 duration value and unit (`MINUTES` / `HOURS`). |
| `et_nap_slot_3_val`, `sp_nap_slot_3_unit` | `EditText`, `Spinner` | Slot 3 duration value and unit (`MINUTES` / `HOURS`). |
| `et_nap_slot_4_val`, `sp_nap_slot_4_unit` | `EditText`, `Spinner` | Slot 4 duration value and unit (`MINUTES` / `HOURS`). |
| `et_holiday_sync_url` | `EditText` | Remote holiday JSON configuration URL. |
| `btn_reset_sync_url` | `Button` | Resets sync URL to default GitHub Raw / CDN endpoint. |
| `btn_sync_holidays_now` | `Button` | Triggers immediate manual sync; shows progress, success toast, or error alert modal. |
| `btn_oem_whitelist_guide` | `Button` | Visible on Vivo/iQOO devices; opens OriginOS background high-power & autostart settings. |
| `row_alarm_protection` | `ViewGroup` | Displays `FULL` only after capability and per-alarm registration success; otherwise displays `LIMITED`, missing capabilities, and failed alarm IDs. |
| `btn_alarm_capability_settings` | `Button` | Opens the highest-impact missing system setting, or retries/shows a registration failure when permissions are complete; re-evaluates and reconciles on resume. |
| `btn_save_settings` | `Button` | Persists changes to storage, reschedules advance notifications, updates dashboard nap button labels. |

### 2.2 Dashboard Nap Quick-Edit Dialog (`dialog_nap_edit.xml`)

- **Trigger**: Long-press on any Quick Nap button (`btn_nap_15`, `btn_nap_30`, `btn_nap_45`, `btn_nap_60`) on `MainActivity`.
- **UI Elements**:
  - `et_nap_duration`: Number input.
  - `sp_nap_unit`: Unit selector (Minutes / Hours).
  - `btn_confirm`, `btn_cancel`.
- **Behavior**:
  - On confirm, updates the specific slot in `AppSettings`, updates button text (e.g. `20m` or `1h`), and persists preference.

---

## 3. Dynamic Locale Switcher Service Contract

```java
public interface ILocalizationManager {
    /**
     * Applies the specified locale to the app context.
     * @param languageCode "system", "zh", or "en"
     */
    void setAppLocale(Context context, String languageCode);

    /**
     * Retrieves the currently configured language preference.
     */
    String getCurrentLocale();
}
```

---

## 4. OEM System Permission Guidance Contract (`OemPermissionHelper`)

```java
public class OemPermissionHelper {
    /**
     * Checks if current device is manufactured by Vivo or iQOO (OriginOS / FuntouchOS).
     */
    public static boolean isVivoOrIqoo();

    /**
     * Navigates directly to OriginOS "Allow Background High Power Consumption"
     * or "Autostart Management" settings page.
     */
    public static void openOriginOsBackgroundSettings(Context context);

    /**
     * Requests battery optimization exemption (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
     */
    public static void requestIgnoreBatteryOptimizations(Activity activity);
}
```

---

## 5. Reliability Guidance Rules

- Exact-alarm, notification, and full-screen-alert capability are evaluated from the Android system and are not persisted as preferences.
- When more than one capability is missing, the settings action resolves them in priority order: exact alarm, notifications, then full-screen alert.
- Force-stop guidance states that alarms cannot run while the package remains stopped and that the user must explicitly reopen the application; it does not promise an Intent flag or OEM whitelist can bypass force-stop.
- OEM guidance is additive. It MUST NOT hide the standard Android capability status or replace real Recent Tasks/device verification.
