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

package com.ctrip.sqllin.driver.cinterop

import cnames.structs.sqlite3_stmt
import com.ctrip.sqllin.driver.CommonCursor
import com.ctrip.sqllin.driver.NativeCursor
import com.ctrip.sqllin.driver.SQLiteStatement
import com.ctrip.sqllin.driver.platform.bytesToString
import com.ctrip.sqllin.driver.sqliteException
import com.ctrip.sqllin.sqlite3.SQLITE_BUSY
import com.ctrip.sqllin.sqlite3.SQLITE_DONE
import com.ctrip.sqllin.sqlite3.SQLITE_LOCKED
import com.ctrip.sqllin.sqlite3.SQLITE_NULL
import com.ctrip.sqllin.sqlite3.SQLITE_OK
import com.ctrip.sqllin.sqlite3.SQLITE_ROW
import com.ctrip.sqllin.sqlite3.SQLITE_TRANSIENT
import com.ctrip.sqllin.sqlite3.sqlite3_bind_blob
import com.ctrip.sqllin.sqlite3.sqlite3_bind_double
import com.ctrip.sqllin.sqlite3.sqlite3_bind_int64
import com.ctrip.sqllin.sqlite3.sqlite3_bind_null
import com.ctrip.sqllin.sqlite3.sqlite3_bind_parameter_index
import com.ctrip.sqllin.sqlite3.sqlite3_bind_text
import com.ctrip.sqllin.sqlite3.sqlite3_bind_zeroblob
import com.ctrip.sqllin.sqlite3.sqlite3_changes
import com.ctrip.sqllin.sqlite3.sqlite3_clear_bindings
import com.ctrip.sqllin.sqlite3.sqlite3_column_blob
import com.ctrip.sqllin.sqlite3.sqlite3_column_bytes
import com.ctrip.sqllin.sqlite3.sqlite3_column_count
import com.ctrip.sqllin.sqlite3.sqlite3_column_double
import com.ctrip.sqllin.sqlite3.sqlite3_column_int64
import com.ctrip.sqllin.sqlite3.sqlite3_column_name
import com.ctrip.sqllin.sqlite3.sqlite3_column_text
import com.ctrip.sqllin.sqlite3.sqlite3_column_type
import com.ctrip.sqllin.sqlite3.sqlite3_errmsg
import com.ctrip.sqllin.sqlite3.sqlite3_finalize
import com.ctrip.sqllin.sqlite3.sqlite3_last_insert_rowid
import com.ctrip.sqllin.sqlite3.sqlite3_reset
import com.ctrip.sqllin.sqlite3.sqlite3_step
import kotlinx.cinterop.*
import platform.posix.usleep

/**
 * The native statement wrapper for `sqlite3_stmt`, interop with SQLite C APIs directly
 * @author yaqiao
 */

