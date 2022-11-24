package com.ctrip.sqllin.driver

import co.touchlab.sqliter.*

/**
 * SQLite Cursor Native actual
 * @author yaqiao
 */

public class CursorImpl internal constructor(
    private val cursor: Cursor,
    private val statement: Statement
) : CommonCursor {

    override fun getInt(columnIndex: Int): Int = cursor.getLong(columnIndex).toInt()
    override fun getLong(columnIndex: Int): Long = cursor.getLong(columnIndex)
    override fun getFloat(columnIndex: Int): Float = cursor.getDouble(columnIndex).toFloat()
    override fun getDouble(columnIndex: Int): Double = cursor.getDouble(columnIndex)
    override fun getString(columnIndex: Int): String? = cursor.getStringOrNull(columnIndex)
    override fun getByteArray(columnIndex: Int): ByteArray? = cursor.getBytesOrNull(columnIndex)

    override fun getColumnIndex(columnName: String): Int = cursor.getColumnIndexOrThrow(columnName)

    override fun forEachRow(block: (Int) -> Unit) {
        var index = 0
        while (cursor.next())
            block(index++)
    }

    override fun close(): Unit = statement.finalizeStatement()
}