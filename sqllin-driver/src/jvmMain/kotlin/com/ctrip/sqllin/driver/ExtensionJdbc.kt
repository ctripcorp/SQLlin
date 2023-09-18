/*
 * Copyright (C) 2023 Ctrip.com.
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

import org.sqlite.SQLiteConfig
import java.io.File
import java.sql.DriverManager
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * SQLite extension JDBC
 * @author yaqiao
 */

public fun String.toDatabasePath(): DatabasePath = JDBCDatabasePath(this)

@JvmInline
internal value class JDBCDatabasePath(val pathString: String) : DatabasePath

private typealias JDBCJournalMode = SQLiteConfig.JournalMode
private typealias JDBCSynchronousMode = SQLiteConfig.SynchronousMode

private val lock = ReentrantLock()

public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    val sqliteConfig = SQLiteConfig().apply {
        setJournalMode(when (config.journalMode) {
            JournalMode.DELETE -> JDBCJournalMode.DELETE
            JournalMode.WAL -> JDBCJournalMode.WAL
        })
        setSynchronous(when (config.synchronousMode) {
            SynchronousMode.OFF -> JDBCSynchronousMode.OFF
            SynchronousMode.NORMAL -> JDBCSynchronousMode.NORMAL
            SynchronousMode.FULL, SynchronousMode.EXTRA -> JDBCSynchronousMode.FULL
        })
        busyTimeout = config.busyTimeout
    }.toProperties()
    val path = config.diskOrMemoryPath()
    println("Database full path: $path")
    val jdbcPath = "jdbc:sqlite:$path"
    return lock.withLock {
        val jdbcDatabaseConnection = JdbcDatabaseConnection(DriverManager.getConnection(jdbcPath, sqliteConfig))
        val finalDatabaseConnection = if (config.isReadOnly)
            jdbcDatabaseConnection
        else
            ConcurrentDatabaseConnection(jdbcDatabaseConnection)
        finalDatabaseConnection.apply {
            try {
                migrateIfNeeded(config.create, config.upgrade, config.version)
            } catch (e: Exception) {
                // If this failed, we have to close the connection, or we will end up leaking it.
                println("attempted to run migration and failed. closing connection.")
                close()
                throw e
            }
        }
    }
}

private fun DatabaseConfiguration.diskOrMemoryPath(): String =
    if (inMemory) {
        ":memory:"
    } else {
        require(name.isNotBlank()) { "Database name cannot be blank" }
        getDatabaseFullPath((path as JDBCDatabasePath).pathString, name)
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
    val separatorChar = '/'
    val windowsSeparatorChar = '\\'
    val haveSlash = (prefix.isNotEmpty() && (prefix.last() == separatorChar || prefix.last() == windowsSeparatorChar))
            || (suffix.isNotEmpty() && (suffix.first() == separatorChar || suffix.first() == windowsSeparatorChar))
    return buildString {
        append(prefix)
        if (!haveSlash)
            append(separatorChar)
        append(suffix)
    }
}

private fun fixSlashes(origPath: String): String {
    // Remove duplicate adjacent slashes.
    val separatorChar = '/'
    val windowsSeparatorChar = '\\'
    var lastWasSlash = false
    val newPath = origPath.toCharArray()
    val length = newPath.size
    var newLength = 0
    val initialIndex = if (origPath.startsWith("file://", true)) 7 else 0
    for (i in initialIndex ..< length) {
        val ch = newPath[i]
        if (ch == separatorChar || ch == windowsSeparatorChar) {
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
    val baseName = getDatabaseFullPath((path as JDBCDatabasePath).pathString, name)
    sequenceOf(
        File("$baseName-shm"),
        File("$baseName-wal"),
        File("$baseName-journal"),
    ).forEach {
        if (it.exists())
            it.delete()
    }
    val result = File(baseName).delete()
    if (!result)
        println("Delete the database file failed, file path: $baseName")
    return result
}

internal fun DatabaseConnection.migrateIfNeeded(
    create: (DatabaseConnection) -> Unit,
    upgrade: (DatabaseConnection, Int, Int) -> Unit,
    version: Int,
) = withTransaction {
    val initialVersion = withQuery("PRAGMA user_version;") {
        it.next()
        it.getInt(0)
    }
    if (initialVersion == 0) {
        create(this)
        execSQL("PRAGMA user_version = $version;")
    } else if (initialVersion != version) {
        if (initialVersion > version) {
            throw IllegalStateException("Database version $initialVersion newer than config version $version")
        }
        upgrade(this, initialVersion, version)
        execSQL("PRAGMA user_version = $version;")
    }
}
