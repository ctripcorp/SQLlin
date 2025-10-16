/*
 * Copyright (C) 2022 Ctrip.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrip.sqllin.driver

import com.ctrip.sqllin.driver.cinterop.NativeDatabase.Companion.openNativeDatabase
import com.ctrip.sqllin.driver.platform.Lock
import com.ctrip.sqllin.driver.platform.withLock
import platform.posix.remove

/**
 * Converts a String path to a [DatabasePath] for native platforms.
 */
public fun String.toDatabasePath(): DatabasePath = StringDatabasePath(this)

private val connectionCreationLock = Lock()
public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection = connectionCreationLock.withLock {
    val realDatabasePath = config.diskOrMemoryPath()
    println("Database full path: $realDatabasePath")
    val database = openNativeDatabase(config, realDatabasePath)
    val realConnection = RealDatabaseConnection(database)
    realConnection.apply {
        updateSynchronousMode(config.synchronousMode)
        updateJournalMode(config.journalMode)
        try {
            migrateIfNeeded(config.create, config.upgrade, config.version, database.isActualReadOnly)
        } catch (e: Exception) {
            // If this failed, we have to close the connection, or we will end up leaking it.
            println("attempted to run migration and failed. closing connection.")
            close()
            throw e
        }
    }
    if (config.isReadOnly) realConnection else ConcurrentDatabaseConnection(realConnection)
}

public actual fun deleteDatabase(path: DatabasePath, name: String): Boolean {
    val baseName = getDatabaseFullPath((path as StringDatabasePath).pathString, name)
    remove("$baseName-shm")
    remove("$baseName-wal")
    remove("$baseName-journal")
    val result = remove(baseName) == 0
    if (!result)
        println("Delete the database file failed, file path: $baseName")
    return result
}
