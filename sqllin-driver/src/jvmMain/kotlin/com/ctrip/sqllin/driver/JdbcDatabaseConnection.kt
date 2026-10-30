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

import java.sql.Connection

import java.sql.PreparedStatement
import java.util.concurrent.atomic.AtomicBoolean

/**
 * JDBC implementation of [DatabaseConnection] for JVM platforms.
 *
 * Uses java.sql.Connection for database operations.
 *
 * @author Yuang Qiao
 */
internal class JdbcDatabaseConnection(private val connection: Connection) : AbstractJdbcDatabaseConnection() {

    override fun execSQL(sql: String, bindParams: Array<out Any?>?) {
        bindParamsToSQL(sql, bindParams).use {
            it.execute()
        }
    }

    override fun executeInsert(sql: String, bindParams: Array<out Any?>?) {
        executeUpdate(sql, bindParams)
    }

    override fun executeUpdateDelete(sql: String, bindParams: Array<out Any?>?) {
        executeUpdate(sql, bindParams)
    }

    private fun executeUpdate(sql: String, bindParams: Array<out Any?>?): Int = bindParamsToSQL(sql, bindParams).use {
        it.executeUpdate()
    }

    override fun query(sql: String, bindParams: Array<out Any?>?): CommonCursor =
        bindParamsToSQL(sql, bindParams)
            .executeQuery()
            ?.let { JdbcCursor(it) }
            ?: throw IllegalStateException("The query result is null.")

    private val isTransactionSuccess = AtomicBoolean(false)

    /**
     * Begins a transaction in [mode], or a deferred one on a read-only connection, which can't take the write lock.
     *
     * It is begun with SQL, in auto-commit mode, rather than by leaving auto-commit mode: sqlite-jdbc begins another
     * transaction of its configured kind as soon as one is committed or rolled back, which an IMMEDIATE one couldn't
     * do while another connection writes, so that a COMMIT that succeeded would fail.
     */
    override fun beginTransaction(mode: TransactionMode) {
        if (isTransactionSuccess.get())
            isTransactionSuccess.set(false)
        execSQL(if (connection.isReadOnly) "BEGIN" else "BEGIN ${mode.name}", null)
    }

    override fun beginTransaction() = beginTransaction(TransactionMode.IMMEDIATE)

    override fun setTransactionSuccessful() {
        isTransactionSuccess.set(true)
    }

    override fun endTransaction() = try {
        if (isTransactionSuccess.get()) {
            try {
                execSQL("COMMIT", null)
            } catch (e: Exception) {
                // A COMMIT that fails, as one does while a deferred foreign key isn't satisfied, leaves the
                // transaction open, so that the statements after it would run in it
                runCatching { execSQL("ROLLBACK", null) }
                throw e
            }
        } else
            execSQL("ROLLBACK", null)
    } finally {
        isTransactionSuccess.set(false)
    }

    override fun close() = connection.close()

    override val isClosed: Boolean
        get() = connection.isClosed

    override fun createStatement(sql: String): PreparedStatement = connection.prepareStatement(sql)
}