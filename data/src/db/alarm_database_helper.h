#pragma once

#include <sqlite3.h>

#include <memory>
#include <string>

namespace edom::alarm::data {

class AlarmDatabaseHelper {
   public:
    explicit AlarmDatabaseHelper(const std::string& db_path);
    ~AlarmDatabaseHelper();

    // Open connection and initialize tables and indexes
    bool Open();
    void Close();

    sqlite3* GetDatabase() const { return db_; }
    bool IsOpen() const { return db_ != nullptr; }

    // Transaction utilities
    bool BeginTransaction();
    bool CommitTransaction();
    bool RollbackTransaction();

    // Execute arbitrary non-query statement
    bool Execute(const std::string& sql);

   private:
    bool CreateTables();

    std::string db_path_;
    sqlite3* db_ = nullptr;
};

}  // namespace edom::alarm::data
