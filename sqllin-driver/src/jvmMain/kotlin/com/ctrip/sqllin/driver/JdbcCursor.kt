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
internal class JdbcCursor(private val resultSet: ResultSet) : CommonCursor {

    override fun getInt(columnIndex: Int): Int = resultSet.getInt(columnIndex + 1)

    override fun getLong(columnIndex: Int): Long = resultSet.getLong(columnIndex + 1)

    override fun getFloat(columnIndex: Int): Float = resultSet.getFloat(columnIndex + 1)

    override fun getDouble(columnIndex: Int): Double = resultSet.getDouble(columnIndex + 1)

    override fun getString(columnIndex: Int): String? = resultSet.getString(columnIndex + 1)

    override fun getByteArray(columnIndex: Int): ByteArray? = resultSet.getBytes(columnIndex + 1)

    override fun getColumnIndex(columnName: String): Int = resultSet.findColumn(columnName + 1)

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