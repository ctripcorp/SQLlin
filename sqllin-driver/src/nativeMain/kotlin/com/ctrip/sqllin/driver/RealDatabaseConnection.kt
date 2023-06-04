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

import com.ctrip.sqllin.driver.cinterop.NativeDatabase
import com.ctrip.sqllin.driver.platform.Lock
import com.ctrip.sqllin.driver.platform.withLock
import kotlin.native.concurrent.AtomicInt
import kotlin.native.concurrent.AtomicReference

public class RealDatabaseConnection internal constructor(
    private val database: NativeDatabase
) : NativeDatabaseConnection() {

    private val transactionLock = Lock()
    private val transaction = AtomicReference<Transaction?>(null)
    private val closedFlag = AtomicInt(0)

    private data class Transaction(val isSuccessful: Boolean)

    override fun execSQL(sql: String, bindParams: Array<Any?>?): Unit =
        if (bindParams == null) {
            database.rawExecSql(sql)
        } else {
            val statement = bindParamsToSQL(sql, bindParams)
            try {
                statement.execute()
            } finally {
                statement.finalizeStatement()
            }
        }

    override fun executeInsert(sql: String, bindParams: Array<Any?>?) {
        val statement = bindParamsToSQL(sql, bindParams)
        try {
            statement.executeInsert()
        } finally {
            statement.finalizeStatement()
        }
    }

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?) {
        val statement = bindParamsToSQL(sql, bindParams)
        try {
            statement.executeUpdateDelete()
        } finally {
            statement.finalizeStatement()
        }
    }

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor {
        val statement = createStatement(sql)
        bindParams?.forEachIndexed { index, str ->
            str?.let {
                statement.bindString(index + 1, it)
            }
        }
        return statement.query()
    }

    override fun beginTransaction(): Unit = transactionLock.withLock {
        database.rawExecSql("BEGIN;")
        transaction.value = Transaction(isSuccessful = false)
    }

    override fun setTransactionSuccessful(): Unit = transactionLock.withLock {
        val trans = checkFailTransaction
        transaction.value = trans.copy(isSuccessful = true)
    }

    override fun endTransaction(): Unit = transactionLock.withLock {
        try {
            val sql = if (checkFailTransaction.isSuccessful) "COMMIT;" else "ROLLBACK;"
            database.rawExecSql(sql)
        } finally {
            transaction.value = null
        }
    }

    private inline val checkFailTransaction: Transaction
        get() = transaction.value ?: throw IllegalStateException("No transaction")

    override fun close(): Unit = try {
        closedFlag.value = 1
        database.close()
    } finally {
        // transactionLock.close()
    }

    override val closed: Boolean
        get() = closedFlag.value != 0

    override fun createStatement(sql: String): SQLiteStatement = database.prepareStatement(sql)
}
