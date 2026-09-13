# Interface Contract: Alarm Creation & List UI (`AlarmUIContract`)

**Package**: `com.edom.alarm.ui`
**Contract Type**: User Interface & Interaction Specification

---

## 1. Overview

Defines the contract between the UI presentation layer, user interactions, and the underlying alarm repository/scheduler for standard alarm lifecycle management:
1. **Alarm List View**: Displays all configured alarms in a scrollable list, providing instant On/Off toggle switches, quick status indicators, and swipe/long-press actions.
2. **Alarm Creation & Edit View**: Dedicated screen/dialog hosting a native time picker (hour and minute), recurrence mode selection, label editing, and smart feature configuration.

---

## 2. Component Layout & Interaction Contract

### 2.0 Main Dashboard Header (`activity_main.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `tv_app_title` | `TextView` | App title ("Smart Alarm" / "智能闹钟") |
| `btn_settings` | `ImageButton` | Gear icon (⚙️) launching Settings screen / dialog |
| `btn_add_alarm`| `FloatingActionButton` / `Button` | Opens `AlarmEditDialog` |
| `alarm_protection_banner` | `ViewGroup` | Persistent `Alarm protection limited / 闹钟保护受限` state while any enabled alarm lacks exact, notification, or full-screen capability; tapping opens the highest-impact missing system setting |

### 2.1 Alarm List Card (`item_alarm_card.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `tv_alarm_time` | `TextView` | Displays formatted 24-hour time `HH:mm` (e.g., `07:30`) in 36sp font |
| `tv_alarm_label` | `TextView` | Displays user-defined label (e.g., "Morning Workout") or empty |
| `tv_alarm_repeat` | `TextView` | Displays recurrence summary ("Once", "Every Day", "Mon, Wed, Fri", "Statutory Workdays") |
| `tv_alarm_vacation` | `TextView` | Vacation badge ("Vacation (X skipped)"). Clickable: single tap directly opens `VacationCalendarDialog` to inspect/edit |
| `sw_alarm_enabled` | `Switch` | Instant toggle: updates enabled state in SQLite and triggers `scheduleAlarm()` or `cancelAlarm()` |
| `root_card` | `View` | Single tap opens Edit View; long press displays context menu (Edit, Vacation Mode, Delete) |

### 2.2 Alarm Edit Dialog / Screen (`dialog_alarm_edit.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `tp_time` | `TimePicker` | Native Android TimePicker (set to 24-hour mode), initialized with alarm hour and minute |
| `et_alarm_label` | `EditText` | Single-line text input for custom alarm label |
| `rg_repeat_mode` | `RadioGroup` | Mode selector: "Once", "Custom Days", "Statutory Workdays" |
| `layout_custom_days` | `ViewGroup` | 7 toggle chips (Mon through Sun) visible when "Custom Days" is selected |
| `tv_ringtone_name` | `TextView` | Displays current ringtone name (or "Default Alarm Sound") |
| `btn_pick_ringtone`| `Button` | Launches native Android `RingtoneManager.ACTION_RINGTONE_PICKER` (`TYPE_ALARM`) |
| `sw_alarm_vibrate` | `Switch` | Controls `vibrate_enabled` state for this specific alarm |
| `btn_save_alarm` | `Button` | Persists alarm record, delegates registration to the scheduler gateway, updates protection state, and refreshes the main list |
| `btn_delete_alarm` | `Button` | Visible when editing existing alarm: removes record and cancels active schedule |
| `btn_cancel_alarm` | `Button` | Dismisses dialog without persisting changes |

### 2.3 Long-Press Context Menu & Vacation Mode (`dialog_vacation_calendar.xml`)

- **Trigger**:
  - Direct tap on `tv_alarm_vacation` badge on the alarm card, OR
  - Long-press on any alarm card in `AlarmListAdapter` and select "Skip Dates / Vacation Mode".
