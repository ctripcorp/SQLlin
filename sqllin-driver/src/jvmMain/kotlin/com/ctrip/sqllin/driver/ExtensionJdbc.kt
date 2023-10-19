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

public fun String.toDatabasePath(): DatabasePath = StringDatabasePath(this)

private typealias JdbcJournalMode = SQLiteConfig.JournalMode
private typealias JdbcSynchronousMode = SQLiteConfig.SynchronousMode

private val lock = ReentrantLock()

public actual fun openDatabase(config: DatabaseConfiguration): DatabaseConnection {
    val sqliteConfig = SQLiteConfig().apply {
        setJournalMode(when (config.journalMode) {
            JournalMode.DELETE -> JdbcJournalMode.DELETE
            JournalMode.WAL -> JdbcJournalMode.WAL
        })
        setSynchronous(when (config.synchronousMode) {
            SynchronousMode.OFF -> JdbcSynchronousMode.OFF
            SynchronousMode.NORMAL -> JdbcSynchronousMode.NORMAL
            SynchronousMode.FULL, SynchronousMode.EXTRA -> JdbcSynchronousMode.FULL
        })
        busyTimeout = config.busyTimeout
    }.toProperties()
    val path = config.diskOrMemoryPath()
    println("Database full path: $path")
    val jdbcPath = "jdbc:sqlite:$path"
    return lock.withLock {
        val driverConnection = DriverManager.getConnection(jdbcPath, sqliteConfig)
        val jdbcDatabaseConnection = JdbcDatabaseConnection(driverConnection)
        val finalDatabaseConnection = if (config.isReadOnly)
            jdbcDatabaseConnection
        else
            ConcurrentDatabaseConnection(jdbcDatabaseConnection)
        try {
            finalDatabaseConnection.migrateIfNeeded(config.create, config.upgrade, config.version, driverConnection.isReadOnly)
        } catch (e: Exception) {
            // If this failed, we have to close the connection, or we will end up leaking it.
            println("attempted to run migration and failed. closing connection.")
            finalDatabaseConnection.close()
            throw e
        }
        finalDatabaseConnection
    }
}

public actual fun deleteDatabase(path: DatabasePath, name: String): Boolean {
    val baseName = getDatabaseFullPath((path as StringDatabasePath).pathString, name)
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