@OptIn(ExperimentalForeignApi::class)
internal class NativeStatement(
    private val database: NativeDatabase,
    private val cStatementPointer: CPointer<sqlite3_stmt>,
) : SQLiteStatement {

    // Cursor methods
    private fun isNull(index: Int): Boolean =
        sqlite3_column_type(cStatementPointer, index) == SQLITE_NULL

    override fun columnGetLong(columnIndex: Int): Long? =
        if (isNull(columnIndex)) null else sqlite3_column_int64(cStatementPointer, columnIndex)

    override fun columnGetDouble(columnIndex: Int): Double? =
        if (isNull(columnIndex)) null else sqlite3_column_double(cStatementPointer, columnIndex)

    override fun columnGetString(columnIndex: Int): String? =
        if (isNull(columnIndex))
            null
        else
            sqlite3_column_text(cStatementPointer, columnIndex)
                ?.reinterpret<ByteVar>()
                ?.let { bytesToString(it) }

    override fun columnGetBlob(columnIndex: Int): ByteArray? {
        if (isNull(columnIndex))
            return null
        val blobSize = sqlite3_column_bytes(cStatementPointer, columnIndex)
        return if (blobSize == 0)
            null
        else
            sqlite3_column_blob(cStatementPointer, columnIndex)?.readBytes(blobSize)
    }

    override fun columnCount(): Int = sqlite3_column_count(cStatementPointer)

    override fun columnName(columnIndex: Int): String = bytesToString(sqlite3_column_name(cStatementPointer, columnIndex)!!)

    override fun columnType(columnIndex: Int): Int = sqlite3_column_type(cStatementPointer, columnIndex)

    override fun step(): Boolean {
        // Maybe move a first call to pre-loop
        repeat(50) {
            when (val err = sqlite3_step(cStatementPointer)) {
                SQLITE_ROW -> return true
                SQLITE_DONE -> return false
                SQLITE_LOCKED, SQLITE_BUSY -> usleep(1000u)
                else -> throw sqliteException("sqlite3_step failed", err)
            }
        }
        throw sqliteException("sqlite3_step retry count exceeded")
    }

    //Statement methods
    override fun finalizeStatement() {
        // We ignore the result of sqlite3_finalize because it is really telling us about
        // whether any errors occurred while executing the statement.  The statement itself
        // is always finalized regardless.
        sqlite3_finalize(cStatementPointer)
    }

    fun bindParameterIndex(paramName: String): Int =
        sqlite3_bind_parameter_index(cStatementPointer, paramName)

    override fun resetStatement() = opResult(database) {
        sqlite3_reset(cStatementPointer)
    }

    override fun clearBindings() = opResult(database) {
        sqlite3_clear_bindings(cStatementPointer)
    }

    override fun execute() {
        try {
            executeNonQuery()
        } finally {
            resetStatement()
            clearBindings()
        }
    }

    override fun executeInsert(): Long = try {
        executeForLastInsertedRowId()
    } finally {
        resetStatement()
        clearBindings()
    }

    private fun executeForLastInsertedRowId(): Long {
        val err = executeNonQuery()
        return if (err == SQLITE_DONE && sqlite3_changes(database.dbPointer) > 0)
            sqlite3_last_insert_rowid(database.dbPointer)
        else
            -1
    }

    override fun executeUpdateDelete(): Int = try {
        executeForChangedRowCount()
    } finally {
        resetStatement()
        clearBindings()
    }

    private fun executeForChangedRowCount(): Int {
        val err = executeNonQuery()
        return if (err == SQLITE_DONE)
            sqlite3_changes(database.dbPointer)
        else
            -1
    }

    private fun executeNonQuery(): Int {
        val err = sqlite3_step(cStatementPointer)
        if (err == SQLITE_ROW)
            throw sqliteException("Queries can be performed using SQLiteDatabase query or rawQuery methods only.")
        else if (err != SQLITE_DONE)
            throw sqliteException("executeNonQuery error", err)
        return err
    }

    override fun query(): CommonCursor = NativeCursor(this)

    override fun bindNull(index: Int) = opResult(database) {
        sqlite3_bind_null(cStatementPointer, index)
    }

    override fun bindLong(index: Int, value: Long) = opResult(database) {
        sqlite3_bind_int64(cStatementPointer, index, value)
    }

    override fun bindDouble(index: Int, value: Double) = opResult(database) {
        sqlite3_bind_double(cStatementPointer, index, value)
    }

    override fun bindString(index: Int, value: String) = opResult(database) {
        sqlite3_bind_text(cStatementPointer, index, value, -1, SQLITE_TRANSIENT)
    }

    override fun bindBlob(index: Int, value: ByteArray) = opResult(database) {
        if (value.isEmpty())
            sqlite3_bind_zeroblob(cStatementPointer, index, 0)
        else
            sqlite3_bind_blob(cStatementPointer, index, value.refTo(0), value.size, SQLITE_TRANSIENT)
    }

    private inline fun opResult(database: NativeDatabase, block: () -> Int) {
        val err = block()
        if (err != SQLITE_OK) {
            val error = sqlite3_errmsg(database.dbPointer)?.toKString()
            throw sqliteException("Sqlite operation failure ${error ?: ""}", err)
        }
    }
}