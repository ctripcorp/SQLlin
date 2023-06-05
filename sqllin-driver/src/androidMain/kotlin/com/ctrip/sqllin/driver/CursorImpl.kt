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

import android.database.Cursor

/**
 * SQLite Cursor Android actual
 * @author yaqiao
 */

internal class CursorImpl(private val cursor: Cursor) : CommonCursor {

    override fun getInt(columnIndex: Int): Int = cursor.getInt(columnIndex)
    override fun getLong(columnIndex: Int): Long = cursor.getLong(columnIndex)
    override fun getFloat(columnIndex: Int): Float = cursor.getFloat(columnIndex)
    override fun getDouble(columnIndex: Int): Double = cursor.getDouble(columnIndex)

    override fun getString(columnIndex: Int): String? = try {
        cursor.getString(columnIndex)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    override fun getByteArray(columnIndex: Int): ByteArray? = try {
        cursor.getBlob(columnIndex)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    override fun getColumnIndex(columnName: String): Int = cursor.getColumnIndexOrThrow(columnName)

    override fun forEachRows(block: (Int) -> Unit) {
        if (!cursor.moveToFirst()) return
        var index = 0
        do block(index++)
        while (cursor.moveToNext())
    }

    override fun next(): Boolean = cursor.moveToNext()

    override fun close() = cursor.close()
}