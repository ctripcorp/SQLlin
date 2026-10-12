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

/**
 * Configuration parameters for opening a SQLite database connection.
 *
 * @property name The database filename
 * @property path The database directory path (platform-specific implementation)
 * @property version The database schema version number
 * @property isReadOnly Whether to open the database in read-only mode. Default: false
 * @property inMemory Whether to create an in-memory database. Default: false
 * @property journalMode The SQLite journal mode. Default: [JournalMode.WAL]
 * @property synchronousMode The SQLite synchronous mode. Default: [SynchronousMode.NORMAL]
 * @property busyTimeout Timeout in milliseconds for database lock waits. Default: 5000ms
 * @property lookasideSlotSize The size in bytes of each slot of SQLite's lookaside memory allocator, set together with
 * [lookasideSlotCount] when both are 0 or more. Default: -1, which leaves SQLite's default configuration; 0 disables the
 * allocator. Android 9 and higher take it when both are positive or both 0, and lower versions don't take it; on the
 * JVM, where sqlite-jdbc can't set it, it has no effect
 * @property lookasideSlotCount The number of slots of SQLite's lookaside memory allocator of each connection, set
 * together with [lookasideSlotSize]. Default: -1, which leaves SQLite's default configuration; 0 disables the allocator
 * @property create Callback invoked when creating a new database
 * @property upgrade Callback invoked when upgrading the database schema
 *
 * @author Yuang Qiao
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
    val lookasideSlotSize: Int = -1,
    val lookasideSlotCount: Int = -1,
    val create: (databaseConnection: DatabaseConnection) -> Unit = {},
    val upgrade: (databaseConnection: DatabaseConnection, oldVersion: Int, newVersion: Int) -> Unit = { _, _, _ -> },
)