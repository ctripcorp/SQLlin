package com.ctrip.sqllin.driver

/**
 * SQLite journal mode
 * @author yaqiao
 */

public enum class JournalMode {
    DELETE, // Write-ahead logging
    WAL; // Rollback journal delete Mode
}