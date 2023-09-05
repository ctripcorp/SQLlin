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

/**
 * SQLite Cursor Native actual
 * @author yaqiao
 */

internal class NativeCursor(
    private val statement: SQLiteStatement
) : CommonCursor {

    override fun getInt(columnIndex: Int): Int = getLong(columnIndex).toInt()

    override fun getLong(columnIndex: Int): Long = statement.columnGetLong(columnIndex)

    override fun getFloat(columnIndex: Int): Float = getDouble(columnIndex).toFloat()

    override fun getDouble(columnIndex: Int): Double = statement.columnGetDouble(columnIndex)

    override fun getString(columnIndex: Int): String = statement.columnGetString(columnIndex)

    override fun getByteArray(columnIndex: Int): ByteArray = statement.columnGetBlob(columnIndex)

    override fun getColumnIndex(columnName: String): Int = columnNames[columnName] ?: throw IllegalArgumentException("Col for $columnName not found")

    override fun next(): Boolean = statement.step()

    override fun forEachRows(block: (Int) -> Unit) {
        var index = 0
        while (next())
            block(index++)
    }

    override fun close() = statement.finalizeStatement()

    private val columnNames: Map<String, Int> by lazy {
        val count = statement.columnCount()
        val map = HashMap<String, Int>(count)
        repeat(count) {
            val key = statement.columnName(it)
            if (map.containsKey(key)) {
                var index = 1
                val basicKey = "$key&JOIN"
                var finalKey = basicKey + index

                while (map.containsKey(finalKey))
                    finalKey = basicKey + ++index

                map[finalKey] = it
            } else {
                map[key] = it
            }
        }
        map
    }
}