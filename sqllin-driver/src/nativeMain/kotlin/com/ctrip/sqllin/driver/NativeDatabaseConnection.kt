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

public abstract class NativeDatabaseConnection internal constructor() : DatabaseConnection {

    internal abstract fun createStatement(sql: String): SQLiteStatement

    internal fun bindParamsToSQL(sql: String, bindParams: Array<Any?>?): SQLiteStatement = createStatement(sql).apply {
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
}