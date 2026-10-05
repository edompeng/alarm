package com.edom.alarm.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import com.edom.alarm.R;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages remote statutory holiday and compensatory workday synchronization,
 * validation against HolidaySyncModel, local persistence, and auto-sync cadence.
 */
public class HolidaySyncManager {

    public static final String PREFS_NAME = "smart_alarm_prefs";
    public static final String DEFAULT_SYNC_URL = "https://raw.githubusercontent.com/edompeng/alarm/master/core/src/assets/holidays_2026.json";
    public static final String LEGACY_SYNC_URL = "https://raw.githubusercontent.com/edom/alarm/main/core/src/assets/holidays_2026.json";

    public static final String KEY_HOLIDAY_SYNC_URL = "holiday_sync_url";
    public static final String KEY_LAST_SYNC_TIME = "last_holiday_sync_timestamp";
    public static final String KEY_LAST_SYNC_STATUS = "last_holiday_sync_status";
    public static final String KEY_CACHED_HOLIDAYS = "cached_holiday_dates";
    public static final String KEY_CACHED_WORKDAYS = "cached_workday_dates";

    private static final long SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface OnSyncCallback {
        void onSuccess(int year, int holidayCount, int workdayCount);
        void onError(String errorMessage);
    }

    /**
     * Notified on the main thread only after fetched holiday rules have been
     * persisted successfully. Composition roots can use this to reconcile
     * enabled alarms against the updated workday calendar.
     */
    public interface OnHolidayRulesUpdatedListener {
        void onHolidayRulesUpdated();
    }

    /**
     * Executes manual synchronization in a background thread.
     * Posts success or error callback to UI thread.
     */
    public static void syncManual(Context context, String urlString, OnSyncCallback callback) {
        syncManual(context, urlString, callback, null);
    }

    /**
     * Executes manual synchronization and optionally notifies a composition
     * root after the new rules are durable.
     */
    public static void syncManual(
            Context context,
            String urlString,
            OnSyncCallback callback,
            OnHolidayRulesUpdatedListener rulesUpdatedListener) {
        if (urlString == null || urlString.trim().isEmpty()) {
            if (callback != null) {
                MAIN_HANDLER.post(() -> callback.onError("Sync URL cannot be empty"));
            }
            return;
        }

        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                SyncResult result = executeFetchAndParse(urlString);
                persistSyncResult(appContext, result);

                // Update settings
                SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                        .putBoolean(KEY_LAST_SYNC_STATUS, true)
                        .apply();

                if (rulesUpdatedListener != null || callback != null) {
                    MAIN_HANDLER.post(() -> {
                        notifyRulesUpdated(rulesUpdatedListener);
                        if (callback != null) {
                            callback.onSuccess(result.year, result.holidays.size(), result.workdays.size());
                        }
                    });
                }
            } catch (Exception e) {
                // Record failure status
                SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_LAST_SYNC_STATUS, false).apply();

