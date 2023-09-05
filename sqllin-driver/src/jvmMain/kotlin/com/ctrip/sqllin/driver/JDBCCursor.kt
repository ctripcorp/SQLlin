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

import java.sql.ResultSet

/**
 * SQLite Cursor JDBC actual
 * @author yaqiao
 */
internal class JDBCCursor(private val resultSet: ResultSet) : CommonCursor {

    override fun getInt(columnIndex: Int): Int = resultSet.getInt(columnIndex)

    override fun getLong(columnIndex: Int): Long = resultSet.getLong(columnIndex)

    override fun getFloat(columnIndex: Int): Float = resultSet.getFloat(columnIndex)

    override fun getDouble(columnIndex: Int): Double = resultSet.getDouble(columnIndex)

    override fun getString(columnIndex: Int): String? = resultSet.getString(columnIndex)

    override fun getByteArray(columnIndex: Int): ByteArray? = resultSet.getBytes(columnIndex)
    override fun getColumnIndex(columnName: String): Int = resultSet.findColumn(columnName)

    override fun forEachRows(block: (Int) -> Unit) {
        var index = 0
        while (next())
            block(index++)
    }

    override fun next(): Boolean = resultSet.next()

    override fun close() {
        resultSet.close()
        resultSet.statement.close()
    }
}