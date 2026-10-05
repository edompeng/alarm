package com.edom.alarm.core.scheduler;

import android.content.Context;
import android.content.SharedPreferences;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.ClaimResult;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedAlarmOutcome;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.MissedState;
import com.edom.alarm.core.scheduler.AlarmDeliveryModels.StoredAlarm;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Android SharedPreferences adapter for durable alarm and missed-outcome state. */
public final class SharedPreferencesAlarmScheduleStore implements AlarmScheduleStore {
    public static final String PREFERENCES_NAME = "smart_alarm_prefs";
    public static final String ALARMS_KEY = "alarms_list";
    public static final String MISSED_OUTCOMES_KEY = "missed_alarm_outcomes";

    private static final String EMPTY_JSON_ARRAY = "[]";
    private static final int DEFAULT_SNOOZE_INTERVAL_MINUTES = 10;
    private static final int DEFAULT_SNOOZE_MAX_COUNT = 3;
    private static final Object PREFERENCES_LOCK = new Object();
    private static final String[] NON_DURABLE_ALARM_KEYS = {
        "exact_alarm_available",
        "notifications_available",
        "full_screen_available",
        "evaluated_at_ms",
        "protection_level",
        "registration_mode",
        "failed_alarm_ids",
        "exactAlarmAvailable",
        "notificationsAvailable",
        "fullScreenAvailable",
        "evaluatedAtMs",
        "protectionLevel",
        "registrationMode",
        "failedAlarmIds"
    };

    private final SharedPreferences preferences;

