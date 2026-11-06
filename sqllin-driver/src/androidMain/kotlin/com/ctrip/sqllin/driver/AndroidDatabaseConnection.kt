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

import android.database.sqlite.SQLiteCursor
import android.os.Build
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteQuery

/**
 * Android implementation of [DatabaseConnection] using Android's SQLiteDatabase.
 *
 * @author Yuang Qiao
 */
internal class AndroidDatabaseConnection(private val database: SQLiteDatabase) : DatabaseConnection {

    /**
     * Runs [sql], and a PRAGMA that sets how the connection runs statements on every connection of the database.
     *
     * From Android 13 on, a database in WAL mode has several connections: the one that writes, and those that read
     * outside a transaction. The framework only prepares a PRAGMA such as `foreign_keys = ON` on the connection that
     * writes, and steps it on one that reads, as `sqlite3_stmt_readonly()` reports it doesn't write. SQLite changes the
     * setting when it prepares the PRAGMA, but only expires the statements the connection has compiled when it steps
     * it, so the statements that the connection that writes has cached kept running as they were compiled, with the
     * setting they had: without checking foreign keys, or with them; and the other connections that read didn't have
     * it, which `PRAGMA foreign_keys` may read through. Such a PRAGMA runs on every connection,
     * those opened later included, so that each steps it. In a transaction, which runs on the connection that writes,
     * it runs there, where SQLite ignores a change of foreign_keys, as it does on the other platforms, and applies
     * recursive_triggers to the statements after it, which only that connection runs. Before Android 13, a database
     * that SQLlin opens has a single connection.
     */
    override fun execSQL(sql: String, bindParams: Array<out Any?>?) = when {
        bindParams != null -> database.execSQL(sql, Array(bindParams.size) { unsignedAsLong(bindParams[it]) })
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !database.inTransaction() && CONNECTION_SETTING.containsMatchIn(sql) ->
            database.execPerConnectionSQL(sql, null)
        else -> database.execSQL(sql)
    }

    private companion object {
        /**
         * A PRAGMA that sets how the connection runs statements, which each connection has to run itself: foreign_keys,
         * recursive_triggers and ignore_check_constraints, which SQLite compiles into the statements, and
         * legacy_alter_table, which ALTER TABLE RENAME reads.
         */
        val CONNECTION_SETTING = Regex("""^\s*PRAGMA\s+(foreign_keys|recursive_triggers|legacy_alter_table|ignore_check_constraints)\s*[=(]""", RegexOption.IGNORE_CASE)
    }

    /**
     * The framework binds a value it doesn't know, such as a `UInt`, as its string, so unsigned values are bound as
     * the numbers they are, as [bindTypedParameters] does for queries. A `ULong` keeps the bits of a `Long`.
     */
    private fun unsignedAsLong(param: Any?): Any? = when (param) {
        is ULong -> param.toLong()
        is UInt -> param.toLong()
        is UShort -> param.toLong()
        is UByte -> param.toLong()
        else -> param
    }

    override fun executeInsert(sql: String, bindParams: Array<out Any?>?) = execSQL(sql, bindParams)

    override fun executeUpdateDelete(sql: String, bindParams: Array<out Any?>?) = execSQL(sql, bindParams)

    override fun query(sql: String, bindParams: Array<out Any?>?): CommonCursor {
        val cursor = if (bindParams == null) {
            database.rawQuery(sql, null)
        } else {
            // Use rawQueryWithFactory to bind parameters with proper types
            // This allows us to bind parameters with their actual types (Int, Long, Double, etc.)
            // instead of converting everything to String like rawQuery does
            val cursorFactory = SQLiteDatabase.CursorFactory { _, masterQuery, editTable, query ->
                bindTypedParameters(query, bindParams)
                SQLiteCursor(masterQuery, editTable, query)
            }
            // Pass emptyArray() for selectionArgs since we bind parameters via the factory
            // Use empty string for editTable since it's only needed for updateable cursors
            database.rawQueryWithFactory(cursorFactory, sql, null, "")
        }
        return AndroidCursor(cursor)
    }

    /**
     * Binds parameters to SQLiteQuery with proper type handling.
     *
     * Unlike rawQuery which only accepts String[], this method binds parameters
     * with their actual types (Long, Double, ByteArray, etc.) to ensure correct
     * SQLite type affinity and comparisons.
     */
    private fun bindTypedParameters(query: SQLiteQuery, bindParams: Array<out Any?>) {
        bindParams.forEachIndexed { index, param ->
            val position = index + 1 // SQLite bind positions are 1-based
            when (param) {
                null -> query.bindNull(position)
                is ByteArray -> query.bindBlob(position, param)
                is Double -> query.bindDouble(position, param)
                is Float -> query.bindDouble(position, param.toDouble())
                is Long -> query.bindLong(position, param)
                is Int -> query.bindLong(position, param.toLong())
                is Short -> query.bindLong(position, param.toLong())
                is Byte -> query.bindLong(position, param.toLong())
                is Boolean -> query.bindLong(position, if (param) 1L else 0L)
                is ULong -> query.bindLong(position, param.toLong())
                is UInt -> query.bindLong(position, param.toLong())
                is UShort -> query.bindLong(position, param.toLong())
                is UByte -> query.bindLong(position, param.toLong())
                else -> query.bindString(position, param.toString())
            }
        }
    }

    override fun beginTransaction() = beginTransaction(TransactionMode.IMMEDIATE)

    // beginTransactionNonExclusive begins an IMMEDIATE transaction, and beginTransaction an EXCLUSIVE one. The framework
    // can't begin a DEFERRED one that writes, as it turns a BEGIN statement into an EXCLUSIVE transaction of its own, so
    // that one is IMMEDIATE, which only takes the write lock earlier
    override fun beginTransaction(mode: TransactionMode) = when (mode) {
        TransactionMode.EXCLUSIVE -> database.beginTransaction()
        TransactionMode.DEFERRED, TransactionMode.IMMEDIATE -> database.beginTransactionNonExclusive()
    }
    override fun endTransaction() {
        try {
            database.endTransaction()
        } catch (e: Exception) {
            rollBackOpenTransaction()
            throw e
        }
    }

    /**
     * Rolls back the transaction that a failed COMMIT leaves open in SQLite, as one does while a deferred foreign key
     * isn't satisfied: the framework has already let go of it, so that the next one would fail to begin.
     *
     * The framework turns a ROLLBACK given to it into its own end of a transaction, which it has none of, so a TEMP
     * trigger raises the ROLLBACK. The table and the trigger, created in the transaction, are rolled back with it; the
     * DROP only removes them where SQLite had already rolled back, as it does for some errors.
     */
    private fun rollBackOpenTransaction() {
        runCatching {
            database.execSQL("CREATE TEMP TABLE IF NOT EXISTS sqllin_rollback(x)")
            database.execSQL("CREATE TEMP TRIGGER IF NOT EXISTS sqllin_rollback BEFORE INSERT ON sqllin_rollback BEGIN SELECT RAISE(ROLLBACK, 'rollback'); END")
            database.execSQL("INSERT INTO sqllin_rollback VALUES (1)")
        }
        runCatching { database.execSQL("DROP TABLE IF EXISTS temp.sqllin_rollback") }
    }
    override fun setTransactionSuccessful() = database.setTransactionSuccessful()

    override fun close() = database.close()

    override val isClosed: Boolean
        get() = !database.isOpen
}
