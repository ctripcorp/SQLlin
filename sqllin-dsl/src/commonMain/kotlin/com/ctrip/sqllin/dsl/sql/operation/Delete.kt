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
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.WhereClause
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement

/**
 * DELETE operation builder.
 *
 * Constructs DELETE statements with optional WHERE clauses. Supports both targeted deletion
 * (with WHERE) and bulk deletion (all rows).
 *
 * @author Yuang Qiao
 */
internal object Delete : Operation {

    override val sqlStr: String
        get() = "DELETE FROM "

    /**
     * Builds a DELETE statement with WHERE clause.
     *
     * Generates SQL in the format: `DELETE FROM table WHERE condition`
     *
     * @param table Table to delete from
     * @param connection Database connection for execution
     * @param whereClause WHERE condition specifying which rows to delete
     * @return Final DELETE statement ready for execution
     */
    fun <T> delete(table: Table<*>, connection: DatabaseConnection, whereClause: WhereClause<T>): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
            append(whereClause.clauseStr)
        }
        return UpdateDeleteStatement(sql, connection, whereClause.selectCondition.parameters)
    }

    /**
     * Builds a DELETE statement without WHERE clause (deletes all rows).
     *
     * Generates SQL in the format: `DELETE FROM table`
     *
     * @param table Table to delete all rows from
     * @param connection Database connection for execution
     * @return Final DELETE statement ready for execution
     */
    fun deleteAllEntities(table: Table<*>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
        }
        return UpdateDeleteStatement(sql, connection, null)
    }

    private fun StringBuilder.buildBaseDeleteStatement(table: Table<*>) {
        append(sqlStr)
        append(table.tableName)
    }
}