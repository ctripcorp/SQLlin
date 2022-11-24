package com.ctrip.sqllin.driver

import android.database.sqlite.SQLiteDatabase

/**
 * Database manager Android actual
 * @author yaqiao
 */

class DatabaseConnectionImpl internal constructor(private val database: SQLiteDatabase) : DatabaseConnection {

    override fun execSQL(sql: String, bindParams: Array<Any?>?) =
        if (bindParams == null)
            database.execSQL(sql)
        else
            database.execSQL(sql, bindParams)

    override fun executeInsert(sql: String, bindParams: Array<Any?>?) = execSQL(sql, bindParams)

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?) = execSQL(sql, bindParams)

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor = CursorImpl(database.rawQuery(sql, bindParams))

    override fun beginTransaction() = database.beginTransaction()
    override fun endTransaction() = database.endTransaction()
    override fun setTransactionSuccessful() = database.setTransactionSuccessful()

    override fun close() = database.close()
    override val closed: Boolean
        get() = !database.isOpen
}
