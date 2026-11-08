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
import com.ctrip.sqllin.dsl.sql.statement.Changes
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
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
     * Unlike [suspendedScope], it isn't serialized with the scopes of other threads, nor with the queries of
     * [observe], which run on `Dispatchers.IO` by default: on the JVM and Kotlin/Native, where the connection is locked
     * for each statement rather than for a transaction, their statements can run in its transaction. To share the
     * database between threads, use [suspendedScope].
     *
     * @param block The DSL block containing SQL operations
     * @return The result of the block
     */
    public operator fun <T> invoke(block: DatabaseScope.() -> T): T {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        notifyingChanges { execute(databaseScope) }
        return result
    }

    /**
     * The tracker of the tables that observed queries and versioned reads read, which the first of them creates, so
     * that the code of a database that uses neither doesn't reach the tracker, which code shrinkers then remove.
     */
    @Volatile
    private var invalidationTracker: InvalidationTracker? = null

    private suspend fun invalidationTracker(): InvalidationTracker =
        invalidationTracker ?: executiveMutex.withLock {
            invalidationTracker ?: InvalidationTracker(databaseConnection).also { invalidationTracker = it }
        }

    /**
     * Runs the statements of [databaseScope], then tells the observed queries what they changed. They are told even
     * when a statement fails, as the ones before it may have changed something, and then a failure to tell them is
     * added to the statement's, rather than taking its place.
     */
    private fun execute(databaseScope: DatabaseScope) {
        val changes = databaseScope.changes
        val writtenTables = databaseScope.writtenTables
        try {
            databaseScope.executeAllStatements()
        } catch (e: Throwable) {
            runCatching { invalidationTracker?.refresh(changes, writtenTables) }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        }
        invalidationTracker?.refresh(changes, writtenTables)
    }

    /**
     * Runs [run], which executes a scope, then the callbacks of [onChange] whose tables it changed. They run once the
     * executive lock is released, so that a callback that uses the database doesn't wait for it forever, and even when
     * a statement fails, as the ones before it may have changed something.
     */
    private inline fun notifyingChanges(run: () -> Unit) {
        try {
            run()
        } catch (e: Throwable) {
            runCatching { invalidationTracker?.dispatch() }.exceptionOrNull()?.let(e::addSuppressed)
            throw e
        }
        invalidationTracker?.dispatch()
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
        notifyingChanges {
            executiveMutex.withLock {
                execute(databaseScope)
            }
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
     * same file, or of another process. The queries run in [context], on another thread than the one that writes, so
     * write through [suspendedScope], which they are serialized with, rather than [invoke], which they aren't. To combine the results of several SELECTs, combine the flows that observe them;
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
            val tracker = invalidationTracker()
            // The versions of the tables read when the results were last queried
            var queriedVersions: Map<String, Long>? = null
            tracker.versions.collect { versions ->
                val queried = queriedVersions
                if (queried != null && queried.all { (table, version) -> (versions[table] ?: 0L) == version })
                    return@collect
                val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
                val statement = databaseScope.query()
                executiveMutex.withLock {
                    // Tracked before the SELECT runs, so that no change after it is missed
                    val tables = tracker.track(statement.tables)
                    queriedVersions = tables.associateWith { tracker.versions.value[it] ?: 0L }
                    execute(databaseScope)
                }
                emit(statement.getResults())
            }
        }.distinctUntilChanged().flowOn(context)

    /**
     * Runs the read-only scope [block], and returns its result with a token of the versions of the tables its SELECTs
     * read, so that a reader such as a `PagingSource` learns when they change, which [awaitChange] and [onChange] tell.
     *
     * Example:
     * ```kotlin
     * val (page, since) = database.versioned {
     *     PersonTable SELECT ORDER_BY(PersonTable.age to ASC) LIMIT 20 OFFSET 40
     * }
     * val persons = page.getResults()
     * // Once a statement run through this database changes the person table
     * database.awaitChange(since)
     * ```
     *
     * The tables are tracked, and their versions captured, under the lock that the SELECTs run under, before they run,
     * so that no change made after them is missed; a change they already include may make the token stale too. A view
     * stands for the tables it reads, and a subquery's tables count. The token only sees the changes made through this
     * database, as [observe] does. To cover reads of several scopes, as when a query is built from the results of
     * another, combine their tokens with [TableVersions.plus].
     *
     * @param block Builds the SELECTs to run, which are its only statements
     * @return The result of [block], and the token of the versions of the tables its SELECTs read
     * @throws IllegalArgumentException if [block] writes, as its token couldn't tell its own changes from others',
     * in which case none of its statements run
     */
    @ExperimentalDSLDatabaseAPI
    public suspend fun <T> versioned(block: suspend DatabaseScope.() -> T): Pair<T, TableVersions> {
        val databaseScope = DatabaseScope(databaseConnection, enableSimpleSQLLog)
        val result = databaseScope.block()
        require(databaseScope.changes == Changes.NONE) {
            "versioned { } only runs SELECTs, as its token couldn't tell its own changes from others': write in another scope."
        }
        val tracker = invalidationTracker()
        val versions = executiveMutex.withLock {
            // Tracked before the SELECTs run, so that no change after them is missed
            val tables = tracker.track(databaseScope.readTables)
            val versions = tables.associateWith { tracker.versions.value[it] ?: 0L }
            execute(databaseScope)
            versions
        }
        return result to TableVersions(tracker, versions)
    }

    /**
     * Suspends until a statement run through this database changes one of the tables of [since], or returns at once
     * if one already has.
     *
     * @throws IllegalArgumentException if [since] is a token of another database
     */
    @ExperimentalDSLDatabaseAPI
    public suspend fun awaitChange(since: TableVersions) {
        trackerOf(since).versions.first { since.isStaleIn(it) }
    }

    /**
     * Registers [callback] to run once, when a statement run through this database changes one of the tables of
     * [since], unless one already has: then it registers nothing and returns null, so that the caller acts on the change
     * itself, as a `PagingSource` returns `LoadResult.Invalid()`. It registers before it checks, so that a change between
     * the read and the registration either makes it return null or runs [callback], never neither.
     *
     * [callback] runs on the thread that wrote, once the writing scope has released the database's lock, so it may use
     * the database, but should return quickly, as the scope's caller waits for it. The registration holds [callback]
     * until it runs or is closed.
     *
     * @return What closes the registration, so that [callback] no longer runs, or null when [since] is already stale
     * @throws IllegalArgumentException if [since] is a token of another database
     */
    @ExperimentalDSLDatabaseAPI
    public fun onChange(since: TableVersions, callback: () -> Unit): AutoCloseable? =
        trackerOf(since).register(since, callback)

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    private fun trackerOf(since: TableVersions): InvalidationTracker {
        val tracker = invalidationTracker
        require(tracker != null && since.tracker === tracker) { "The token belongs to another database, whose changes this one doesn't see." }
        return tracker
    }
}