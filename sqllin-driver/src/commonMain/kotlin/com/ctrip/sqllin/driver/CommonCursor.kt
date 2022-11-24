package com.ctrip.sqllin.driver

/**
 * SQLite Cursor common abstract
 * @author yaqiao
 */

interface CommonCursor {

    fun getInt(columnIndex: Int): Int
    fun getLong(columnIndex: Int): Long
    fun getFloat(columnIndex: Int): Float
    fun getDouble(columnIndex: Int): Double
    fun getString(columnIndex: Int): String?
    fun getByteArray(columnIndex: Int): ByteArray?

    fun getColumnIndex(columnName: String): Int

    fun forEachRow(block: (Int) -> Unit)

    fun close()
}