                if (callback != null) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    MAIN_HANDLER.post(() -> callback.onError(msg));
                }
            }
        });
    }

    /**
     * Checks if auto-sync is due upon cold launch (never synced, previous failed,
     * or >= 7 days elapsed). If due, runs sync asynchronously and silently updates cache.
     */
    public static void checkAndSyncAuto(Context context) {
        checkAndSyncAuto(context, null);
    }

    /**
     * Checks whether automatic synchronization is due and optionally notifies
     * a composition root after durable success. Automatic failures remain
     * silent and do not invoke the listener.
     */
    public static void checkAndSyncAuto(
            Context context, OnHolidayRulesUpdatedListener rulesUpdatedListener) {
        final Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        long lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0);
        boolean lastSyncStatus = prefs.getBoolean(KEY_LAST_SYNC_STATUS, false);
        long now = System.currentTimeMillis();

        boolean isDue = (lastSyncTime == 0) || (!lastSyncStatus) || (now - lastSyncTime >= SEVEN_DAYS_MS);
        if (!isDue) {
            return;
        }

        String syncUrl = prefs.getString(KEY_HOLIDAY_SYNC_URL, DEFAULT_SYNC_URL);
        if (LEGACY_SYNC_URL.equals(syncUrl)) {
            syncUrl = DEFAULT_SYNC_URL;
            prefs.edit().putString(KEY_HOLIDAY_SYNC_URL, DEFAULT_SYNC_URL).apply();
        }
        final String effectiveSyncUrl = syncUrl;
        EXECUTOR.execute(() -> {
            try {
                SyncResult result = executeFetchAndParse(effectiveSyncUrl);
                persistSyncResult(appContext, result);
                prefs.edit()
                        .putLong(KEY_LAST_SYNC_TIME, System.currentTimeMillis())
                        .putBoolean(KEY_LAST_SYNC_STATUS, true)
                        .apply();
                if (rulesUpdatedListener != null) {
                    MAIN_HANDLER.post(() -> notifyRulesUpdated(rulesUpdatedListener));
                }
            } catch (Exception ignored) {
                // Auto-sync failure is completely silent; mark status false to retry next launch
                prefs.edit().putBoolean(KEY_LAST_SYNC_STATUS, false).apply();
            }
        });
    }

    private static void notifyRulesUpdated(OnHolidayRulesUpdatedListener rulesUpdatedListener) {
        if (rulesUpdatedListener == null) {
            return;
        }
        rulesUpdatedListener.onHolidayRulesUpdated();
    }

    /**
     * Determines whether the given date (YYYY-MM-DD) is a statutory workday.
     */
    public static boolean isStatutoryWorkday(Context context, String dateStr) {
        Set<String> holidays = getCachedHolidays(context);
        Set<String> workdays = getCachedWorkdays(context);

        // 1. Compensatory workdays (调休补班) take precedence -> MUST RING
        if (workdays.contains(dateStr)) {
            return true;
        }

        // 2. Official statutory holidays (法定节假日) -> SKIP
        if (holidays.contains(dateStr)) {
            return false;
        }

        // 3. Fallback to standard day of week
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int dow = cal.get(Calendar.DAY_OF_WEEK);
                // Sunday (1) or Saturday (7) -> weekend
                return dow != Calendar.SUNDAY && dow != Calendar.SATURDAY;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    public static Set<String> getCachedHolidays(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_CACHED_HOLIDAYS, null);
        if (set == null || set.isEmpty()) {
            loadBaselineRules(context);
            set = prefs.getStringSet(KEY_CACHED_HOLIDAYS, new HashSet<>());
        }
        return set != null ? set : new HashSet<>();
    }

    public static Set<String> getCachedWorkdays(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_CACHED_WORKDAYS, null);
        if (set == null || set.isEmpty()) {
            loadBaselineRules(context);
            set = prefs.getStringSet(KEY_CACHED_WORKDAYS, new HashSet<>());
        }
        return set != null ? set : new HashSet<>();
    }

    private static synchronized void loadBaselineRules(Context context) {
        try {
            InputStream is = context.getResources().openRawResource(R.raw.statutory_holidays_baseline);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            JSONArray rules = json.optJSONArray("rules");
            if (rules != null) {
                Set<String> holidays = new HashSet<>();
                Set<String> workdays = new HashSet<>();
                for (int i = 0; i < rules.length(); i++) {
                    JSONObject r = rules.getJSONObject(i);
                    String date = r.getString("date");
                    int type = r.getInt("type");
                    if (type == 2) {
                        holidays.add(date);
                    } else if (type == 3) {
                        workdays.add(date);
                    }
                }
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putStringSet(KEY_CACHED_HOLIDAYS, holidays)
                        .putStringSet(KEY_CACHED_WORKDAYS, workdays)
                        .apply();
            }
        } catch (Exception ignored) {
        }
    }

    private static class SyncResult {
        int year;
        Set<String> holidays = new HashSet<>();
        Set<String> workdays = new HashSet<>();
    }

    private static SyncResult executeFetchAndParse(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "SmartAlarm-Android/1.0");

        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP Error: " + responseCode + " " + conn.getResponseMessage());
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            sb.append(line);
        }
        in.close();
        conn.disconnect();

        String body = sb.toString().trim();
        if (body.isEmpty()) {
            throw new RuntimeException("Empty response received from server");
        }

        SyncResult result = new SyncResult();

        if (body.startsWith("[")) {
            // Multi-year array: parse all years
            JSONArray arr = new JSONArray(body);
            if (arr.length() == 0) throw new RuntimeException("Empty holiday JSON array");
            for (int i = 0; i < arr.length(); i++) {
                parseYearObject(arr.getJSONObject(i), result);
            }
        } else if (body.startsWith("{")) {
            JSONObject obj = new JSONObject(body);
            if (obj.has("years")) {
                JSONArray yearsArr = obj.getJSONArray("years");
                for (int i = 0; i < yearsArr.length(); i++) {
                    parseYearObject(yearsArr.getJSONObject(i), result);
                }
            } else if (obj.has("rules")) {
                // Baseline schema
                parseBaselineObject(obj, result);
            } else {
                // Standard single year object conforming to HolidaySyncModel
                parseYearObject(obj, result);
            }
        } else {
            throw new RuntimeException("Invalid JSON format");
        }

        // Validate intersection
        Set<String> overlap = new HashSet<>(result.holidays);
        overlap.retainAll(result.workdays);
        if (!overlap.isEmpty()) {
            throw new RuntimeException("Validation error: Dates overlap between holidays and workdays: " + overlap);
        }

        return result;
    }

    private static void parseYearObject(JSONObject obj, SyncResult result) throws Exception {
        int y = obj.getInt("year");
        if (y < 2020 || y > 2099) {
            throw new RuntimeException("Invalid year in holiday config: " + y);
        }
        result.year = y;

        JSONArray hArr = obj.getJSONArray("holidays");
        for (int i = 0; i < hArr.length(); i++) {
            String d = hArr.getString(i);
            validateDateFormat(d, y);
            result.holidays.add(d);
        }

        JSONArray wArr = obj.getJSONArray("workdays");
        for (int i = 0; i < wArr.length(); i++) {
            String d = wArr.getString(i);
            validateDateFormat(d, y);
            result.workdays.add(d);
        }
    }

    private static void parseBaselineObject(JSONObject obj, SyncResult result) throws Exception {
        JSONArray rules = obj.getJSONArray("rules");
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.getJSONObject(i);
            String d = r.getString("date");
            int type = r.getInt("type");
            int y = Integer.parseInt(d.substring(0, 4));
            result.year = y;
            validateDateFormat(d, y);
            if (type == 2) {
                result.holidays.add(d);
            } else if (type == 3) {
                result.workdays.add(d);
            }
        }
    }

    private static void validateDateFormat(String dateStr, int expectedYear) throws Exception {
        if (!dateStr.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            throw new RuntimeException("Invalid date format: " + dateStr + " (expected YYYY-MM-DD)");
        }
        if (!dateStr.startsWith(String.valueOf(expectedYear))) {
            throw new RuntimeException("Date " + dateStr + " does not match specified year " + expectedYear);
        }
    }

    private static void persistSyncResult(Context context, SyncResult result) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> existingHolidays = new HashSet<>(getCachedHolidays(context));
        Set<String> existingWorkdays = new HashSet<>(getCachedWorkdays(context));

        // Merge newly synced year's dates
        existingHolidays.addAll(result.holidays);
        existingWorkdays.addAll(result.workdays);

        prefs.edit()
                .putStringSet(KEY_CACHED_HOLIDAYS, existingHolidays)
                .putStringSet(KEY_CACHED_WORKDAYS, existingWorkdays)
                .apply();
    }
}
