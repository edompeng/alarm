#include "data/src/repository/alarm_repository_impl.h"

#include <sqlite3.h>

#include <iostream>

namespace edom::alarm::data {

AlarmRepositoryImpl::AlarmRepositoryImpl(AlarmDatabaseHelper* db_helper) : db_helper_(db_helper) {}

int64_t AlarmRepositoryImpl::InsertAlarm(const AlarmEntity& alarm) {
    if (!db_helper_ || !db_helper_->IsOpen() || !alarm.IsValid()) {
        return -1;
    }
    const char* sql = R"(
        INSERT INTO alarms (
            label, hour, minute, is_enabled, repeat_mode, days_bitmask,
            volume, crescendo_seconds, vibration_pattern, vibration_intensity,
            force_speaker, ringtone_type, ringtone_uri, ringtone_fallback_uri,
            snooze_interval_minutes, snooze_max_count, challenge_type,
            challenge_difficulty, tts_enabled, next_trigger_time, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
    )";

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return -1;
    }

    sqlite3_bind_text(stmt, 1, alarm.label.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, alarm.hour);
    sqlite3_bind_int(stmt, 3, alarm.minute);
    sqlite3_bind_int(stmt, 4, alarm.is_enabled ? 1 : 0);
    sqlite3_bind_int(stmt, 5, static_cast<int>(alarm.repeat_mode));
    sqlite3_bind_int(stmt, 6, alarm.days_bitmask);
    sqlite3_bind_int(stmt, 7, alarm.volume);
    sqlite3_bind_int(stmt, 8, alarm.crescendo_seconds);
    sqlite3_bind_text(stmt, 9, alarm.vibration_pattern.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 10, alarm.vibration_intensity);
    sqlite3_bind_int(stmt, 11, alarm.force_speaker ? 1 : 0);
    sqlite3_bind_text(stmt, 12, alarm.ringtone_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 13, alarm.ringtone_uri.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 14, alarm.ringtone_fallback_uri.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 15, alarm.snooze_interval_minutes);
    sqlite3_bind_int(stmt, 16, alarm.snooze_max_count);
    sqlite3_bind_text(stmt, 17, alarm.challenge_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 18, alarm.challenge_difficulty);
    sqlite3_bind_int(stmt, 19, alarm.tts_enabled ? 1 : 0);
    sqlite3_bind_int64(stmt, 20, alarm.next_trigger_time);
    sqlite3_bind_int64(stmt, 21, alarm.created_at);
    sqlite3_bind_int64(stmt, 22, alarm.updated_at);

    int64_t new_id = -1;
    if (sqlite3_step(stmt) == SQLITE_DONE) {
        new_id = sqlite3_last_insert_rowid(db_helper_->GetDatabase());
    }
    sqlite3_finalize(stmt);
    return new_id;
}

