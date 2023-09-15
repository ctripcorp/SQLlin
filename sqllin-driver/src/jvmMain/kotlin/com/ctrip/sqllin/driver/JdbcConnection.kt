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

import java.lang.IllegalStateException
import java.sql.Connection

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Types
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Database connection JDBC actual
 * @author yaqiao
 */

internal class JdbcConnection(private val connection: Connection) : DatabaseConnection {

    private fun bindParamsToSQL(sql: String, bindParams: Array<Any?>?): PreparedStatement = connection.prepareStatement(sql).apply {
        bindParams?.run {
            require(isNotEmpty()) { "Empty bindArgs" }
            forEachIndexed { index, any ->
                val realIndex = index + 1
                when (any) {
                    is String -> setString(realIndex, any)
                    is Long -> setLong(realIndex, any)
                    is Double -> setDouble(realIndex, any)
                    is ByteArray -> setBytes(realIndex, any)
                    null -> setNull(realIndex, Types.NULL)

                    is Int -> setInt(realIndex, any)
                    is Float -> setFloat(realIndex, any)
                    is Boolean -> setBoolean(realIndex, any)
                    is Char -> setString(realIndex, any.toString())
                    is Short -> setShort(realIndex, any)
                    is Byte -> setByte(realIndex, any)

                    is ULong -> setLong(realIndex, any.toLong())
                    is UInt -> setInt(realIndex, any.toInt())
                    is UShort -> setShort(realIndex, any.toShort())
                    is UByte -> setByte(realIndex, any.toByte())

                    is BigDecimal -> setBigDecimal(realIndex, any)

                    else -> throw IllegalArgumentException("No supported element type.")
                }
            }
        }
    }

    override fun execSQL(sql: String, bindParams: Array<Any?>?) {
        bindParamsToSQL(sql, bindParams).use {
            it.execute()
        }
    }

    override fun executeInsert(sql: String, bindParams: Array<Any?>?) {
        executeUpdate(sql, bindParams)
    }

    override fun executeUpdateDelete(sql: String, bindParams: Array<Any?>?) {
        executeUpdate(sql, bindParams)
    }

    private fun executeUpdate(sql: String, bindParams: Array<Any?>?): Int = bindParamsToSQL(sql, bindParams).use {
        it.executeUpdate()
    }

    override fun query(sql: String, bindParams: Array<String?>?): CommonCursor {
        val statement = connection.prepareStatement(sql)
        bindParams?.forEachIndexed { index, str ->
            str?.let {
                statement.setString(index + 1, it)
            }
        }
        return statement.executeQuery()?.let { JdbcCursor(it) } ?: throw IllegalStateException("The query result is null.")
    }

    private val isTransactionSuccess = AtomicBoolean(false)

    override fun beginTransaction() {
        if (isTransactionSuccess.get())
            isTransactionSuccess.set(false)
        connection.autoCommit = false
    }

    override fun setTransactionSuccessful() {
        isTransactionSuccess.set(true)
    }

    override fun endTransaction() = try {
        if (isTransactionSuccess.get())
            connection.commit()
        else
            connection.rollback()
    } finally {
        connection.autoCommit = true
        isTransactionSuccess.set(false)
    }

    override fun close() = connection.close()

    @Deprecated(
        message = "The property closed has been deprecated, please use the isClosed to replace it",
        replaceWith = ReplaceWith("isClosed")
    )
    override val closed: Boolean
        get() = connection.isClosed
    override val isClosed: Boolean
        get() = connection.isClosed
}