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

/**
 * Execution engine for top-level database DSL operations.
 *
 * Collects all statements created within a `Database` scope and executes them when the scope
 * exits. Handles both individual statements and transaction groups. Supports progressive clause
 * building by allowing UPDATE and SELECT statements to be replaced with refined versions.
 *
 * @property enableSimpleSQLLog Whether to print SQL and parameters before execution
 *
 * @author Yuang Qiao
 */
internal class DatabaseExecuteEngine(
    private val enableSimpleSQLLog: Boolean,
) : StatementContainer {

    private val statementList = ArrayDeque<ExecutableStatement>()

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementList.lastOrNull() is UpdateStatementWithoutWhereClause<*>
            || statementList.lastOrNull() is SelectStatement<*>) {
            statementList.removeLast()
            statementList.add(statement)
        } else
            throw IllegalStateException("Current statement can't append clause.")
    }

    infix fun addStatement(statement: ExecutableStatement) {
        statementList.add(statement)
    }

    fun executeAllStatement() {
        statementList.forEach {
            when (it) {
                is SingleStatement -> {
                    if (enableSimpleSQLLog)
                        it.printlnSQL()
                    it.execute()
                }
                is TransactionStatementsGroup -> it.execute()
            }
        }
    }
}