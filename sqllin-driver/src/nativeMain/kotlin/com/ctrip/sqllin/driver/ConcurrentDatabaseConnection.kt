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

import com.ctrip.sqllin.driver.platform.Lock
import com.ctrip.sqllin.driver.platform.withLock

/**
 * Thread-safe wrapper for [NativeDatabaseConnection] using platform-specific locks.
 *
 * Delegates all operations to the underlying connection while ensuring thread safety.
 *
 * @author Yuang Qiao
 */
internal class ConcurrentDatabaseConnection(
    private val delegateConnection: NativeDatabaseConnection
) : NativeDatabaseConnection() {

    private val accessLock = Lock()

    override fun execSQL(sql: String, bindParams: Array<out Any?>?) = accessLock.withLock {
        delegateConnection.execSQL(sql, bindParams)
    }

    override fun executeInsert(sql: String, bindParams: Array<out Any?>?) = accessLock.withLock {
        delegateConnection.executeInsert(sql, bindParams)
    }

    override fun executeUpdateDelete(sql: String, bindParams: Array<out Any?>?) = accessLock.withLock {
        delegateConnection.executeUpdateDelete(sql, bindParams)
    }

    override fun query(sql: String, bindParams: Array<out Any?>?): CommonCursor = accessLock.withLock {
        delegateConnection.query(sql, bindParams)
    }

    override fun beginTransaction() = accessLock.withLock {
        delegateConnection.beginTransaction()
    }

    override fun setTransactionSuccessful() = accessLock.withLock {
        delegateConnection.setTransactionSuccessful()
    }

    override fun endTransaction() = accessLock.withLock {
        delegateConnection.endTransaction()
    }

    override fun close() = try {
        accessLock.withLock {
            delegateConnection.close()
        }
    } finally {
        accessLock.close()
    }

    override val isClosed: Boolean
        get() = delegateConnection.isClosed

    override fun createStatement(sql: String): SQLiteStatement =
        ConcurrentStatement(delegateConnection.createStatement(sql), accessLock)
}