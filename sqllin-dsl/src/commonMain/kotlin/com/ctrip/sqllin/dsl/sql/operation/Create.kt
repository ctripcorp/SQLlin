/*
 * Copyright (C) 2025 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.statement.CreateStatement

/**
 * SQL create
 * @author Yuang Qiao
 */

internal object Create : Operation {

    override val sqlStr: String
        get() = "CREATE TABLE "

    fun <T> create(table: Table<T>, connection: DatabaseConnection): CreateStatement =
        CreateStatement(buildSQL(table), connection)

    private fun <T> buildSQL(table: Table<T>): String = buildString {
        append(sqlStr)
        append(table.tableName)
        append(" (")
        val tableDescriptor = table.kSerializer().descriptor
        val lastIndex = tableDescriptor.elementsCount - 1
        for (elementIndex in 0 .. lastIndex) {
            val elementName = tableDescriptor.getElementName(elementIndex)
            val descriptor = tableDescriptor.getElementDescriptor(elementIndex)
            val type = with(descriptor.serialName) {
                when {
                    startsWith(FullNameCache.BYTE) || startsWith(FullNameCache.UBYTE) -> " TINYINT"
                    startsWith(FullNameCache.SHORT) || startsWith(FullNameCache.USHORT) -> " SMALLINT"
                    startsWith(FullNameCache.INT) || startsWith(FullNameCache.UINT) -> " INT"
                    startsWith(FullNameCache.LONG) -> if (elementName == table.primaryKeyInfo?.primaryKeyName) " INTEGER" else " BIGINT"
                    startsWith(FullNameCache.ULONG) -> " BIGINT"
                    startsWith(FullNameCache.FLOAT) -> " FLOAT"
                    startsWith(FullNameCache.DOUBLE) -> " DOUBLE"
                    startsWith(FullNameCache.BOOLEAN) -> " BOOLEAN"
                    startsWith(FullNameCache.CHAR) -> " CHAR(1)"
                    startsWith(FullNameCache.STRING) -> " TEXT"
                    startsWith(FullNameCache.BYTE_ARRAY) -> " BLOB"
                    else -> throw IllegalStateException("Hasn't support the type '$this' yet")
                }
            }
            val isNullable = descriptor.isNullable
            append(elementName)
            append(type)
            if (elementName == table.primaryKeyInfo?.primaryKeyName) {
                if (table.primaryKeyInfo?.isAutomaticIncrement == true && type == FullNameCache.LONG)
                    append(" PRIMARY KEY AUTOINCREMENT")
                else
                    append(" PRIMARY KEY")
            } else if (isNullable) {
                if (elementIndex < lastIndex)
                    append(',')

            } else {
                if (elementIndex < lastIndex)
                    append(" NOT NULL,")
                else
                    append(" NOT NULL")
            }
        }
        table.primaryKeyInfo?.compositePrimaryKeys?.joinTo(
            buffer = this,
            separator = ",",
            prefix = ", PRIMARY KEY ",
            postfix = ")"
        )
        append(')')
    }
}