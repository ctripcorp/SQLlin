/*
 * Copyright (C) 2025 Ctrip.com.
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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.JournalMode
import com.ctrip.sqllin.driver.SynchronousMode

/**
 * DSL database configuration
 * @author Yuang Qiao
 */

public data class DSLDBConfiguration(
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
    val create: DatabaseScope.() -> Unit = {},
    val upgrade: DatabaseScope.(oldVersion: Int, newVersion: Int) -> Unit = { _, _ -> },
) {
    internal infix fun convertToDatabaseConfiguration(enableSimpleSQLLog: Boolean): DatabaseConfiguration = DatabaseConfiguration(
        name,
        path,
        version,
        isReadOnly,
        inMemory,
        journalMode,
        synchronousMode,
        busyTimeout,
        lookasideSlotSize,
        lookasideSlotCount,
        create = {
            val database = Database(it, enableSimpleSQLLog)
            database {
                create()
            }
        },
        upgrade = { databaseConnection, oldVersion, newVersion ->
            val database = Database(databaseConnection, enableSimpleSQLLog)
            database {
                upgrade(oldVersion, newVersion)
            }
        }
    )
}
