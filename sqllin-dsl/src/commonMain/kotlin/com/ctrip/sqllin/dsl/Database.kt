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

import com.ctrip.sqllin.driver.DatabaseConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * High-level database interface for executing SQL operations using type-safe DSL.
 *
 * Database objects are created via factory functions in [DatabaseCreators.kt][Database]
 * and provide a scope-based API for executing SQL statements. All SQL operations must be
 * performed within a [DatabaseScope], which is entered by invoking the database object.
 *
 * Example:
 * ```kotlin
 * val database = Database(config)
 * database {
 *     PersonTable INSERT person
 *     PersonTable SELECT WHERE(PersonTable.age GTE 18)
 * }
 * database.close()
 * ```
 *
 * @author Yuang Qiao
 */
public class Database internal constructor(
    private val databaseConnection: DatabaseConnection,
    private val enableSimpleSQLLog: Boolean = false,
) {

    /**
     * Closes the database connection and releases resources.
     *
     * After closing, the database cannot be used for further operations.
     */
    public fun close(): Unit = databaseConnection.close()

    /**
     * Opens a database scope for executing SQL statements.
     *
     * All SQL operations within the block are executed when the scope exits.
     * Statements are batched and executed in order.
     *
     * @param block The DSL block containing SQL operations
     * @return The result of the block
     */
    public operator fun <T> invoke(block: DatabaseScope.() -> T): T {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        databaseScope.executeAllStatements()
        return result
    }

    private val executiveMutex by lazy { Mutex() }

    /**
     * Opens a suspendable database scope for executing SQL statements in coroutines.
     *
     * Similar to [invoke] but supports suspend functions within the block.
     * Statement execution is serialized using a mutex to ensure thread safety.
     *
     * @param block The suspending DSL block containing SQL operations
     * @return The result of the block
     */
    public suspend infix fun <T> suspendedScope(block: suspend DatabaseScope.() -> T): T {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        executiveMutex.withLock {
            databaseScope.executeAllStatements()
        }
        return result
    }
}