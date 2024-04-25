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

import com.ctrip.sqllin.driver.platform.separatorChar
import kotlin.jvm.JvmInline

/**
 * SQLite extension function
 * @author yaqiao
 */

/**
 * Abstract database path, it is 'Context' in Android, and 'String' in native targets.
 * DO NOT implementation 'DatabasePath' by yourself!!!
 */
public interface DatabasePath

public expect fun openDatabase(config: DatabaseConfiguration): DatabaseConnection

public inline fun <T> openDatabase(config: DatabaseConfiguration, block: (DatabaseConnection) -> T): T {
    val connection = openDatabase(config)
    try {
        return block(connection)
    } finally {
        connection.close()
    }
}

public inline fun <T> DatabaseConnection.withTransaction(block: (DatabaseConnection) -> T): T {
    beginTransaction()
    try {
        val result = block(this)
        setTransactionSuccessful()
        return result
    } finally {
        endTransaction()
    }
}

public inline fun <T> DatabaseConnection.withQuery(
    sql: String,
    bindParams: Array<String?>? = null,
    block: (CommonCursor) -> T,
): T {
    val commonCursor = query(sql, bindParams)
    try {
        return block(commonCursor)
    } finally {
        commonCursor.close()
    }
}

public expect fun deleteDatabase(path: DatabasePath, name: String): Boolean

internal infix fun DatabaseConnection.updateSynchronousMode(mode: SynchronousMode) {
    val currentJournalMode = withQuery("PRAGMA synchronous;") {
        it.next()
        it.getInt(0)
    }
    if (currentJournalMode != mode.value)
        execSQL("PRAGMA synchronous=${mode.value};")
}

internal infix fun DatabaseConnection.updateJournalMode(mode: JournalMode) {
    val currentJournalMode = withQuery("PRAGMA journal_mode;") {
        it.next()
        it.getString(0)
    }
    if (!currentJournalMode.equals(mode.name, ignoreCase = true))
        withQuery("PRAGMA journal_mode=${mode.name};") {}
}

internal fun DatabaseConnection.migrateIfNeeded(
    create: (DatabaseConnection) -> Unit,
    upgrade: (DatabaseConnection, Int, Int) -> Unit,
    version: Int,
    isActualReadOnly: Boolean,
) = withTransaction {
    val initialVersion = withQuery("PRAGMA user_version;") {
        it.next()
        it.getInt(0)
    }
    if (initialVersion == 0) {
        create(this)
        execSQL("PRAGMA user_version = $version;")
    } else if (initialVersion != version) {
        if (initialVersion > version)
            throw IllegalStateException("Database version $initialVersion newer than config version $version")
        if (isActualReadOnly)
            throw IllegalArgumentException("Under the ready-only mode, you should ensure your version parameter same with the version in database file")
        upgrade(this, initialVersion, version)
        execSQL("PRAGMA user_version = $version;")
    }
}

internal fun DatabaseConfiguration.diskOrMemoryPath(): String =
    if (inMemory) {
        if (name.isBlank())
            ":memory:"
        else
            "file:$name?mode=memory&cache=shared"
    } else {
        require(name.isNotBlank()) { "Database name cannot be blank" }
        getDatabaseFullPath((path as StringDatabasePath).pathString, name)
    }

internal fun getDatabaseFullPath(dirPath: String, name: String): String {
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

@JvmInline
internal value class StringDatabasePath(val pathString: String) : DatabasePath
