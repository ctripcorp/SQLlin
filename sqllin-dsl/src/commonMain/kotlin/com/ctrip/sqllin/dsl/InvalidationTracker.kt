/*
 * Copyright (C) 2026 Ctrip.com.
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
import com.ctrip.sqllin.driver.withQuery
import com.ctrip.sqllin.driver.withTransaction
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.statement.Changes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Tracks the changes a [Database] makes to the tables its observed queries read, so that they run again when one of
 * them changes.
 *
 * A table is tracked from the first time a query that reads it is observed. TEMP triggers on its INSERT, UPDATE and
 * DELETE add one to its count in a TEMP table, so SQLite itself reports every row the connection changes: those of
 * foreign key actions and of `INSERT OR REPLACE` too, and none for an UPDATE or DELETE that matches no rows. A rolled
 * back transaction rolls its counts back with it. After a scope that may have written runs, [refresh] reads the counts
 * into [versions], which observed queries collect. Counts, rather than a flag that is read and then cleared, can't lose
 * a change made between the two.
 *
 * A view can't have such triggers, so an observed view stands for the tables it reads, which SQLite itself lists: they
 * are the ones the program of `EXPLAIN SELECT * FROM view` opens, through views over views and joins alike. A virtual
 * table, such as an FTS table, can't have them either, so its changes are counted from the SQLlin statements that write
 * to it, INSERT, UPDATE and DELETE: a change made with other SQL isn't seen, and neither is one to a virtual table a
 * view reads, as a virtual table isn't opened that way. A rolled back transaction still counts its statements, which
 * only means the queries run once more.
 *
 * TEMP tables and triggers belong to the connection, so changes made through another connection, or by another
 * process, aren't seen. A table stays tracked until the connection closes. They are also rolled back with the
 * transaction they are created in: on the JVM and Kotlin/Native, the connection is locked for each statement rather
 * than for a transaction, so a blocking database scope on another thread can have one open while [track] creates them,
 * and its rollback takes them with it. [refresh] creates them again then.
 *
 * @author Yuang Qiao
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
internal class InvalidationTracker(private val connection: DatabaseConnection) {

    private val trackedTables = MutableStateFlow<Set<String>>(emptySet())

    /** The tracked tables that are virtual, whose counts [refresh] increments, as they have no triggers. */
    private val virtualTables = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The tables that have triggers, read while virtual tables are tracked: a trigger can write a virtual table, as one
     * that keeps an FTS table in step with a table does, which SQLlin's statements don't tell, so a write to any of these
     * tables counts as a change of every tracked virtual table.
     */
    private val triggeredTables = MutableStateFlow<Set<String>>(emptySet())

    private val _versions = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * The count of changes to each tracked table. It only grows.
     */
    val versions: StateFlow<Map<String, Long>> = _versions

    /**
     * The callbacks of [register] that wait for a change of the tables of their tokens. The list is replaced as a
     * whole, so that registering, closing and firing are atomic, from whichever thread they run on.
     */
    private val registrations = MutableStateFlow<List<Registration>>(emptyList())

    private class Registration(val since: TableVersions, val callback: () -> Unit)

    /**
     * Registers [callback] to run once a table of [since] changes, unless one already has, in which case it registers
     * nothing and returns null.
     *
     * It registers before it checks, so that a change between the read that captured [since] and the registration is
     * either seen here, or fires the callback: never neither, and never both.
     *
     * @return What closes the registration, or null when [since] is already stale
     */
    fun register(since: TableVersions, callback: () -> Unit): AutoCloseable? {
        val registration = Registration(since, callback)
        registrations.update { it + registration }
        if (since.isStaleIn(versions.value) && remove(listOf(registration)).isNotEmpty())
            return null
        return object : AutoCloseable {
            override fun close() {
                remove(listOf(registration))
            }
        }
    }

    /**
     * Removes [toRemove] from the registrations, and returns those of them that this call removed, rather than another
     * that ran at the same time.
     */
    private fun remove(toRemove: List<Registration>): List<Registration> {
        while (true) {
            val current = registrations.value
            val removed = toRemove.filter { it in current }
            if (removed.isEmpty() || registrations.compareAndSet(current, current - removed.toSet()))
                return removed
        }
    }

    /**
     * Runs the callbacks whose tokens are stale, once each, after a scope that may have written. It runs once the scope
     * released the executive lock, so that a callback that uses the database doesn't wait for it forever, on the thread
     * that wrote. A failure of a callback is thrown once the others have run.
     */
    fun dispatch() {
        val current = versions.value
        val fired = remove(registrations.value.filter { it.since.isStaleIn(current) })
        var failure: Throwable? = null
        for (registration in fired) {
            try {
                registration.callback()
            } catch (e: Throwable) {
                val first = failure
                if (first == null)
                    failure = e
                else
                    first.addSuppressed(e)
            }
        }
        failure?.let { throw it }
    }

    /**
     * Starts tracking the tables that the tables and views named [relations] read, creating their triggers, unless they
     * are tracked already.
     *
     * @return The tables tracked for [relations]: a table stands for itself, and a view for the tables it reads
     */
    fun track(relations: Set<String>): Set<String> {
        val tables = relations.flatMapTo(LinkedHashSet()) { tablesOf(it) }
        val newTables = tables - trackedTables.value
        if (newTables.isEmpty())
            return tables
        connection.execSQL(CREATE_LOG)
        val newVirtualTables = newTables intersect virtualTables()
        for (table in newTables) {
            connection.execSQL(INSERT_LOG_ROW, arrayOf(table))
            if (table !in newVirtualTables)
                createTriggers(table)
        }
        virtualTables.update { it + newVirtualTables }
        if (newVirtualTables.isNotEmpty())
            triggeredTables.value = triggeredTables()
        trackedTables.update { it + newTables }
        return tables
    }

    /**
     * Returns the virtual tables of the database, the tables without a root page of their own.
     */
    // The triggers of the tracker itself are TEMP ones, which sqlite_master doesn't list
    private fun triggeredTables(): Set<String> =
        connection.withQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE type = 'trigger'") { cursor ->
            buildSet {
                cursor.forEachRow { add(cursor.getString(0)!!) }
            }
        }

    private fun virtualTables(): Set<String> =
        connection.withQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND rootpage = 0") { cursor ->
            buildSet {
                cursor.forEachRow { add(cursor.getString(0)!!) }
            }
        }

    /**
     * Returns the tables that the table or view named [relation] reads: the table itself, or the tables of the view.
     */
    private fun tablesOf(relation: String): Set<String> {
        val type = connection.withQuery("SELECT type FROM sqlite_master WHERE name = ?", arrayOf(relation)) {
            if (it.next()) it.getString(0) else null
        }
        if (type != "view")
            return setOf(relation)
        // An OpenRead's P2 is the root page of the table or index it opens, and P3 the database, 0 for main
        val rootPages = connection.withQuery("EXPLAIN SELECT * FROM ${identifier(relation)}") { cursor ->
            val opcode = cursor.getColumnIndex("opcode")
            val rootPage = cursor.getColumnIndex("p2")
            val database = cursor.getColumnIndex("p3")
            buildSet {
                cursor.forEachRow {
                    if (cursor.getString(opcode) == "OpenRead" && cursor.getLong(database) == 0L)
                        add(cursor.getLong(rootPage))
                }
            }
        }
        if (rootPages.isEmpty())
            return emptySet()
        // An index's tbl_name is its table
        return connection.withQuery("SELECT DISTINCT tbl_name FROM sqlite_master WHERE rootpage IN (${rootPages.joinToString()})") { cursor ->
            buildSet {
                cursor.forEachRow { add(cursor.getString(0)!!) }
            }
        }
    }

    /**
     * Reads the counts of the tracked tables into [versions], after a scope that made [changes] and wrote rows to
     * [writtenTables] ran.
     *
     * A schema change may have dropped or replaced a tracked table, and its triggers with it, so the triggers are
     * created again where they are missing, and every tracked table counts as changed. A table may also have been
     * created again as the other kind, virtual or not, or only now, so which tables are virtual is read again too.
     */
    fun refresh(changes: Changes, writtenTables: Set<String>) {
        val tables = trackedTables.value
        if (changes == Changes.NONE || tables.isEmpty())
            return
        if (changes == Changes.SCHEMA) {
            val virtual = tables intersect virtualTables()
            virtualTables.value = virtual
            triggeredTables.value = if (virtual.isEmpty()) emptySet() else triggeredTables()
        }
        restoreLostObjects(tables)
        if (changes == Changes.SCHEMA) {
            for (table in tables - virtualTables.value)
                createTriggers(table)
            connection.execSQL(COUNT_ALL)
        } else {
            val virtual = virtualTables.value
            val writtenVirtualTables = if (writtenTables.any { it in triggeredTables.value }) virtual else virtual intersect writtenTables
            if (writtenVirtualTables.isNotEmpty()) {
                val placeholders = writtenVirtualTables.joinToString(",") { "?" }
                connection.execSQL("$COUNT_ALL WHERE table_name IN ($placeholders)", writtenVirtualTables.toTypedArray())
            }
        }
        // On Android, a query outside a transaction may run on a reader connection, which has no TEMP table
        val counts = connection.withTransaction {
            it.withQuery(SELECT_LOG) { cursor ->
                buildMap {
                    cursor.forEachRow { put(cursor.getString(0)!!, cursor.getLong(1)) }
                }
            }
        }
        _versions.update { versions ->
            versions + counts.filter { (table, count) -> count > (versions[table] ?: -1L) }
        }
    }

    /**
     * Creates the TEMP table and the triggers of the tracked [tables] again where they are gone, as a rollback can take
     * them, and counts a change of each table whose row or triggers were gone, as its changes may have been missed.
     */
    private fun restoreLostObjects(tables: Set<String>) {
        // On Android, a query outside a transaction may run on a reader connection, which has no TEMP objects
        val (loggedTables, triggerCounts) = connection.withTransaction {
            val logExists = it.withQuery(LOG_EXISTS) { cursor -> cursor.next() && cursor.getLong(0) > 0 }
            val logged = if (logExists) it.withQuery(SELECT_LOGGED_TABLES) { cursor ->
                buildSet<String> {
                    cursor.forEachRow { add(cursor.getString(0)!!) }
                }
            } else null
            val triggers = it.withQuery(COUNT_TRIGGERS) { cursor ->
                buildMap<String, Long> {
                    cursor.forEachRow { put(cursor.getString(0)!!, cursor.getLong(1)) }
                }
            }
            logged to triggers
        }
        val virtual = virtualTables.value
        val lostTables = tables.filter { table ->
            loggedTables == null || table !in loggedTables || (table !in virtual && (triggerCounts[table] ?: 0L) < OPERATIONS.size)
        }
        if (lostTables.isEmpty())
            return
        connection.execSQL(CREATE_LOG)
        for (table in lostTables) {
            // Above the version last read, so that the queries that read the table run again
            connection.execSQL(INSERT_LOG_VERSION, arrayOf<Any?>(table, _versions.value[table] ?: 0L))
            connection.execSQL(COUNT_TABLE, arrayOf(table))
            if (table !in virtual)
                createTriggers(table)
        }
    }

    /**
     * Creates the triggers of [table] unless they exist. A table that doesn't exist, as after DROP, gets them once it
     * is created again.
     */
    private fun createTriggers(table: String) {
        try {
            for (operation in OPERATIONS) {
                connection.execSQL(
                    "CREATE TEMP TRIGGER IF NOT EXISTS ${identifier("sqllin_modification_trigger_${table}_$operation")} " +
                        "AFTER $operation ON ${identifier(table)} BEGIN " +
                        "UPDATE $LOG SET version = version + 1 WHERE table_name = ${literal(table)}; END"
                )
            }
        } catch (e: Exception) {
            if (!tableExists(table))
                return
            throw e
        }
    }

    private fun tableExists(table: String): Boolean =
        connection.withQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)) {
            it.next() && it.getLong(0) > 0
        }

    private companion object {
        const val LOG = "sqllin_modification_log"
        const val CREATE_LOG = "CREATE TEMP TABLE IF NOT EXISTS $LOG(table_name TEXT PRIMARY KEY NOT NULL, version INTEGER NOT NULL DEFAULT 0)"
        const val INSERT_LOG_ROW = "INSERT OR IGNORE INTO $LOG(table_name) VALUES(?)"
        const val INSERT_LOG_VERSION = "INSERT OR IGNORE INTO $LOG(table_name, version) VALUES(?, ?)"
        const val COUNT_ALL = "UPDATE $LOG SET version = version + 1"
        const val COUNT_TABLE = "$COUNT_ALL WHERE table_name = ?"
        const val SELECT_LOG = "SELECT table_name, version FROM $LOG"
        const val SELECT_LOGGED_TABLES = "SELECT table_name FROM $LOG"
        const val LOG_EXISTS = "SELECT count(*) FROM sqlite_temp_master WHERE type = 'table' AND name = '$LOG'"
        const val COUNT_TRIGGERS = "SELECT tbl_name, count(*) FROM sqlite_temp_master WHERE type = 'trigger' AND name LIKE 'sqllin\\_modification\\_trigger\\_%' ESCAPE '\\' GROUP BY tbl_name"
        val OPERATIONS = listOf("INSERT", "UPDATE", "DELETE")

        fun identifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
        fun literal(value: String): String = "'${value.replace("'", "''")}'"
    }
}
