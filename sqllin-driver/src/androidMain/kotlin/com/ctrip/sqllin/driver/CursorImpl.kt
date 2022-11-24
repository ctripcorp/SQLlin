package com.ctrip.sqllin.driver

import android.database.Cursor

/**
 * SQLite Cursor Android actual
 * @author yaqiao
 */

class CursorImpl internal constructor(private val cursor: Cursor) : CommonCursor {

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

    override fun forEachRow(block: (Int) -> Unit) {
        if (!cursor.moveToFirst()) return
        var index = 0
        do block(index++)
        while (cursor.moveToNext())
    }

    override fun close() = cursor.close()
}