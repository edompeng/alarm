# Interface Contract: Android Alarm Delivery & Protection

**Package**: `com.edom.alarm.core.scheduler` and `com.edom.alarm.ui`
**Contract Type**: Android framework integration, recovery, and user-visible capability state
**Requirements**: FR-025, FR-049, FR-050, FR-051; SC-001, SC-012, SC-013

---

## 1. Purpose and Boundaries

This contract defines one authoritative path from a persisted enabled alarm to system registration, process recreation, ringing execution, optional full-screen UI, cancellation, and next-occurrence reconciliation.

Supported autonomous-delivery states are ordinary backgrounding, OS process reclamation, Doze, and Recent Tasks removal. Android Settings force-stop and equivalent OEM deep-stop are explicit unsupported runtime states: the package does not claim delivery until the user explicitly reopens it and reconciliation succeeds.

---

## 2. Collaborator Contracts

### `AlarmCapabilityEvaluator`

```java
AlarmCapabilitySnapshot evaluate(Context context);
Intent createSettingsIntent(Context context, MissingCapability capability);
```

`AlarmCapabilitySnapshot` contains independent booleans for exact-alarm, notification, and full-screen-alert capability. It is runtime state and MUST NOT be persisted as durable truth. Final protection level also consumes the latest `ReconcileReport`; capability booleans alone cannot produce `FULL`.

### `AlarmSystemScheduler`

```java
ScheduleResult schedule(long alarmId, String occurrenceId, long triggerAtMs);
void cancel(long alarmId);
```

`ScheduleResult` reports:

| Mode | Meaning |
|:---|:---|
| `EXACT_ALARM_CLOCK` | Registered with `setAlarmClock()`; exact capability was available |
| `BEST_EFFORT_IDLE_ALLOWED` | Registered with `setAndAllowWhileIdle()` because exact capability was unavailable |
| `NOT_REGISTERED` | Invalid/disabled/no-future occurrence or framework registration failure |

The scheduler owns all AlarmManager and PendingIntent construction. Callers MUST NOT construct competing alarm operations.

### `AlarmScheduleReconciler`

```java
ReconcileReport reconcile(ReconcileReason reason);
```

Reasons are `APP_LAUNCH`, `APP_RESUME`, `BOOT_COMPLETED`, `TIME_CHANGED`, `TIMEZONE_CHANGED`, `PACKAGE_REPLACED`, and `EXACT_CAPABILITY_GRANTED`.

Reconciliation loads all enabled alarms, applies the expired-occurrence policy, cancels stale registrations, computes/persists the next valid occurrence, and schedules exactly one trigger plus at most one advance notification per alarm. Repeating the same reconciliation MUST be idempotent. `ReconcileReport` contains `completed`, one result per enabled alarm, and `failed_alarm_ids`; any missing/current `NOT_REGISTERED` entry prevents full protection.

Expired occurrences are surfaced through an independent durable
`MissedAlarmOutcome` queue, not through `ReconcileReport`. The store exposes list and
acknowledge operations for pending outcomes. Reconciliation persists the outcome
before disabling a one-time alarm, deleting a Quick Nap, or advancing a recurring
alarm. UI acknowledgement occurs only after presentation and atomically removes the
queue entry; listing and acknowledgement are idempotent.

### `AlarmRingingService`

The service owns active audio, haptics, bounded wake-lock lifetime, and stop/snooze/dismiss commands. It exists only while an occurrence is active, enters the foreground immediately, and stops itself after completion or the configured safety timeout. With target SDK 34 it is declared as `mediaPlayback` and the manifest declares `FOREGROUND_SERVICE` plus `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. `RingingActivity` binds/publishes user actions but is not the sole owner of ringing execution. If a best-effort inexact delivery is not permitted to start the foreground service, the receiver handles that rejection and falls back to the permitted notification/full-screen path without crashing or reporting full protection.

---

## 3. Canonical PendingIntent Identity

Schedule, replace, query, and cancel MUST use the same factory and the following identity fields:

| Field | Contract |
|:---|:---|
| Type | `PendingIntent.getBroadcast()` |
| Component | Explicit `AlarmTriggerReceiver` |
| Action | `com.edom.alarm.ACTION_ALARM_TRIGGER` |
| Request code | Deterministic conversion of `alarm_id` |
| Data URI | `alarm://trigger/{alarm_id}` |
| Flags | `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE` |

`alarm_id`, `occurrence_id`, and `trigger_at_ms` extras are payload. Extras do not define PendingIntent identity and MUST be validated against persisted state when received.

---

## 4. Delivery Sequence

