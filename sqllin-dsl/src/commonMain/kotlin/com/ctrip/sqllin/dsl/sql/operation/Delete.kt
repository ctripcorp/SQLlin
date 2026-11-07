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
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.WhereClause
import com.ctrip.sqllin.dsl.sql.statement.StatementContainer
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteParts
import com.ctrip.sqllin.dsl.sql.statement.WhereUpdateDeleteStatement

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
     * Builds a DELETE statement with WHERE clause, which ORDER BY and LIMIT can follow.
     *
     * Generates SQL in the format: `DELETE FROM table WHERE condition`
     *
     * @param table Table to delete from
     * @param connection Database connection for execution
     * @param container The container the statement is added to, in which a clause that follows replaces it
     * @param whereClause WHERE condition specifying which rows to delete
     * @param target The table, or the table with INDEXED BY or NOT INDEXED, which the rows are looked up through
     * @return DELETE statement ready for execution
     */
    fun <T> delete(
        table: Table<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
        whereClause: WhereClause<T>,
        target: String = table.tableName,
    ): WhereUpdateDeleteStatement<T> =
        WhereUpdateDeleteStatement(parts(table, target).copy(where = whereClause.selectCondition), container, connection)

    /**
     * Builds a DELETE statement without WHERE clause (deletes all rows), which ORDER BY and LIMIT can follow.
     *
     * Generates SQL in the format: `DELETE FROM table`
     *
     * @param table Table to delete all rows from
     * @param connection Database connection for execution
     * @param container The container the statement is added to, in which a clause that follows replaces it
     * @param target The table, or the table with INDEXED BY or NOT INDEXED, which the rows are looked up through
     * @return DELETE statement ready for execution
     */
    fun <T> deleteAllEntities(
        table: Table<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
        target: String = table.tableName,
    ): WhereUpdateDeleteStatement<T> = WhereUpdateDeleteStatement(parts(table, target), container, connection)

    private fun parts(table: Table<*>, target: String) = UpdateDeleteParts(
        statement = sqlStr,
        table = table,
        target = target,
        assignments = "",
        assignmentParameters = null,
    )
}