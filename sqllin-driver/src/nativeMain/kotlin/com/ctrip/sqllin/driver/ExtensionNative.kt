package com.ctrip.sqllin.driver

import co.touchlab.sqliter.DatabaseConfiguration.Extended
import co.touchlab.sqliter.SynchronousFlag
import co.touchlab.sqliter.createDatabaseManager

/**
 * SQLite extension iOS
 * @author yaqiao
 */

internal typealias NativeDatabaseConnection = co.touchlab.sqliter.DatabaseConnection
internal typealias NativeDatabaseConfiguration = co.touchlab.sqliter.DatabaseConfiguration
internal typealias NativeJournalMode = co.touchlab.sqliter.JournalMode

public fun String.toDatabasePath(): DatabasePath = NativeDatabasePath(this)

internal value class NativeDatabasePath internal constructor(val pathString: String) : DatabasePath

public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    val (name, path, version, isReadOnly, inMemory, journalMode, synchronousMode, busyTimeout, lookasideSlotSize, lookasideSlotCount, create, upgrade) = config
    val configNative = NativeDatabaseConfiguration(
        name = name,
        version = version,
        create = {
            create(DatabaseConnectionImpl(it))
        },
        upgrade = { connection, oldVersion, newVersion ->
            upgrade(DatabaseConnectionImpl(connection), oldVersion, newVersion)
        },
        inMemory = inMemory,
        journalMode = when (journalMode) {
            JournalMode.DELETE -> NativeJournalMode.DELETE
            JournalMode.WAL -> NativeJournalMode.WAL
        },
        extendedConfig = Extended(
            busyTimeout = busyTimeout,
            basePath = (path as NativeDatabasePath).pathString,
            synchronousFlag = when (synchronousMode) {
                SynchronousMode.OFF -> SynchronousFlag.OFF
                SynchronousMode.NORMAL -> SynchronousFlag.NORMAL
                SynchronousMode.FULL -> SynchronousFlag.FULL
                SynchronousMode.EXTRA -> SynchronousFlag.EXTRA
            },
            lookasideSlotSize = lookasideSlotSize,
            lookasideSlotCount = lookasideSlotCount,
        )
    )
    val databaseManager = createDatabaseManager(configNative)
    return DatabaseConnectionImpl(
        if (isReadOnly)
            databaseManager.createSingleThreadedConnection()
        else
            databaseManager.createMultiThreadedConnection()
    )
}