1. In one store mutation, persist the logical alarm plus its future `next_trigger_at_ms`, incremented generation, string `occurrence_id`, and unclaimed state.
2. Evaluate capability and register that persisted occurrence through `AlarmSystemScheduler`; preserve `NOT_REGISTERED` in the reconciliation result on framework failure.
3. At trigger time Android delivers the explicit broadcast PendingIntent and recreates the process if needed.
4. `AlarmTriggerReceiver` loads the alarm by ID and, in one synchronized store critical section, rejects missing, disabled, skipped, stale, or already-claimed occurrences and persists `last_claimed_occurrence_id` before handoff.
5. For a valid exact occurrence, the receiver starts `AlarmRingingService`; the service immediately enters the foreground with an `IMPORTANCE_HIGH`, `CATEGORY_ALARM` notification before starting audio/haptics.
6. If full-screen capability is available, the service notification attaches `RingingActivity` as the full-screen intent. Otherwise it uses the permitted heads-up/content-intent path without directly calling `startActivity()` from the receiver.
7. For a best-effort inexact occurrence, the receiver attempts the same handoff. If the platform rejects background foreground-service start, it catches the failure, posts whatever alarm notification/full-screen path remains permitted, records the attempt outcome, and keeps protection `LIMITED`.
8. Claiming an occurrence schedules the next recurring occurrence through the same scheduler gateway; one-time alarms disable and Quick Nap alarms delete according to their lifecycle rules.
9. Dismiss/snooze/cancel stops the active service and updates registration through the same identity factory.

---

## 5. Capability and UI Contract

| Missing capability | Registration/alert behavior | Persistent dashboard state | Banner action |
|:---|:---|:---|:---|
| None and all current registrations succeeded | Exact alarm + notification + permitted full-screen intent | Hidden | None |
| Exact alarm | Best-effort idle-allowed alarm | `Alarm protection limited / 闹钟保护受限` | Alarms & reminders settings |
| Notifications | Continue timing/ringing attempt; no notification visibility guarantee | Same warning | App notification settings |
| Full-screen alert | Continue timing/ringing attempt; notification/content-intent fallback | Same warning | Full-screen intent settings on supported API levels |
| Multiple | Apply all degradation rules | Same warning | Highest-impact missing capability, then re-evaluate on return |
| Registration incomplete/failed | Preserve enabled state; report failed alarm IDs and retry reconciliation | Same warning | Retry or actionable failure details |

The banner is non-dismissible while at least one alarm is enabled and protection remains limited. It disappears only when all required capabilities are available and the latest completed reconciliation has a successful current registration for every enabled alarm.

---

## 6. Recovery Contract

- Boot, time, timezone, and package-replace receivers invoke reconciliation only; they MUST NOT start ringing media without a due alarm occurrence.
- Explicit application launch after force-stop invokes reconciliation before the UI reports alarm protection as active.
- Force-stop limitations remain visible in initial/reliability guidance on every API level. With the current compile SDK 34, launch reconciliation is unconditional and MUST NOT infer force-stop from Recent Tasks removal.
- Exact-alarm capability recovery invokes reconciliation because Android may have canceled exact registrations when capability was revoked. External revocation may stop the package, so conversion to best-effort is required on the next explicit resume/relaunch; no autonomous fallback is promised while stopped.
- A framework error during one alarm registration does not prevent reconciliation of other alarms; the report lists failures and protection remains limited.
- Alarm JSON remains credential-protected in this repair. Remove `LOCKED_BOOT_COMPLETED`/`directBootAware` delivery claims and reconcile after `BOOT_COMPLETED`; pre-unlock alarm delivery is outside this contract.
- Hardware power-off RTC wakeup (FR-026) is outside this contract and MUST NOT be inferred from `BOOT_COMPLETED`. A later OEM-specific contract requires a documented third-party interface, device-protected occurrence data, and powered-off/pre-unlock validation.
- A due unclaimed persisted occurrence found during reconciliation is recorded as missed rather than rung retroactively: standard one-time alarms disable, expired Quick Naps are surfaced as missed then removed, and recurring alarms advance to the next strictly future occurrence.
- A Quick Nap record may be deleted only after its independent pending missed outcome is durably stored. Replacing a `ReconcileReport` cannot discard that outcome; it remains pending until the UI presents and acknowledges it.

---

## 7. Verification Contract

- Autonomous delivery tests MUST wait for a real registered AlarmManager occurrence.
- `am broadcast` and `am start` tests validate component entry points only.
- `am kill` may represent process death; `am force-stop` MUST be tested as the unsupported stopped-package boundary, not as Recent Tasks removal.
- Doze is a separate case: record `dumpsys alarm`, explicitly enter device idle, read back idle state, wait for the real registered alarm, calculate receiver latency from `trigger_at_ms`, and restore idle state after the case.
- Each result records API level/device, exit state, exact/notification/full-screen capability, registered operation, receiver/service timestamps, visual surface, and audio/haptic outcome.
- Required matrices include representative Android 11, 12, 13, 14, and 15+ runtimes, Samsung One UI, and Vivo/iQOO OriginOS.
- Unit/policy coverage includes legacy JSON upgrade, process reconstruction, duplicate delivery, stale payload rejection, expired one-time/Quick Nap/recurring handling, and partial reconciliation failure.