bool AlarmRepositoryImpl::UpdateAlarm(const AlarmEntity& alarm) {
    if (!db_helper_ || !db_helper_->IsOpen() || !alarm.IsValid() || alarm.id <= 0) {
        return false;
    }
    const char* sql = R"(
        UPDATE alarms SET
            label = ?, hour = ?, minute = ?, is_enabled = ?, repeat_mode = ?,
            days_bitmask = ?, volume = ?, crescendo_seconds = ?, vibration_pattern = ?,
            vibration_intensity = ?, force_speaker = ?, ringtone_type = ?,
            ringtone_uri = ?, ringtone_fallback_uri = ?, snooze_interval_minutes = ?,
            snooze_max_count = ?, challenge_type = ?, challenge_difficulty = ?,
            tts_enabled = ?, next_trigger_time = ?, updated_at = ?
        WHERE id = ?;
    )";

    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }

    sqlite3_bind_text(stmt, 1, alarm.label.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, alarm.hour);
    sqlite3_bind_int(stmt, 3, alarm.minute);
    sqlite3_bind_int(stmt, 4, alarm.is_enabled ? 1 : 0);
    sqlite3_bind_int(stmt, 5, static_cast<int>(alarm.repeat_mode));
    sqlite3_bind_int(stmt, 6, alarm.days_bitmask);
    sqlite3_bind_int(stmt, 7, alarm.volume);
    sqlite3_bind_int(stmt, 8, alarm.crescendo_seconds);
    sqlite3_bind_text(stmt, 9, alarm.vibration_pattern.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 10, alarm.vibration_intensity);
    sqlite3_bind_int(stmt, 11, alarm.force_speaker ? 1 : 0);
    sqlite3_bind_text(stmt, 12, alarm.ringtone_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 13, alarm.ringtone_uri.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_text(stmt, 14, alarm.ringtone_fallback_uri.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 15, alarm.snooze_interval_minutes);
    sqlite3_bind_int(stmt, 16, alarm.snooze_max_count);
    sqlite3_bind_text(stmt, 17, alarm.challenge_type.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 18, alarm.challenge_difficulty);
    sqlite3_bind_int(stmt, 19, alarm.tts_enabled ? 1 : 0);
    sqlite3_bind_int64(stmt, 20, alarm.next_trigger_time);
    sqlite3_bind_int64(stmt, 21, alarm.updated_at);
    sqlite3_bind_int64(stmt, 22, alarm.id);

    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

bool AlarmRepositoryImpl::DeleteAlarm(int64_t alarm_id) {
    if (!db_helper_ || !db_helper_->IsOpen() || alarm_id <= 0) {
        return false;
    }
    const char* sql = "DELETE FROM alarms WHERE id = ?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int64(stmt, 1, alarm_id);
    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

static void PopulateAlarmFromStmt(sqlite3_stmt* stmt, AlarmEntity* out_alarm) {
    out_alarm->id = sqlite3_column_int64(stmt, 0);
    const unsigned char* label = sqlite3_column_text(stmt, 1);
    out_alarm->label = label ? reinterpret_cast<const char*>(label) : "";
    out_alarm->hour = sqlite3_column_int(stmt, 2);
    out_alarm->minute = sqlite3_column_int(stmt, 3);
    out_alarm->is_enabled = (sqlite3_column_int(stmt, 4) != 0);
    out_alarm->repeat_mode = static_cast<RepeatMode>(sqlite3_column_int(stmt, 5));
    out_alarm->days_bitmask = sqlite3_column_int(stmt, 6);
    out_alarm->volume = sqlite3_column_int(stmt, 7);
    out_alarm->crescendo_seconds = sqlite3_column_int(stmt, 8);
    const unsigned char* pattern = sqlite3_column_text(stmt, 9);
    out_alarm->vibration_pattern = pattern ? reinterpret_cast<const char*>(pattern) : "HEARTBEAT";
    out_alarm->vibration_intensity = sqlite3_column_int(stmt, 10);
    out_alarm->force_speaker = (sqlite3_column_int(stmt, 11) != 0);
    const unsigned char* rtype = sqlite3_column_text(stmt, 12);
    out_alarm->ringtone_type = rtype ? reinterpret_cast<const char*>(rtype) : "LOCAL";
    const unsigned char* ruri = sqlite3_column_text(stmt, 13);
    out_alarm->ringtone_uri = ruri ? reinterpret_cast<const char*>(ruri) : "";
    const unsigned char* rfuri = sqlite3_column_text(stmt, 14);
    out_alarm->ringtone_fallback_uri = rfuri ? reinterpret_cast<const char*>(rfuri) : "";
    out_alarm->snooze_interval_minutes = sqlite3_column_int(stmt, 15);
    out_alarm->snooze_max_count = sqlite3_column_int(stmt, 16);
    const unsigned char* ctype = sqlite3_column_text(stmt, 17);
    out_alarm->challenge_type = ctype ? reinterpret_cast<const char*>(ctype) : "NONE";
    out_alarm->challenge_difficulty = sqlite3_column_int(stmt, 18);
    out_alarm->tts_enabled = (sqlite3_column_int(stmt, 19) != 0);
    out_alarm->next_trigger_time = sqlite3_column_int64(stmt, 20);
    out_alarm->created_at = sqlite3_column_int64(stmt, 21);
    out_alarm->updated_at = sqlite3_column_int64(stmt, 22);
}

bool AlarmRepositoryImpl::GetAlarmById(int64_t alarm_id, AlarmEntity* out_alarm) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_alarm || alarm_id <= 0) {
        return false;
    }
    const char* sql = "SELECT * FROM alarms WHERE id = ?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int64(stmt, 1, alarm_id);
    bool found = false;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        PopulateAlarmFromStmt(stmt, out_alarm);
        found = true;
    }
    sqlite3_finalize(stmt);
    return found;
}

