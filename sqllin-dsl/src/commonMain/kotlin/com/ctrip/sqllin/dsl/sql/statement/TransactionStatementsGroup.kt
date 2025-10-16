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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.driver.withTransaction

/**
 * Container for statements executed within a single database transaction.
 *
 * Collects all SQL statements written inside a transaction DSL scope and executes them
 * atomically. If any statement fails, the entire transaction rolls back. Supports progressive
 * clause building on UPDATE and SELECT statements.
 *
 * @property databaseConnection Connection for executing the transactional operations
 * @property enableSimpleSQLLog Whether to log each statement's SQL before execution
 *
 * @author Yuang Qiao
 */
internal class TransactionStatementsGroup(
    private val databaseConnection: DatabaseConnection,
    private val enableSimpleSQLLog: Boolean,
) : ExecutableStatement, StatementContainer {

    private val statementList = ArrayDeque<SingleStatement>()

    infix fun addStatement(statement: SingleStatement) {
        statementList.add(statement)
    }

    override fun execute() = databaseConnection.withTransaction {
        statementList.forEach {
            if (enableSimpleSQLLog)
                it.printlnSQL()
            it.execute()
        }
    }

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementList.lastOrNull() is UpdateStatementWithoutWhereClause<*>
            || statementList.lastOrNull() is SelectStatement<*>) {
            statementList.removeLast()
            statementList.add(statement)
        } else
            throw IllegalStateException("Current statement can't append clause.")
    }
}