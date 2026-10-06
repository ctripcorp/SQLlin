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
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

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
        execute(databaseScope)
        return result
    }

    private val invalidationTracker = InvalidationTracker(databaseConnection)

    /**
     * Runs the statements of [databaseScope], then tells the observed queries what they changed. They are told even
     * when a statement fails, as the ones before it may have changed something.
     */
    private fun execute(databaseScope: DatabaseScope) {
        val changes = databaseScope.changes
        val writtenTables = databaseScope.writtenTables
        try {
            databaseScope.executeAllStatements()
        } finally {
            invalidationTracker.refresh(changes, writtenTables)
        }
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
            execute(databaseScope)
        }
        return result
    }

    /**
     * Observes the results of the SELECT that [query] builds: the flow emits them when it is collected, and again
     * whenever a statement run through this database changes one of the tables the SELECT reads, if the results differ.
     *
     * Example:
     * ```kotlin
     * val adults: Flow<List<Person>> = database.observe {
     *     PersonTable SELECT WHERE(PersonTable.age GTE 18)
     * }
     * ```
     *
     * [query] runs again to build the SELECT each time, and should only build it. The tables it reads are watched
     * whatever the SELECT is: a join or a compound SELECT watches all of its tables, and a view the tables it reads. Changes are counted by SQLite
     * itself, so rows that a foreign key action changes count, a rolled back transaction doesn't, and neither does an
     * UPDATE or DELETE that matches no rows. Several changes in a row may lead to a single query.
     *
     * Only changes made through this database are seen, not those of another database instance or connection on the
     * same file, or of another process. To combine the results of several SELECTs, combine the flows that observe them;
     * as each runs its query on its own, a change to the tables of both may briefly show the new results of one with the
     * old results of the other.
     *
     * @param context The context the queries run in
     * @param query Builds the SELECT to observe
     * @return A flow of the SELECT's results
     */
    @ExperimentalDSLDatabaseAPI
    public fun <R> observe(context: CoroutineContext = Dispatchers.IO, query: DatabaseScope.() -> SelectStatement<R>): Flow<List<R>> =
        flow {
            // The versions of the tables read when the results were last queried
            var queriedVersions: Map<String, Long>? = null
            invalidationTracker.versions.collect { versions ->
                val queried = queriedVersions
                if (queried != null && queried.all { (table, version) -> (versions[table] ?: 0L) == version })
                    return@collect
                val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
                val statement = databaseScope.query()
                executiveMutex.withLock {
                    // Tracked before the SELECT runs, so that no change after it is missed
                    val tables = invalidationTracker.track(statement.tables)
                    queriedVersions = tables.associateWith { invalidationTracker.versions.value[it] ?: 0L }
                    execute(databaseScope)
                }
                emit(statement.getResults())
            }
        }.distinctUntilChanged().flowOn(context)
}