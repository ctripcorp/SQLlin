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
 * TEMP tables and triggers belong to the connection, so changes made through another connection, or by another
 * process, aren't seen. A table stays tracked until the connection closes.
 *
 * @author Yuang Qiao
 */
internal class InvalidationTracker(private val connection: DatabaseConnection) {

    private val trackedTables = MutableStateFlow<Set<String>>(emptySet())

    private val _versions = MutableStateFlow<Map<String, Long>>(emptyMap())

    /**
     * The count of changes to each tracked table. It only grows.
     */
    val versions: StateFlow<Map<String, Long>> = _versions

    /**
     * Starts tracking [tables], creating their triggers, unless they are tracked already.
     */
    fun track(tables: Set<String>) {
        val newTables = tables - trackedTables.value
        if (newTables.isEmpty())
            return
        connection.execSQL(CREATE_LOG)
        for (table in newTables) {
            connection.execSQL(INSERT_LOG_ROW, arrayOf(table))
            createTriggers(table)
        }
        trackedTables.update { it + newTables }
    }

    /**
     * Reads the counts of the tracked tables into [versions], after a scope that made [changes] ran.
     *
     * A schema change may have dropped or replaced a tracked table, and its triggers with it, so the triggers are
     * created again where they are missing, and every tracked table counts as changed.
     */
    fun refresh(changes: Changes) {
        val tables = trackedTables.value
        if (changes == Changes.NONE || tables.isEmpty())
            return
        if (changes == Changes.SCHEMA) {
            for (table in tables)
                createTriggers(table)
            connection.execSQL(COUNT_ALL)
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
        const val COUNT_ALL = "UPDATE $LOG SET version = version + 1"
        const val SELECT_LOG = "SELECT table_name, version FROM $LOG"
        val OPERATIONS = listOf("INSERT", "UPDATE", "DELETE")

        fun identifier(name: String): String = "\"${name.replace("\"", "\"\"")}\""
        fun literal(value: String): String = "'${value.replace("'", "''")}'"
    }
}