- **Dialog Features**:
  - **First Column**: Sunday (`Calendar.SUNDAY`).
  - **Header Row**: 7 centered columns: `Sun`, `Mon`, `Tue`, `Wed`, `Thu`, `Fri`, `Sat` (or `日`, `一`, `二`, `三`, `四`, `五`, `六`).
  - **Navigation Controls**: Compact `<` and `>` month navigation buttons (36dp $\times$ 36dp) with center title.
  - **Active Selection**: Pre-populates all existing skipped dates in orange highlights.
  - **Action Buttons**:
    - `btn_cancel_calendar`: Closes dialog without saving.
    - `btn_clear_calendar`: "Clear Skips / 清除跳过" instantly purges all skipped dates, updates card badge, and saves alarm.
    - `btn_save_calendar`: Prompts secondary `SkipConfirmationDialog` and persists modified dates.

### 2.4 Quick Nap Alarms (`btn_nap_15`, `btn_nap_30`, `btn_nap_45`, `btn_nap_60`)

- **Tapping**:
  - Immediately creates an active alarm card with trigger time = `current time + N minutes` (where $N$ is the slot's effective duration).
  - Tagged with `is_quick_nap = 1`, `repeat_mode = Once`, label = `"Quick Nap (Nm)"` or `"Quick Nap (Nh)"`.
  - Scheduled through the capability-aware scheduler gateway (`setAlarmClock()` when exact capability is available; explicit best-effort fallback otherwise).
  - Card displays dynamic remaining countdown badge.
  - **Self-Destruct Lifecycle**: Upon alarm ringing completion, user dismissal, or manual toggle Off, the alarm card is automatically purged and deleted from the list and persistent storage.
- **Long-Pressing**:
  - Opens `dialog_nap_edit` allowing direct modification of that slot's duration value and unit (Minutes / Hours).
  - Updates the dashboard button text dynamically upon confirmation.

---

## 3. Data Flow & State Transitions

```
[Main Screen]
     │
     ├── Taps (+) Button ───────────────► Open Edit View (hour=current+1, min=00, repeat=Once)
     │                                            │
     │                                    User edits & taps Save
     │                                            │
     │                                            ▼
     │                                 Persist to SQLite / SharedPreferences
     │                                            │
     │                                 Register through AlarmSystemScheduler
     │                                 Refresh AlarmProtectionPresenter
     │                                            │
     │                                 Show Toast: "Rings in X hrs, Y min"
     │                                            │
     │                                 Refresh Alarm List Cards
     │
     ├── Taps Quick Nap (15m/30m/...) ──► Calculate time = now + N min
     │                                            │
     │                                 Create active card (is_quick_nap = 1)
     │                                 Register through AlarmSystemScheduler
     │                                 Auto-destruct on dismiss/cancel
     │
     ├── Long-Press Alarm Card ─────────► Context Menu:
     │                                      ├── [Skip Dates] ──► VacationCalendarDialog
     │                                      ├── [Edit]       ──► AlarmEditDialog
     │                                      └── [Delete]     ──► Confirm & Delete
     │
     └── Taps Card Switch (On/Off) ─────► Toggle is_enabled state
                                                  │
                                          If ON:  setAlarmClock()
                                          If OFF: cancelAlarm() & if nap, purge card
```

### Protection Presentation Rules

- The banner is hidden only when no alarm is enabled or `AlarmProtectionLevel` is `FULL` after current capability evaluation **and** a completed reconciliation reports a successful current registration for every enabled alarm.
- The banner cannot be dismissed while protection remains limited.
- Returning from system settings triggers capability re-evaluation and schedule reconciliation before hiding the warning. If capabilities are present but registration failed, the banner offers retry/failure details rather than a permission-settings action.
- The UI MUST distinguish full protection, best-effort permission degradation, and force-stop guidance; it MUST NOT claim that an alarm is protected while the package remains stopped.
