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
import com.ctrip.sqllin.dsl.sql.statement.StatementContainer
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.SetClause
import com.ctrip.sqllin.dsl.sql.statement.UpdateStatementWithoutWhereClause

/**
 * UPDATE operation builder.
 *
 * Constructs UPDATE statements by combining table information with SET clauses. Returns an
 * intermediate statement that can either execute immediately (update all rows) or be refined
 * with a WHERE clause (update filtered rows).
 *
 * @author Yuang Qiao
 */
internal object Update : Operation {

    override val sqlStr: String
        get() = "UPDATE "

    /**
     * Builds an UPDATE statement with SET clause.
     *
     * Generates SQL in the format: `UPDATE table SET column1 = ?, column2 = ?, ...`
     *
     * @param table Table definition
     * @param connection Database connection for execution
     * @param container Statement container for DSL scope management
     * @param clause SET clause with column assignments
     * @return Statement that can either execute or be refined with WHERE
     */
    fun <T> update(
        table: Table<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
        clause: SetClause<T>,
    ): UpdateStatementWithoutWhereClause<T> {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(" SET ")
            append(clause.finalize())
        }
        return UpdateStatementWithoutWhereClause(sql, container, connection, clause.parameters)
    }
}