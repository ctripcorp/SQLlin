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
 * Collect and execute all SQL statement in 'database {}' block
 * @author yaqiao
 */

internal class DatabaseExecuteEngine : StatementContainer {

    private var statementLinkedList: StatementLinkedList<ExecutableStatement>? = null

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementLinkedList?.lastStatement is UpdateStatementWithoutWhereClause<*>
            || statementLinkedList?.lastStatement is SelectStatement<*>)
            statementLinkedList!!.resetLastStatement(statement)
        else
            throw IllegalStateException("Current statement can't append clause.")
    }

    infix fun addStatement(statement: ExecutableStatement) {
        if (statementLinkedList != null)
            statementLinkedList!!.addStatement(statement)
        else
            statementLinkedList = StatementLinkedList(statement)
    }

    fun executeAllStatement() = statementLinkedList?.run {
        forEach {
            when (it) {
                is SingleStatement -> {
                    println("SQL String: ${it.sqlStr}")
                    it.execute()
                }
                is TransactionStatementsGroup -> it.execute()
            }
        }
        statementLinkedList = null
    } ?: Unit
}