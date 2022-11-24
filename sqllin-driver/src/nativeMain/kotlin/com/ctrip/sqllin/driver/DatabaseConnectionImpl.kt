package com.ctrip.sqllin.driver

import co.touchlab.sqliter.Statement
import co.touchlab.sqliter.bindString

/**
 * Database manager Native actual.
 * @author yaqiao
 */

public class DatabaseConnectionImpl internal constructor(private val databaseConnection: NativeDatabaseConnection) : DatabaseConnection {

    override fun execSQL(sql: String, bindParams: Array<Any?>?): Unit =
        if (bindParams == null)
            databaseConnection.rawExecSql(sql)
        else {
            val statement = bindParamsToSQL(sql, bindParams)
            try {
                statement.execute()
            } finally {
                statement.finalizeStatement()
            }
        }

    override fun executeInsert(sql: String, bindParams: Array<Any?>?) {
        val statement = bindParamsToSQL(sql, bindParams)
        try {
            statement.executeInsert()
        } finally {
            statement.finalizeStatement()
        }
    }

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?) {
        val statement = bindParamsToSQL(sql, bindParams)
        try {
            statement.executeUpdateDelete()
        } finally {
            statement.finalizeStatement()
        }
    }

    private fun bindParamsToSQL(sql: String, bindParams: Array<Any?>?): Statement = databaseConnection.createStatement(sql).apply {
        bindParams?.run {
            require(isNotEmpty()) { "Empty bindArgs" }
            forEachIndexed { index, any ->
                val realIndex = index + 1
                when (any) {
                    is String -> bindString(realIndex, any)
                    is Long -> bindLong(realIndex, any)
                    is Double -> bindDouble(realIndex, any)
                    is ByteArray -> bindBlob(realIndex, any)
                    null -> bindNull(realIndex)

                    is Int -> bindLong(realIndex, any.toLong())
                    is Float -> bindDouble(realIndex, any.toDouble())
                    is Boolean -> bindLong(realIndex, if (any) 1 else 0)
                    is Char -> bindString(realIndex, any.toString())
                    is Short -> bindLong(realIndex, any.toLong())
                    is Byte -> bindLong(realIndex, any.toLong())

                    is ULong -> bindLong(realIndex, any.toLong())
                    is UInt -> bindLong(realIndex, any.toLong())
                    is UShort -> bindLong(realIndex, any.toLong())
                    is UByte -> bindLong(realIndex, any.toLong())

                    else -> throw IllegalArgumentException("No supported element type.")
                }
            }
        }
    }

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor {
        val statement = databaseConnection.createStatement(sql)
        bindParams?.forEachIndexed { index, str ->
            statement.bindString(index + 1, str)
        }
        return CursorImpl(statement.query(), statement)
    }

    override fun beginTransaction(): Unit = databaseConnection.beginTransaction()
    override fun endTransaction(): Unit = databaseConnection.endTransaction()
    override fun setTransactionSuccessful(): Unit = databaseConnection.setTransactionSuccessful()

    override fun close(): Unit = databaseConnection.close()
    override val closed: Boolean
        get() = databaseConnection.closed
}
