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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.WhereClause
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement

/**
 * SQL delete
 * @author yaqiao
 */

internal object Delete : Operation {

    override val sqlStr: String
        get() = "DELETE FROM "

    fun <T : DBEntity<T>> delete(table: Table<*>, connection: DatabaseConnection, whereClause: WhereClause<T>): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
            append(whereClause.clauseStr)
        }
        return UpdateDeleteStatement(sql, connection)
    }

    fun deleteAllEntity(table: Table<*>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
        }
        return UpdateDeleteStatement(sql, connection)
    }

    private fun StringBuilder.buildBaseDeleteStatement(table: Table<*>) {
        append(sqlStr)
        append(table.tableName)
    }
}