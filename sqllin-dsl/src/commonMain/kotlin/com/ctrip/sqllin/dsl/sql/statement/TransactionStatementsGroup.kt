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
 * The group of some statements those them in same transaction
 * @author yaqiao
 */

internal class TransactionStatementsGroup(
    private val databaseConnection: DatabaseConnection,
    private val enableSimpleSQLLog: Boolean,
) : ExecutableStatement, StatementContainer {

    private lateinit var statementList: StatementLinkedList<SingleStatement>

    infix fun addStatement(statement: SingleStatement) {
        if (this::statementList.isInitialized)
            statementList.addStatement(statement)
        else
            statementList = StatementLinkedList(statement)
    }

    override fun execute() = databaseConnection.withTransaction {
        statementList.forEach {
            if (enableSimpleSQLLog)
                println("SQL String: ${it.sqlStr}")
            it.execute()
        }
    }

    override infix fun changeLastStatement(statement: SingleStatement) {
        if (statementList.lastStatement is UpdateStatementWithoutWhereClause<*>
            || statementList.lastStatement is SelectStatement<*>)
            statementList resetLastStatement statement
        else
            throw IllegalStateException("Current statement can't append clause.")
    }
}