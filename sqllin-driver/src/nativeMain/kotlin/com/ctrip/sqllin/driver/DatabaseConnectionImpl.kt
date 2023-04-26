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

/**
 * Database manager Native actual
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
