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

/**
 * SQLite native statement abstract APIs
 * @author yaqiao
 */

internal interface SQLiteStatement {

    fun columnGetLong(columnIndex: Int): Long

    fun columnGetDouble(columnIndex: Int): Double

    fun columnGetString(columnIndex: Int): String

    fun columnGetBlob(columnIndex: Int): ByteArray

    fun columnCount(): Int

    fun columnName(columnIndex: Int): String

    fun columnType(columnIndex: Int): Int

    fun step(): Boolean

    fun finalizeStatement()

    fun resetStatement()

    fun clearBindings()

    fun execute()

    fun executeInsert(): Long

    fun executeUpdateDelete(): Int

    fun query(): CommonCursor

    fun bindNull(index: Int)

    fun bindLong(index: Int, value: Long)

    fun bindDouble(index: Int, value: Double)

    fun bindString(index: Int, value: String)

    fun bindBlob(index: Int, value: ByteArray)
}