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
import com.ctrip.sqllin.driver.platform.separatorChar
import com.ctrip.sqllin.driver.platform.withLock
import platform.posix.remove

/**
 * SQLite extension Native
 * @author yaqiao
 */

public fun String.toDatabasePath(): DatabasePath = NativeDatabasePath(this)

internal value class NativeDatabasePath(val pathString: String) : DatabasePath

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
            migrateIfNeeded(config.create, config.upgrade, config.version)
        } catch (e: Exception) {
            // If this failed, we have to close the connection, or we will end up leaking it.
            println("attempted to run migration and failed. closing connection.")
            close()
            throw e
        }
    }
    if (config.isReadOnly) realConnection else ConcurrentDatabaseConnection(realConnection)
}

private fun DatabaseConfiguration.diskOrMemoryPath(): String =
    if (inMemory) {
        if (name.isBlank())
            ":memory:"
        else
            "file:$name?mode=memory&cache=shared"
    } else {
        require(name.isNotBlank()) { "Database name cannot be blank" }
        getDatabaseFullPath((path as NativeDatabasePath).pathString, name)
    }

private fun getDatabaseFullPath(dirPath: String, name: String): String {
    val param = when {
        dirPath.isEmpty() -> name
        name.isEmpty() -> dirPath
        else -> join(dirPath, name)
    }
    return fixSlashes(param)
}

private fun join(prefix: String, suffix: String): String {
    val haveSlash = (prefix.isNotEmpty() && prefix.last() == separatorChar)
            || (suffix.isNotEmpty() && suffix.first() == separatorChar)
    return buildString {
        append(prefix)
        if (!haveSlash)
            append(separatorChar)
        append(suffix)
    }
}

private fun fixSlashes(origPath: String): String {
    // Remove duplicate adjacent slashes.
    var lastWasSlash = false
    val newPath = origPath.toCharArray()
    val length = newPath.size
    var newLength = 0
    val initialIndex = if (origPath.startsWith("file://", true)) 7 else 0
    for (i in initialIndex ..< length) {
        val ch = newPath[i]
        if (ch == separatorChar) {
            if (!lastWasSlash) {
                newPath[newLength++] = separatorChar
                lastWasSlash = true
            }
        } else {
            newPath[newLength++] = ch
            lastWasSlash = false
        }
    }
    // Remove any trailing slash (unless this is the root of the file system).
    if (lastWasSlash && newLength > 1) {
        newLength--
    }

    // Reuse the original string if possible.
    return if (newLength != length) buildString(newLength) {
        append(newPath)
        setLength(newLength)
    } else origPath
}

public actual fun deleteDatabase(path: DatabasePath, name: String): Boolean {
    val baseName = getDatabaseFullPath((path as NativeDatabasePath).pathString, name)
    remove("$baseName-shm")
    remove("$baseName-wal")
    remove("$baseName-journal")
    val result = remove(baseName) == 0
    if (!result)
        println("Delete the database file failed, file path: $baseName")
    return result
}
