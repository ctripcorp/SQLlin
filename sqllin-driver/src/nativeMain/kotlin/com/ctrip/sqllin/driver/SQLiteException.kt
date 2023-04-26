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

package com.ctrip.sqllin.driver

import com.ctrip.sqllin.driver.SQLiteResultCode.Companion.INVALID_CODE

/**
 * SQLiteException
 * @author yaqiao
 */

public open class SQLiteException(message: String) : Exception(message)

/**
 * The result codes in SQLite
 */
public class SQLiteResultCode(message: String) : SQLiteException("$message | error code ${SQLiteErrorType.values().find { it == code }}") {
    internal companion object {
        const val INVALID_CODE = -1
    }
}

internal fun sqliteException(message: String, errorCode: Int = INVALID_CODE): SQLiteException =
    if (errorCode == INVALID_CODE)
        SQLiteException(message)
    else
        SQLiteResultCode(message)

public enum class SQLiteErrorType(public val code: Int) {
    SQLITE_OK(com.ctrip.sqllin.sqlite3.SQLITE_OK),   /* Successful result */

    /* beginning-of-error-codes */
    SQLITE_ERROR(com.ctrip.sqllin.sqlite3.SQLITE_ERROR),   /* Generic error */
    SQLITE_INTERNAL(com.ctrip.sqllin.sqlite3.SQLITE_INTERNAL),   /* Internal logic error in SQLite */
    SQLITE_PERM(com.ctrip.sqllin.sqlite3.SQLITE_PERM),   /* Access permission denied */
    SQLITE_ABORT(com.ctrip.sqllin.sqlite3.SQLITE_ABORT),   /* Callback routine requested an abort */
    SQLITE_BUSY(com.ctrip.sqllin.sqlite3.SQLITE_BUSY),   /* The database file is locked */
    SQLITE_LOCKED(com.ctrip.sqllin.sqlite3.SQLITE_LOCKED),   /* A table in the database is locked */
    SQLITE_NOMEM(com.ctrip.sqllin.sqlite3.SQLITE_NOMEM),   /* A malloc() failed */
    SQLITE_READONLY(com.ctrip.sqllin.sqlite3.SQLITE_READONLY),   /* Attempt to write a readonly database */
    SQLITE_INTERRUPT(com.ctrip.sqllin.sqlite3.SQLITE_INTERRUPT),   /* Operation terminated by sqlite3_interrupt()*/
    SQLITE_IOERR(com.ctrip.sqllin.sqlite3.SQLITE_IOERR),   /* Some kind of disk I/O error occurred */
    SQLITE_CORRUPT(com.ctrip.sqllin.sqlite3.SQLITE_CORRUPT),   /* The database disk image is malformed */
    SQLITE_NOTFOUND(com.ctrip.sqllin.sqlite3.SQLITE_NOTFOUND),   /* Unknown opcode in sqlite3_file_control() */
    SQLITE_FULL(com.ctrip.sqllin.sqlite3.SQLITE_FULL),   /* Insertion failed because database is full */
    SQLITE_CANTOPEN(com.ctrip.sqllin.sqlite3.SQLITE_CANTOPEN),   /* Unable to open the database file */
    SQLITE_PROTOCOL(com.ctrip.sqllin.sqlite3.SQLITE_PROTOCOL),   /* Database lock protocol error */
    SQLITE_EMPTY(com.ctrip.sqllin.sqlite3.SQLITE_EMPTY),   /* Internal use only */
    SQLITE_SCHEMA(com.ctrip.sqllin.sqlite3.SQLITE_SCHEMA),   /* The database schema changed */
    SQLITE_TOOBIG(com.ctrip.sqllin.sqlite3.SQLITE_TOOBIG),   /* String or BLOB exceeds size limit */
    SQLITE_CONSTRAINT(com.ctrip.sqllin.sqlite3.SQLITE_CONSTRAINT),   /* Abort due to constraint violation */
    SQLITE_MISMATCH(com.ctrip.sqllin.sqlite3.SQLITE_MISMATCH),   /* Data type mismatch */
    SQLITE_MISUSE(com.ctrip.sqllin.sqlite3.SQLITE_MISUSE),   /* Library used incorrectly */
    SQLITE_NOLFS(com.ctrip.sqllin.sqlite3.SQLITE_NOLFS),   /* Uses OS features not supported on host */
    SQLITE_AUTH(com.ctrip.sqllin.sqlite3.SQLITE_AUTH),   /* Authorization denied */
    SQLITE_FORMAT(com.ctrip.sqllin.sqlite3.SQLITE_FORMAT),   /* Not used */
    SQLITE_RANGE(com.ctrip.sqllin.sqlite3.SQLITE_RANGE),   /* 2nd parameter to sqlite3_bind out of range */
    SQLITE_NOTADB(com.ctrip.sqllin.sqlite3.SQLITE_NOTADB),   /* File opened that is not a database file */
    SQLITE_NOTICE(com.ctrip.sqllin.sqlite3.SQLITE_NOTICE),   /* Notifications from sqlite3_log() */
    SQLITE_WARNING(com.ctrip.sqllin.sqlite3.SQLITE_WARNING),   /* Warnings from sqlite3_log() */
    SQLITE_ROW(com.ctrip.sqllin.sqlite3.SQLITE_ROW),  /* sqlite3_step() has another row ready */
    SQLITE_DONE(com.ctrip.sqllin.sqlite3.SQLITE_DONE),  /* sqlite3_step() has finished executing */
}