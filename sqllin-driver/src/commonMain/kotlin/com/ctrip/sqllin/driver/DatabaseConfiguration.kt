package com.ctrip.sqllin.driver

/**
 * Database configuration params in sqllin-driver
 * @author yaqiao
 */

public data class DatabaseConfiguration(
    val name: String,
    val path: DatabasePath,
    val version: Int,
    val isReadOnly: Boolean = false,
    val inMemory: Boolean = false,
    val journalMode: JournalMode = JournalMode.WAL,
    val synchronousMode: SynchronousMode = SynchronousMode.NORMAL,
    val busyTimeout: Int = 5000,
    val lookasideSlotSize: Int = 0,
    val lookasideSlotCount: Int = 0,
    val create: (databaseConnection: DatabaseConnection) -> Unit = {},
    val upgrade: (databaseConnection: DatabaseConnection, oldVersion: Int, newVersion: Int) -> Unit = { _, _, _ -> },
)