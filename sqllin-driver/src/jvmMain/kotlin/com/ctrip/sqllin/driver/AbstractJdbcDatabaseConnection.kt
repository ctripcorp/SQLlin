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

import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.Types

/**
 * Base class for JDBC-based database connections on JVM platforms.
 *
 * Handles parameter binding for PreparedStatements with support for various data types.
 *
 * @author Yuang Qiao
 */
internal abstract class AbstractJdbcDatabaseConnection : DatabaseConnection {

    /**
     * Creates a PreparedStatement for the given SQL.
     */
    abstract fun createStatement(sql: String): PreparedStatement

    /**
     * Binds parameters to a PreparedStatement, supporting common Kotlin/Java types.
     */
    protected fun bindParamsToSQL(sql: String, bindParams: Array<out Any?>?): PreparedStatement = createStatement(sql).apply {
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
}