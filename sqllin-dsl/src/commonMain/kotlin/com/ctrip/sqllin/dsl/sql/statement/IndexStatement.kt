/*
 * Copyright (C) 2026 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.SelectCondition
import com.ctrip.sqllin.dsl.sql.compiler.inlineParameters
import com.ctrip.sqllin.dsl.sql.operation.Create

/**
 * A CREATE INDEX statement, which `WHERE` makes a partial index: an index of the rows that satisfy a condition.
 *
 * @author Yuang Qiao
 */
public class IndexStatement internal constructor(
    private val table: Table<*>,
    private val indexName: String,
    private val connection: DatabaseConnection,
    private val container: StatementContainer,
    private val statement: SingleStatement,
) {

    private var hasWhere = false

    /**
     * Makes the index a partial index of the rows that satisfy [condition]: `CREATE INDEX ... WHERE condition`.
     *
     * SQLite allows neither parameters nor subqueries there, and a partial index can only read its table, so the
     * values of [condition] are written into the SQL as literals, and its columns without the table's name.
     */
    internal infix fun where(condition: SelectCondition) {
        check(!hasWhere) { "The index '$indexName' already has a WHERE." }
        require(condition.tables.isEmpty()) {
            "The WHERE of index '$indexName' can't have a subquery, which SQLite doesn't allow in a partial index."
        }
        // SQLite rejects it too, but only once the table has rows
        require(condition.isDeterministic) {
            "The WHERE of index '$indexName' can't have a function that doesn't always give the same result for a row."
        }
        val where = inlineParameters(Create.unqualified(condition.conditionSQL, table.tableName), condition.parameters)
        container.replaceStatement(statement, TableStructureStatement("${statement.sqlStr} WHERE $where", connection))
        hasWhere = true
    }
}
