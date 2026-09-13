#include "data/src/repository/holiday_repository_impl.h"

#include <sqlite3.h>

namespace edom::alarm::data {

HolidayRepositoryImpl::HolidayRepositoryImpl(AlarmDatabaseHelper* db_helper)
    : db_helper_(db_helper) {}

bool HolidayRepositoryImpl::UpsertHolidayRule(const HolidayEntity& rule) {
    if (!db_helper_ || !db_helper_->IsOpen() || !rule.IsValid()) {
        return false;
    }
    const char* sql = R"(
        INSERT INTO holiday_calendar (date_str, year, day_type, name, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(date_str) DO UPDATE SET
            year = excluded.year,
            day_type = excluded.day_type,
            name = excluded.name,
            updated_at = excluded.updated_at;
    )";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_text(stmt, 1, rule.date_str.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, rule.year);
    sqlite3_bind_int(stmt, 3, static_cast<int>(rule.day_type));
    sqlite3_bind_text(stmt, 4, rule.name.c_str(), -1, SQLITE_TRANSIENT);
    sqlite3_bind_int64(stmt, 5, rule.updated_at);

    bool success = (sqlite3_step(stmt) == SQLITE_DONE);
    sqlite3_finalize(stmt);
    return success;
}

bool HolidayRepositoryImpl::BatchUpsertHolidayRules(const std::vector<HolidayEntity>& rules) {
    if (!db_helper_ || !db_helper_->IsOpen() || rules.empty()) {
        return false;
    }
    db_helper_->BeginTransaction();
    for (const auto& rule : rules) {
        if (!UpsertHolidayRule(rule)) {
            db_helper_->RollbackTransaction();
            return false;
        }
    }
    return db_helper_->CommitTransaction();
}

bool HolidayRepositoryImpl::GetRuleByDate(const std::string& date_str, HolidayEntity* out_rule) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_rule || date_str.empty()) {
        return false;
    }
    const char* sql =
        "SELECT date_str, year, day_type, name, updated_at FROM holiday_calendar WHERE date_str = "
        "?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_text(stmt, 1, date_str.c_str(), -1, SQLITE_TRANSIENT);
    bool found = false;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        const unsigned char* dstr = sqlite3_column_text(stmt, 0);
        out_rule->date_str = dstr ? reinterpret_cast<const char*>(dstr) : "";
        out_rule->year = sqlite3_column_int(stmt, 1);
        out_rule->day_type = static_cast<DayType>(sqlite3_column_int(stmt, 2));
        const unsigned char* name = sqlite3_column_text(stmt, 3);
        out_rule->name = name ? reinterpret_cast<const char*>(name) : "";
        out_rule->updated_at = sqlite3_column_int64(stmt, 4);
        found = true;
    }
    sqlite3_finalize(stmt);
    return found;
}

bool HolidayRepositoryImpl::HasYearData(int year) {
    if (!db_helper_ || !db_helper_->IsOpen() || year <= 0) {
        return false;
    }
    const char* sql = "SELECT COUNT(*) FROM holiday_calendar WHERE year = ?;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int(stmt, 1, year);
    int count = 0;
    if (sqlite3_step(stmt) == SQLITE_ROW) {
        count = sqlite3_column_int(stmt, 0);
    }
    sqlite3_finalize(stmt);
    return count > 0;
}

bool HolidayRepositoryImpl::GetRulesForYear(int year, std::vector<HolidayEntity>* out_rules) {
    if (!db_helper_ || !db_helper_->IsOpen() || !out_rules || year <= 0) {
        return false;
    }
    out_rules->clear();
    const char* sql =
        "SELECT date_str, year, day_type, name, updated_at FROM holiday_calendar WHERE year = ? "
        "ORDER BY date_str ASC;";
    sqlite3_stmt* stmt = nullptr;
    if (sqlite3_prepare_v2(db_helper_->GetDatabase(), sql, -1, &stmt, nullptr) != SQLITE_OK) {
        return false;
    }
    sqlite3_bind_int(stmt, 1, year);
    while (sqlite3_step(stmt) == SQLITE_ROW) {
        HolidayEntity rule;
        const unsigned char* dstr = sqlite3_column_text(stmt, 0);
        rule.date_str = dstr ? reinterpret_cast<const char*>(dstr) : "";
        rule.year = sqlite3_column_int(stmt, 1);
        rule.day_type = static_cast<DayType>(sqlite3_column_int(stmt, 2));
        const unsigned char* name = sqlite3_column_text(stmt, 3);
        rule.name = name ? reinterpret_cast<const char*>(name) : "";
        rule.updated_at = sqlite3_column_int64(stmt, 4);
        out_rules->push_back(rule);
    }
    sqlite3_finalize(stmt);
    return true;
}

}  // namespace edom::alarm::data