bool AlarmRepositoryImpl::GetAllAlarms(std::vector<AlarmEntity>* out_alarms) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_alarms) {
        return false;
    }
    out_alarms->clear();
    const char* sql = "SELECT * FROM alarms ORDER BY hour ASC, minute ASC;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        AlarmEntity alarm;
        PopulateAlarmFromStmt(stmt, &alarm);
        out_alarms->push_back(alarm);
    }
    sqlite3_finalize(stmt);
    return true;
}

bool AlarmRepositoryImpl::GetEnabledAlarms(std::vector<AlarmEntity>* out_alarms) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_alarms) {
        return false;
    }
    out_alarms->clear();
    const char* sql = "SELECT * FROM alarms WHERE is_enabled = 1 ORDER BY next_trigger_time ASC;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        AlarmEntity alarm;
        PopulateAlarmFromStmt(stmt, &alarm);
        out_alarms->push_back(alarm);
    }
    sqlite3_finalize(stmt);
    return true;
}

bool AlarmRepositoryImpl::AddSkipDate(int64_t alarm_id, const std::string& skip_date) {
    if (!db_helper_ || !db_helper_->IsOpen() || alarm_id <= 0 || skip_date.empty()) {
        return false;
    }
    const char* sql =
        "INSERT OR REPLACE INTO alarm_skip_rules (alarm_id, skip_date, created_at) VALUES (?, ?, "
        "?);";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int64(stmt, 1, alarm_id);
    sqlite3_bind_text(stmt, 2, skip_date.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(stmt, 3, 0);

    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

bool AlarmRepositoryImpl::RemoveSkipDate(int64_t alarm_id, const std::string& skip_date) {
    if (!db_helper_ || !db_helper_->IsOpen() || alarm_id <= 0) {
        return false;
    }
    const char* sql = "DELETE FROM alarm_skip_rules WHERE alarm_id = ? AND skip_date = ?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int64(stmt, 1, alarm_id);
    sqlite3_bind_text(stmt, 2, skip_date.c_str(), -1, SQLITE_TRANSIENT);

    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

bool AlarmRepositoryImpl::GetSkipDates(int64_t alarm_id, std::vector<std::string>* out_dates) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_dates || alarm_id <= 0) {
        return false;
    }
    out_dates->clear();
    const char* sql =
        "SELECT skip_date FROM alarm_skip_rules WHERE alarm_id = ? ORDER BY skip_date ASC;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int64(stmt, 1, alarm_id);
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        const unsigned char* text = sqlite3_column_text(stmt, 0);
        if (text) {
            out_dates->push_back(reinterpret_cast<const char*>(text));
        }
    }
    sqlite3_finalize(stmt);
    return true;
}

bool AlarmRepositoryImpl::ClearExpiredSkipDates(const std::string& current_date) {
    if (!db_helper_ || !db_helper_->IsOpen() || current_date.empty()) {
        return false;
    }
    const char* sql = "DELETE FROM alarm_skip_rules WHERE skip_date < ?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_text(stmt, 1, current_date.c_str(), -1, SQLITE_TRANSIENT);
    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

}  // namespace edom::alarm::data
