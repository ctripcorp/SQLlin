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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.openDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Database object
 * @author yaqiao
 */

public class Database(
    configuration: DatabaseConfiguration,
    private val enableSimpleSQLLog: Boolean = false,
) {

    public constructor(
        name: String,
        path: DatabasePath,
        version: Int,
        enableSimpleSQLLog: Boolean = false,
    ) : this(
        DatabaseConfiguration(
            name = name,
            path = path,
            version = version,
        ),
        enableSimpleSQLLog,
    )

    private val databaseConnection = openDatabase(configuration)

    /**
     * Close the database connection.
     */
    public fun close(): Unit = databaseConnection.close()

    /**
     * Start a scope with this database object that used for execute SQL.
     */
    public operator fun <T> invoke(block: DatabaseScope.() -> T): T {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        databaseScope.executeAllStatements()
        return result
    }

    private val executiveMutex by lazy { Mutex() }

    public suspend infix fun <T> suspendedScope(block: suspend DatabaseScope.() -> T): T {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        executiveMutex.withLock {
            databaseScope.executeAllStatements()
        }
        return result
    }
}