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

import cnames.structs.sqlite3
import cnames.structs.sqlite3_stmt
import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.sqliteException
import com.ctrip.sqllin.sqlite3.SQLITE_DBCONFIG_LOOKASIDE
import com.ctrip.sqllin.sqlite3.SQLITE_OK
import com.ctrip.sqllin.sqlite3.SQLITE_OPEN_CREATE
import com.ctrip.sqllin.sqlite3.SQLITE_OPEN_READWRITE
import com.ctrip.sqllin.sqlite3.SQLITE_OPEN_URI
import com.ctrip.sqllin.sqlite3.sqlite3_busy_timeout
import com.ctrip.sqllin.sqlite3.sqlite3_close_v2
import com.ctrip.sqllin.sqlite3.sqlite3_db_config
import com.ctrip.sqllin.sqlite3.sqlite3_db_readonly
import com.ctrip.sqllin.sqlite3.sqlite3_errmsg
import com.ctrip.sqllin.sqlite3.sqlite3_exec
import com.ctrip.sqllin.sqlite3.sqlite3_open_v2
import com.ctrip.sqllin.sqlite3.sqlite3_prepare16_v2
import kotlinx.cinterop.*

/**
 * The native database wrapper for `sqlite3`, interop with SQLite C APIs directly
 * @author yaqiao
 */

@OptIn(ExperimentalForeignApi::class)
internal class NativeDatabase private constructor(val dbPointer: CPointer<sqlite3>) {

    companion object {
        fun openNativeDatabase(configuration: DatabaseConfiguration, realPath: String): NativeDatabase {
            val sqliteFlags = SQLITE_OPEN_READWRITE or SQLITE_OPEN_CREATE or SQLITE_OPEN_URI

            val db = memScoped {
                val dbPtr = alloc<CPointerVar<sqlite3>>()
                if(configuration.isReadOnly) {
                    //from SQLITE_OPEN_READWRITE docs: if opening in read-write mode fails due to OS-level permissions, an attempt is made to open it in read-only mode
                    val openResult = sqlite3_open_v2(realPath, dbPtr.ptr, SQLITE_OPEN_READWRITE or SQLITE_OPEN_URI, null)
                    if (openResult == SQLITE_OK) return@memScoped dbPtr.value!!
                }
                val openResult = sqlite3_open_v2(realPath, dbPtr.ptr, sqliteFlags, null)
                if (openResult != SQLITE_OK) {
                    throw sqliteException(sqlite3_errmsg(dbPtr.value)?.toKString() ?: "", openResult)
                }
                dbPtr.value!!
            }

            if (configuration.lookasideSlotSize >= 0 && configuration.lookasideSlotCount >= 0) {
                val err = sqlite3_db_config(db, SQLITE_DBCONFIG_LOOKASIDE, null, configuration.lookasideSlotSize, configuration.lookasideSlotCount)
                if (err != SQLITE_OK) {
                    val error = sqlite3_errmsg(db)?.toKString()
                    sqlite3_close_v2(db)
                    throw sqliteException("Cannot set lookaside : sqlite3_db_config(..., ${configuration.lookasideSlotSize}, %${configuration.lookasideSlotCount}) failed, ${error ?: ""}", err)
                }
            }

            // Check that the database is really read/write when that is what we asked for.
            if ((sqliteFlags and SQLITE_OPEN_READWRITE > 0) && sqlite3_db_readonly(db, null) != 0) {
                sqlite3_close_v2(db)
                throw sqliteException("Could not open the database in read/write mode")
            }

            // Set the default busy handler to retry automatically before returning SQLITE_BUSY.
            val err = sqlite3_busy_timeout(db, configuration.busyTimeout)
            if (err != SQLITE_OK) {
                sqlite3_close_v2(db)
                throw sqliteException("Could not set busy timeout", err)
            }

            return NativeDatabase(db)
        }
    }

    fun prepareStatement(sqlString: String): NativeStatement {
        val cStatement = memScoped {
            val statementPtr = alloc<CPointerVar<sqlite3_stmt>>()
            val sqlUgt16 = sqlString.wcstr
            val err = sqlite3_prepare16_v2(dbPointer, sqlUgt16.ptr, sqlUgt16.size, statementPtr.ptr, null)

            if (err != SQLITE_OK) {
                val error = sqlite3_errmsg(dbPointer)?.toKString()
                throw sqliteException("error while compiling: $sqlString\n$error", err)
            }

            statementPtr.value!!
        }
        return NativeStatement(this, cStatement)
    }

    fun rawExecSql(sqlString: String) {
        val err = sqlite3_exec(dbPointer, sqlString, null, null, null)
        if (err != SQLITE_OK) {
            val error = sqlite3_errmsg(dbPointer)?.toKString()
            throw sqliteException("error rawExecSql: $sqlString, ${error ?: ""}", err)
        }
    }

    fun close(){
        val err = sqlite3_close_v2(dbPointer)
        if (err != SQLITE_OK) {
            // This can happen if sub-objects aren't closed first.  Make sure the caller knows.
            throw sqliteException("sqlite3_close($dbPointer) failed", err)
        }
    }
}
