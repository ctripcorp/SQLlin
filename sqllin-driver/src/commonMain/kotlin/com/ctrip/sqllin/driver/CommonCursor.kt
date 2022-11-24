package com.ctrip.sqllin.driver

/**
 * SQLite Cursor common abstract
 * @author yaqiao
 */

public interface CommonCursor {

    public fun getInt(columnIndex: Int): Int
    public fun getLong(columnIndex: Int): Long
    public fun getFloat(columnIndex: Int): Float
    public fun getDouble(columnIndex: Int): Double
    public fun getString(columnIndex: Int): String?
    public fun getByteArray(columnIndex: Int): ByteArray?

    public fun getColumnIndex(columnName: String): Int

    public fun forEachRow(block: (Int) -> Unit)

    public fun close()
}