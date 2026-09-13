# Statutory Holiday Synchronization Contract

## Overview

This contract defines the interface and protocols for synchronizing official Chinese statutory holiday and compensatory workday arrangements from a remote HTTP(S) endpoint into the Android Smart Alarm application.

---

## 1. Remote Endpoint Schema (HTTP GET)

### 1.1 Request
- **Method**: `GET`
- **Headers**:
  - `Accept: application/json`
  - `User-Agent: SmartAlarm-Android/1.0`
- **Timeout**: 8,000 milliseconds connect timeout, 10,000 milliseconds read timeout.

### 1.2 Response (HTTP 200 OK)
- **Content-Type**: `application/json`
- **Body**:
```json
{
  "year": 2026,
  "holidays": [
    "2026-01-01", "2026-01-02", "2026-01-03",
    "2026-02-15", "2026-02-16", "2026-02-17", "2026-02-18", "2026-02-19", "2026-02-20", "2026-02-21", "2026-02-22", "2026-02-23",
    "2026-04-04", "2026-04-05", "2026-04-06",
    "2026-05-01", "2026-05-02", "2026-05-03", "2026-05-04", "2026-05-05",
    "2026-06-19", "2026-06-20", "2026-06-21",
    "2026-09-25", "2026-09-26", "2026-09-27",
    "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07"
  ],
  "workdays": [
    "2026-02-14", "2026-02-28",
    "2026-04-26", "2026-05-09",
    "2026-09-20", "2026-10-10"
  ]
}
```

### 1.3 Validation Requirements
1. Response must be well-formed JSON object.
2. `year`: integer between 2020 and 2099 inclusive.
3. `holidays`: non-null JSON array of date strings matching `^\d{4}-\d{2}-\d{2}$`.
4. `workdays`: non-null JSON array of date strings matching `^\d{4}-\d{2}-\d{2}$`.
5. Intersection of `holidays` and `workdays` sets must be empty.

---

## 2. Programmatic Java Interface (`HolidaySyncManager.java`)

```java
package com.edom.alarm.ui;

import android.content.Context;

public class HolidaySyncManager {

    public interface OnSyncCallback {
        void onSuccess(int year, int holidayCount, int workdayCount);
        void onError(String errorMessage);
    }

    /**
     * Executes manual synchronization in a background worker thread.
     * Shows modal error dialog or success confirmation via callback on UI thread.
     */
    public static void syncManual(Context context, String url, OnSyncCallback callback);

    /**
     * Checks if automatic sync is due upon cold launch (never synced, previous failed,
     * or >= 7 days elapsed). If due, runs sync asynchronously and silently updates cache.
     */
    public static void checkAndSyncAuto(Context context);
}
```

---

## 3. UI Error & Success State Presentation

| Trigger Mode | Condition | UI Behavior |
|:---|:---|:---|
| **Manual (Settings)** | HTTP 200 & Valid JSON | Shows success Toast: "Holiday rules updated (X holidays, Y workdays)" |
| **Manual (Settings)** | Network timeout / HTTP error / Malformed JSON | Shows modal `AlertDialog`: Title "Update Failed / 更新失败", Message containing the specific reason, "OK" button. Existing rules remain unchanged. |
| **Automatic (Cold Launch)** | Due & Successful | Silently updates local cache, reloads holiday rules, sets `last_holiday_sync_timestamp = now`, `last_holiday_sync_status = true`. |
| **Automatic (Cold Launch)** | Due & Failed | Silently logs error, sets `last_holiday_sync_status = false`. Scheduled to retry on next app launch. Zero popups or interruptions. |
