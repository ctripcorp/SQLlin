package com.ctrip.sqllin.driver

import android.database.sqlite.SQLiteDatabase

/**
 * Database manager Android actual
 * @author yaqiao
 */

public class DatabaseConnectionImpl internal constructor(private val database: SQLiteDatabase) : DatabaseConnection {

    override fun execSQL(sql: String, bindParams: Array<Any?>?): Unit =
        if (bindParams == null)
            database.execSQL(sql)
        else
            database.execSQL(sql, bindParams)

    override fun executeInsert(sql: String, bindParams: Array<Any?>?): Unit = execSQL(sql, bindParams)

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?): Unit = execSQL(sql, bindParams)

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor = CursorImpl(database.rawQuery(sql, bindParams))

    override fun beginTransaction(): Unit = database.beginTransaction()
    override fun endTransaction(): Unit = database.endTransaction()
    override fun setTransactionSuccessful(): Unit = database.setTransactionSuccessful()

    override fun close(): Unit = database.close()
    override val closed: Boolean
        get() = !database.isOpen
}