    public SharedPreferencesAlarmScheduleStore(Context context) {
        this(Objects.requireNonNull(context, "context")
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE));
    }

    /** Injection constructor for Android integration tests and alternate Context owners. */
    public SharedPreferencesAlarmScheduleStore(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences, "preferences");
    }

    @Override
    public List<StoredAlarm> loadAll() {
        synchronized (PREFERENCES_LOCK) {
            AlarmDocument document = readAlarmDocument();
            persistUpgradeIfNeeded(document);
            return new ArrayList<>(document.alarms);
        }
    }

    @Override
    public StoredAlarm findById(long alarmId) {
        synchronized (PREFERENCES_LOCK) {
            AlarmDocument document = readAlarmDocument();
            persistUpgradeIfNeeded(document);
            for (StoredAlarm alarm : document.alarms) {
                if (alarm.id == alarmId) {
                    return alarm;
                }
            }
            return null;
        }
    }

    @Override
    public void save(StoredAlarm alarm) {
        Objects.requireNonNull(alarm, "alarm");
        synchronized (PREFERENCES_LOCK) {
            JSONArray alarms = readJsonArray(ALARMS_KEY);
            commitJson(ALARMS_KEY, upsertAlarm(alarms, alarm));
        }
    }

    @Override
    public void delete(long alarmId) {
        synchronized (PREFERENCES_LOCK) {
            JSONArray alarms = readJsonArray(ALARMS_KEY);
            RemovalResult result = removeAlarm(alarms, alarmId);
            if (result.removed) {
                commitJson(ALARMS_KEY, result.alarms);
            }
        }
    }

    /**
     * Replaces the currently scheduled occurrence with a durable snooze occurrence.
     *
     * <p>The active service validates the command's occurrence before entering this store
     * boundary. The persisted record may already contain the following recurring occurrence, so
     * this mutation intentionally keys by alarm ID and serializes with claim/reconcile writes.
     */
    StoredAlarm createSnoozedOccurrence(long alarmId, long nowMs) {
        synchronized (PREFERENCES_LOCK) {
            AlarmDocument document = readAlarmDocument();
            persistUpgradeIfNeeded(document);
            for (int i = 0; i < document.alarms.size(); i++) {
                StoredAlarm alarm = document.alarms.get(i);
                if (alarm.id != alarmId) {
                    continue;
                }
                try {
                    JSONObject persisted = document.json.getJSONObject(i);
                    int intervalMinutes = readSnoozeSetting(
                            persisted,
                            "snooze_interval_minutes",
                            "snoozeIntervalMinutes",
                            DEFAULT_SNOOZE_INTERVAL_MINUTES);
                    int maximumCount = readSnoozeSetting(
                            persisted,
                            "snooze_max_count",
                            "snoozeMaxCount",
                            DEFAULT_SNOOZE_MAX_COUNT);
                    int currentCount = readSnoozeSetting(
                            persisted, "snooze_count", "snoozeCount", 0);
                    if (intervalMinutes < 1 || intervalMinutes > 60
                            || maximumCount < -1 || (maximumCount >= 0
                            && currentCount >= maximumCount)) {
                        return null;
                    }

                    long triggerAtMs = Math.addExact(
                            nowMs, Math.multiplyExact(intervalMinutes, 60L * 1000L));
                    StoredAlarm snoozed = AlarmDeliveryPolicy.createNextOccurrence(
                            alarm.withEnabled(true), triggerAtMs);
                    JSONObject updatedJson = encodeAlarm(snoozed, persisted);
                    updatedJson.put("snooze_count", currentCount + 1);
                    updatedJson.remove("snoozeCount");
                    commitJson(ALARMS_KEY, replaceAlarmAt(document.json, i, updatedJson));
                    return decodeAlarm(updatedJson);
                } catch (JSONException | ArithmeticException exception) {
                    throw invalidStoredJson(ALARMS_KEY, exception);
                }
            }
            return null;
        }
    }

    /** Applies the completed-occurrence lifecycle and resets its per-ring snooze counter. */
    void completeRingingOccurrence(long alarmId) {
        completeRingingOccurrence(alarmId, false);
    }

    /**
     * Applies the completed-occurrence lifecycle and resets its per-ring snooze counter.
     *
     * @param deleteExpired when true a finished one-time alarm is removed instead of being
     *                      kept as a disabled record (Quick Naps are always removed).
     */
    void completeRingingOccurrence(long alarmId, boolean deleteExpired) {
        synchronized (PREFERENCES_LOCK) {
            AlarmDocument document = readAlarmDocument();
            persistUpgradeIfNeeded(document);
            for (int i = 0; i < document.alarms.size(); i++) {
                StoredAlarm alarm = document.alarms.get(i);
                if (alarm.id != alarmId) {
                    continue;
                }
                if (alarm.quickNap || (deleteExpired && alarm.repeatMode == 0)) {
                    RemovalResult result = removeAlarm(document.json, alarmId);
                    if (result.removed) {
                        commitJson(ALARMS_KEY, result.alarms);
                    }
                    return;
                }
                try {
                    JSONObject persisted = document.json.getJSONObject(i);
                    persisted.put("snooze_count", 0);
                    persisted.remove("snoozeCount");
                    StoredAlarm completed = alarm.repeatMode == 0
                            ? alarm.withEnabled(false) : alarm;
                    JSONObject updatedJson = encodeAlarm(completed, persisted);
                    commitJson(ALARMS_KEY, replaceAlarmAt(document.json, i, updatedJson));
                    return;
                } catch (JSONException exception) {
                    throw invalidStoredJson(ALARMS_KEY, exception);
                }
            }
        }
    }

    @Override
    public ClaimResult claimOccurrence(
            long alarmId, String occurrenceId, long triggerAtMs, String localDate) {
        synchronized (PREFERENCES_LOCK) {
            AlarmDocument document = readAlarmDocument();
            persistUpgradeIfNeeded(document);
            for (int i = 0; i < document.alarms.size(); i++) {
                StoredAlarm alarm = document.alarms.get(i);
                if (alarm.id != alarmId) {
                    continue;
                }
                if (alarm.nextTriggerAtMs != triggerAtMs) {
                    return ClaimResult.STALE;
                }
                boolean skipped = localDate != null && alarm.skippedDates.contains(localDate);
                AlarmDeliveryPolicy.ClaimDecision decision =
                        AlarmDeliveryPolicy.claimOccurrence(alarm, occurrenceId, skipped);
                if (decision.result == ClaimResult.CLAIMED) {
                    commitJson(ALARMS_KEY, upsertAlarm(document.json, decision.updatedAlarm));
                }
                return decision.result;
            }
            return ClaimResult.MISSING;
        }
    }

    @Override
    public void applyMissedTransition(
            MissedAlarmOutcome outcome, StoredAlarm updatedAlarm, boolean deleteAlarm) {
        Objects.requireNonNull(outcome, "outcome");
        if (deleteAlarm && updatedAlarm != null) {
            throw new IllegalArgumentException("A missed transition cannot update and delete");
        }
        if (updatedAlarm != null && updatedAlarm.id != outcome.alarmId) {
            throw new IllegalArgumentException("Missed outcome and alarm IDs differ");
        }

        synchronized (PREFERENCES_LOCK) {
            JSONArray outcomes = readJsonArray(MISSED_OUTCOMES_KEY);
            if (!containsOutcome(outcomes, outcome.outcomeId)) {
                outcomes.put(encodeOutcome(outcome));
                // This commit must succeed before an alarm can be disabled, advanced, or deleted.
                commitJson(MISSED_OUTCOMES_KEY, outcomes);
            }

            if (deleteAlarm) {
                JSONArray alarms = readJsonArray(ALARMS_KEY);
                RemovalResult result = removeAlarm(alarms, outcome.alarmId);
                if (result.removed) {
                    commitJson(ALARMS_KEY, result.alarms);
                }
            } else if (updatedAlarm != null) {
                commitJson(ALARMS_KEY,
                        upsertAlarm(readJsonArray(ALARMS_KEY), updatedAlarm));
            }
        }
    }

    @Override
    public List<MissedAlarmOutcome> loadPendingMissedOutcomes() {
        synchronized (PREFERENCES_LOCK) {
            JSONArray json = readJsonArray(MISSED_OUTCOMES_KEY);
            List<MissedAlarmOutcome> outcomes = new ArrayList<>(json.length());
            for (int i = 0; i < json.length(); i++) {
                try {
                    outcomes.add(decodeOutcome(json.getJSONObject(i)));
                } catch (JSONException | IllegalArgumentException exception) {
                    throw invalidStoredJson(MISSED_OUTCOMES_KEY, exception);
                }
            }
            return outcomes;
        }
    }

    @Override
    public void acknowledgeMissedOutcome(String outcomeId) {
        synchronized (PREFERENCES_LOCK) {
            JSONArray current = readJsonArray(MISSED_OUTCOMES_KEY);
            JSONArray retained = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < current.length(); i++) {
                try {
                    JSONObject outcome = current.getJSONObject(i);
                    if (Objects.equals(outcome.optString("outcome_id", ""), outcomeId)) {
                        removed = true;
                    } else {
                        retained.put(outcome);
                    }
                } catch (JSONException exception) {
                    throw invalidStoredJson(MISSED_OUTCOMES_KEY, exception);
                }
            }
            if (removed) {
                commitJson(MISSED_OUTCOMES_KEY, retained);
            }
        }
    }

    private AlarmDocument readAlarmDocument() {
        JSONArray json = readJsonArray(ALARMS_KEY);
        List<StoredAlarm> alarms = new ArrayList<>(json.length());
        boolean upgraded = false;
        for (int i = 0; i < json.length(); i++) {
            try {
                JSONObject alarmJson = json.getJSONObject(i);
                upgraded |= normalizeDurableSchema(alarmJson);
                alarms.add(decodeAlarm(alarmJson));
            } catch (JSONException | IllegalArgumentException exception) {
                throw invalidStoredJson(ALARMS_KEY, exception);
            }
        }
        return new AlarmDocument(json, alarms, upgraded);
    }

    private void persistUpgradeIfNeeded(AlarmDocument document) {
        if (document.upgraded) {
            commitJson(ALARMS_KEY, document.json);
        }
    }

    private JSONArray readJsonArray(String key) {
        String value = preferences.getString(key, EMPTY_JSON_ARRAY);
        if (value == null || value.isEmpty()) {
            return new JSONArray();
        }
        try {
            return new JSONArray(value);
        } catch (JSONException exception) {
            throw invalidStoredJson(key, exception);
        }
    }

    private void commitJson(String key, JSONArray value) {
        if (!preferences.edit().putString(key, value.toString()).commit()) {
            throw new IllegalStateException(
                    "Unable to durably write SharedPreferences key: " + key);
        }
    }

    private static StoredAlarm decodeAlarm(JSONObject json) throws JSONException {
        int hour = getIntOrDefault(json, "hour", 8);
        int minute = getIntOrDefault(json, "minute", 0);
        int repeatMode = getIntOrDefault(json, "repeatMode", 0);
        validateAlarmFields(hour, minute, repeatMode);

        Set<String> skippedDates = new LinkedHashSet<>();
        JSONArray skippedJson = json.optJSONArray("skippedDates");
        if (json.has("skippedDates") && !json.isNull("skippedDates") && skippedJson == null) {
            throw new JSONException("skippedDates must be an array");
        }
        if (skippedJson != null) {
            for (int i = 0; i < skippedJson.length(); i++) {
                skippedDates.add(skippedJson.getString(i));
            }
        }

        String ringtoneUri = json.optString("ringtoneUri", null);
        if (ringtoneUri != null && ringtoneUri.isEmpty()) {
            ringtoneUri = null;
        }
        return new StoredAlarm(
                json.optLong("id", 0L),
                hour,
                minute,
                json.optBoolean("isEnabled", true),
                repeatMode,
                json.optInt("daysBitmask", 0),
                json.optString("label", "Alarm"),
                json.optBoolean("isQuickNap", false),
                ringtoneUri,
                json.optString("ringtoneTitle", "Default Alarm Sound"),
                json.optBoolean("vibrateEnabled", true),
                skippedDates,
                json.optLong("next_trigger_at_ms", 0L),
                json.optLong("occurrence_generation", 0L),
                json.optString("occurrence_id", ""),
                json.optString("last_claimed_occurrence_id", ""),
                parseMissedState(json.optString("missed_state", MissedState.NONE.name())),
                json.toString());
    }

    private static void validateAlarmFields(int hour, int minute, int repeatMode) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
        if (minute < 0 || minute > 59) {
            throw new IllegalArgumentException("minute must be between 0 and 59");
        }
        if (repeatMode < 0 || repeatMode > 2) {
            throw new IllegalArgumentException("repeatMode must be 0, 1, or 2");
        }
    }

    private static int getIntOrDefault(JSONObject json, String key, int defaultValue)
            throws JSONException {
        return json.has(key) && !json.isNull(key) ? json.getInt(key) : defaultValue;
    }

    private static int readSnoozeSetting(
            JSONObject json, String canonicalKey, String legacyKey, int defaultValue)
            throws JSONException {
        if (json.has(canonicalKey) && !json.isNull(canonicalKey)) {
            return json.getInt(canonicalKey);
        }
        if (json.has(legacyKey) && !json.isNull(legacyKey)) {
            return json.getInt(legacyKey);
        }
        return defaultValue;
    }

    private static MissedState parseMissedState(String value) {
        try {
            return MissedState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid missed_state: " + value, exception);
        }
    }

    private static boolean normalizeDurableSchema(JSONObject json) throws JSONException {
        boolean changed = false;
        changed |= putDefault(json, "next_trigger_at_ms", 0L);
        changed |= putDefault(json, "occurrence_generation", 0L);
        changed |= putDefault(json, "occurrence_id", "");
        changed |= putDefault(json, "last_claimed_occurrence_id", "");
        changed |= putDefault(json, "missed_state", MissedState.NONE.name());
        for (String key : NON_DURABLE_ALARM_KEYS) {
            if (json.has(key)) {
                json.remove(key);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean putDefault(JSONObject json, String key, Object value)
            throws JSONException {
        if (json.has(key) && !json.isNull(key)) {
            return false;
        }
        json.put(key, value);
        return true;
    }

    private static JSONArray upsertAlarm(JSONArray current, StoredAlarm alarm) {
        JSONArray updated = new JSONArray();
        boolean replaced = false;
        for (int i = 0; i < current.length(); i++) {
            try {
                JSONObject json = current.getJSONObject(i);
                if (json.optLong("id", Long.MIN_VALUE) == alarm.id) {
                    if (!replaced) {
                        updated.put(encodeAlarm(alarm, json));
                        replaced = true;
                    }
                } else {
                    updated.put(json);
                }
            } catch (JSONException exception) {
                throw invalidStoredJson(ALARMS_KEY, exception);
            }
        }
        if (!replaced) {
            updated.put(encodeAlarm(alarm, null));
        }
        return updated;
    }

    private static JSONArray replaceAlarmAt(
            JSONArray current, int replacementIndex, JSONObject replacement) {
        JSONArray updated = new JSONArray();
        for (int i = 0; i < current.length(); i++) {
            try {
                updated.put(i == replacementIndex ? replacement : current.getJSONObject(i));
            } catch (JSONException exception) {
                throw invalidStoredJson(ALARMS_KEY, exception);
            }
        }
        return updated;
    }

    private static JSONObject encodeAlarm(StoredAlarm alarm, JSONObject persistedJson) {
        try {
            JSONObject json = new JSONObject();
            if (!alarm.sourceJson.isEmpty()) {
                copyKeys(new JSONObject(alarm.sourceJson), json);
            }
            if (persistedJson != null) {
                copyKeys(persistedJson, json);
            }
            for (String key : NON_DURABLE_ALARM_KEYS) {
                json.remove(key);
            }

            json.put("id", alarm.id);
            json.put("hour", alarm.hour);
            json.put("minute", alarm.minute);
            json.put("isEnabled", alarm.enabled);
            json.put("repeatMode", alarm.repeatMode);
            json.put("daysBitmask", alarm.daysBitmask);
            json.put("label", alarm.label);
            json.put("isQuickNap", alarm.quickNap);
            json.put("ringtoneUri", alarm.ringtoneUri == null ? "" : alarm.ringtoneUri);
            json.put("ringtoneTitle", alarm.ringtoneTitle);
            json.put("vibrateEnabled", alarm.vibrateEnabled);
            JSONArray skippedDates = new JSONArray();
            for (String skippedDate : alarm.skippedDates) {
                skippedDates.put(skippedDate);
            }
            json.put("skippedDates", skippedDates);
            json.put("next_trigger_at_ms", alarm.nextTriggerAtMs);
            json.put("occurrence_generation", alarm.occurrenceGeneration);
            json.put("occurrence_id", alarm.occurrenceId);
            json.put("last_claimed_occurrence_id", alarm.lastClaimedOccurrenceId);
            json.put("missed_state", alarm.missedState.name());
            return json;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid alarm source JSON", exception);
        }
    }

    private static void copyKeys(JSONObject source, JSONObject destination) throws JSONException {
        Iterator<String> keys = source.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            destination.put(key, source.get(key));
        }
    }

    private static RemovalResult removeAlarm(JSONArray current, long alarmId) {
        JSONArray retained = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < current.length(); i++) {
            try {
                JSONObject json = current.getJSONObject(i);
                if (json.optLong("id", Long.MIN_VALUE) == alarmId) {
                    removed = true;
                } else {
                    retained.put(json);
                }
            } catch (JSONException exception) {
                throw invalidStoredJson(ALARMS_KEY, exception);
            }
        }
        return new RemovalResult(retained, removed);
    }

    private static boolean containsOutcome(JSONArray outcomes, String outcomeId) {
        for (int i = 0; i < outcomes.length(); i++) {
            try {
                JSONObject outcome = outcomes.getJSONObject(i);
                if (Objects.equals(outcome.optString("outcome_id", ""), outcomeId)) {
                    return true;
                }
            } catch (JSONException exception) {
                throw invalidStoredJson(MISSED_OUTCOMES_KEY, exception);
            }
        }
        return false;
    }

    private static JSONObject encodeOutcome(MissedAlarmOutcome outcome) {
        try {
            JSONObject json = new JSONObject();
            json.put("outcome_id", outcome.outcomeId);
            json.put("alarm_id", outcome.alarmId);
            json.put("occurrence_id", outcome.occurrenceId);
            json.put("missed_state", outcome.missedState.name());
            json.put("missed_at_ms", outcome.missedAtMs);
            json.put("label", outcome.label);
            return json;
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Unable to encode missed outcome", exception);
        }
    }

    private static MissedAlarmOutcome decodeOutcome(JSONObject json) throws JSONException {
        return new MissedAlarmOutcome(
                json.getString("outcome_id"),
                json.getLong("alarm_id"),
                json.optString("occurrence_id", ""),
                parseMissedState(json.getString("missed_state")),
                json.getLong("missed_at_ms"),
                json.optString("label", ""));
    }

    private static IllegalStateException invalidStoredJson(String key, Exception cause) {
        return new IllegalStateException("Invalid SharedPreferences JSON for key: " + key, cause);
    }

    private static final class AlarmDocument {
        final JSONArray json;
        final List<StoredAlarm> alarms;
        final boolean upgraded;

        AlarmDocument(JSONArray json, List<StoredAlarm> alarms, boolean upgraded) {
            this.json = json;
            this.alarms = alarms;
            this.upgraded = upgraded;
        }
    }

    private static final class RemovalResult {
        final JSONArray alarms;
        final boolean removed;

        RemovalResult(JSONArray alarms, boolean removed) {
            this.alarms = alarms;
            this.removed = removed;
        }
    }
}
