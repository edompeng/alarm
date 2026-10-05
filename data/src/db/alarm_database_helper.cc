#include "data/src/db/alarm_database_helper.h"

#include <iostream>

namespace edom::alarm::data {

AlarmDatabaseHelper::AlarmDatabaseHelper(const std::string& db_path) : db_path_(db_path) {}

AlarmDatabaseHelper::~AlarmDatabaseHelper() { Close(); }

bool AlarmDatabaseHelper::Open() {
    if (db_ != nullptr) {
        return true;
    }
    int rc = sqlite3_open(db_path_.c_str(), &db_);
    if (rc != SQLITE_OK) {
        std::cerr << "Failed to open database: " << sqlite3_errmsg(db_) << std::endl;
        Close();
        return false;
    }
    return CreateTables();
}

void AlarmDatabaseHelper::Close() {
    if (db_ != nullptr) {
        sqlite3_close(db_);
        db_ = nullptr;
    }
}

bool AlarmDatabaseHelper::BeginTransaction() { return Execute("BEGIN TRANSACTION;"); }

bool AlarmDatabaseHelper::CommitTransaction() { return Execute("COMMIT;"); }

bool AlarmDatabaseHelper::RollbackTransaction() { return Execute("ROLLBACK;"); }

bool AlarmDatabaseHelper::Execute(const std::string& sql) {
    if (db_ == nullptr) {
        return false;
    }
    char* err_msg = nullptr;
    int rc = sqlite3_exec(db_, sql.c_str(), nullptr, nullptr, &err_msg);
    if (rc != SQLITE_OK) {
        if (err_msg != nullptr) {
            std::cerr << "SQLite error: " << err_msg << std::endl;
            sqlite3_free(err_msg);
        }
        return false;
    }
    return true;
}

bool AlarmDatabaseHelper::CreateTables() {
    const std::string sql = R"(
        CREATE TABLE IF NOT EXISTS alarms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            label TEXT NOT NULL DEFAULT '',
            hour INTEGER NOT NULL CHECK (hour >= 0 AND hour <= 23),
            minute INTEGER NOT NULL CHECK (minute >= 0 AND minute <= 59),
            is_enabled INTEGER NOT NULL DEFAULT 1 CHECK (is_enabled IN (0, 1)),
            repeat_mode INTEGER NOT NULL DEFAULT 0 CHECK (repeat_mode IN (0, 1, 2)),
            days_bitmask INTEGER NOT NULL DEFAULT 0,
            volume INTEGER NOT NULL DEFAULT 80 CHECK (volume >= 0 AND volume <= 100),
            crescendo_seconds INTEGER NOT NULL DEFAULT 15 CHECK (crescendo_seconds >= 0 AND crescendo_seconds <= 30),
            vibration_pattern TEXT NOT NULL DEFAULT 'HEARTBEAT',
            vibration_intensity INTEGER NOT NULL DEFAULT 80 CHECK (vibration_intensity >= 0 AND vibration_intensity <= 100),
            force_speaker INTEGER NOT NULL DEFAULT 1 CHECK (force_speaker IN (0, 1)),
            ringtone_type TEXT NOT NULL DEFAULT 'LOCAL',
            ringtone_uri TEXT NOT NULL DEFAULT 'content://settings/system/alarm_alert',
            ringtone_fallback_uri TEXT NOT NULL DEFAULT 'content://settings/system/alarm_alert',
            snooze_interval_minutes INTEGER NOT NULL DEFAULT 10 CHECK (snooze_interval_minutes >= 1 AND snooze_interval_minutes <= 60),
            snooze_max_count INTEGER NOT NULL DEFAULT 3,
            challenge_type TEXT NOT NULL DEFAULT 'NONE',
            challenge_difficulty INTEGER NOT NULL DEFAULT 1,
            tts_enabled INTEGER NOT NULL DEFAULT 0 CHECK (tts_enabled IN (0, 1)),
            next_trigger_time INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_alarms_next_trigger ON alarms(is_enabled, next_trigger_time);

        CREATE TABLE IF NOT EXISTS alarm_skip_rules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            alarm_id INTEGER NOT NULL REFERENCES alarms(id) ON DELETE CASCADE,
            skip_date TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            UNIQUE(alarm_id, skip_date)
        );

        CREATE INDEX IF NOT EXISTS idx_alarm_skip_date ON alarm_skip_rules(alarm_id, skip_date);

        CREATE TABLE IF NOT EXISTS holiday_calendar (
            date_str TEXT PRIMARY KEY,
            year INTEGER NOT NULL,
            day_type INTEGER NOT NULL CHECK (day_type IN (0, 1, 2, 3)),
            name TEXT NOT NULL DEFAULT '',
            updated_at INTEGER NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_holiday_year ON holiday_calendar(year);

        CREATE TABLE IF NOT EXISTS app_configurations (
            config_key TEXT PRIMARY KEY,
            config_value TEXT NOT NULL,
            updated_at INTEGER NOT NULL
        );
    )";

    return Execute(sql);
}

}  // namespace edom::alarm::data
