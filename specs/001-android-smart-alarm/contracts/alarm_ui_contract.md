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

### 2.1 Alarm List Card (`item_alarm_card.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `tv_alarm_time` | `TextView` | Displays formatted 24-hour time `HH:mm` (e.g., `07:30`) in 36sp font |
| `tv_alarm_label` | `TextView` | Displays user-defined label (e.g., "Morning Workout") or empty |
| `tv_alarm_repeat` | `TextView` | Displays recurrence summary ("Once", "Every Day", "Mon, Wed, Fri", "Statutory Workdays") |
| `sw_alarm_enabled` | `Switch` | Instant toggle: updates enabled state in SQLite and triggers `scheduleAlarm()` or `cancelAlarm()` |
| `root_card` | `View` | Single tap opens Edit View; long press displays context menu (Edit, Vacation Mode, Delete) |

### 2.2 Alarm Edit Dialog / Screen (`dialog_alarm_edit.xml`)

| Element ID | View Type | Behavior / Data Binding |
|:---|:---|:---|
| `time_picker` | `TimePicker` | Native Android TimePicker (set to 24-hour mode), initialized with alarm hour and minute |
| `et_alarm_label` | `EditText` | Single-line text input for custom alarm label |
| `rg_repeat_mode` | `RadioGroup` | Mode selector: "Once", "Custom Days", "Statutory Workdays" |
| `layout_days_selector` | `ViewGroup` | 7 toggle chips (Mon through Sun) visible when "Custom Days" is selected |
| `btn_save_alarm` | `Button` | Persists alarm record to SQLite database, recalculates next trigger time, schedules exact alarm, and updates main list |
| `btn_delete_alarm` | `Button` | Visible when editing existing alarm: removes record and cancels active schedule |
| `btn_cancel_alarm` | `Button` | Dismisses dialog without persisting changes |

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
     │                                 Persist to SQLite Repository
     │                                            │
     │                                 Schedule exact AlarmManager
     │                                            │
     ◄───────────────────────────────── Refresh Alarm List Cards
     │
     ├── Taps Card Switch (On/Off) ─────► Toggle is_enabled in SQLite
     │                                            │
     │                                    If ON:  scheduleAlarm()
     │                                    If OFF: cancelAlarm()
     │                                            │
     ◄───────────────────────────────── Show Toast: "Rings in X hrs, Y min"
```
