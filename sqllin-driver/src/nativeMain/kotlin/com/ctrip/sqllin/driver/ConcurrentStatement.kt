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
 * The concurrent statement, use platform-related lock to ensure thread-safe,
 * the lock come from the ConcurrentDatabaseConnection
 * @author yaqiao
 */

internal class ConcurrentStatement(
    private val delegateStatement: SQLiteStatement,
    private val accessLock: Lock,
) : SQLiteStatement {

    override fun columnGetLong(columnIndex: Int): Long = accessLock.withLock {
        delegateStatement.columnGetLong(columnIndex)
    }

    override fun columnGetDouble(columnIndex: Int): Double = accessLock.withLock {
        delegateStatement.columnGetDouble(columnIndex)
    }

    override fun columnGetString(columnIndex: Int): String = accessLock.withLock {
        delegateStatement.columnGetString(columnIndex)
    }

    override fun columnGetBlob(columnIndex: Int): ByteArray = accessLock.withLock {
        delegateStatement.columnGetBlob(columnIndex)
    }

    override fun columnCount(): Int = accessLock.withLock {
        delegateStatement.columnCount()
    }

    override fun columnName(columnIndex: Int): String = accessLock.withLock {
        delegateStatement.columnName(columnIndex)
    }

    override fun columnType(columnIndex: Int): Int = accessLock.withLock {
        delegateStatement.columnType(columnIndex)
    }

    override fun step(): Boolean = accessLock.withLock {
        delegateStatement.step()
    }

    override fun finalizeStatement() = accessLock.withLock {
        delegateStatement.finalizeStatement()
    }

    override fun resetStatement() = accessLock.withLock {
        delegateStatement.resetStatement()
    }

    override fun clearBindings() = accessLock.withLock {
        delegateStatement.clearBindings()
    }

    override fun execute() = accessLock.withLock {
        delegateStatement.execute()
    }

    override fun executeInsert(): Long = accessLock.withLock {
        delegateStatement.executeInsert()
    }

    override fun executeUpdateDelete(): Int = accessLock.withLock {
        delegateStatement.executeUpdateDelete()
    }

    override fun query(): CommonCursor = accessLock.withLock {
        NativeCursor(this)
    }

    override fun bindNull(index: Int) = accessLock.withLock {
        delegateStatement.bindNull(index)
    }

    override fun bindLong(index: Int, value: Long) = accessLock.withLock {
        delegateStatement.bindLong(index, value)
    }

    override fun bindDouble(index: Int, value: Double) = accessLock.withLock {
        delegateStatement.bindDouble(index, value)
    }

    override fun bindString(index: Int, value: String) = accessLock.withLock {
        delegateStatement.bindString(index, value)
    }

    override fun bindBlob(index: Int, value: ByteArray) = accessLock.withLock {
        delegateStatement.bindBlob(index, value)
    }
}