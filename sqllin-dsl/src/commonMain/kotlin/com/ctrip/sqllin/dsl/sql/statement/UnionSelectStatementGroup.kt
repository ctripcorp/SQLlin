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
 * Used for compose multi select statement that use the 'UNION' clause
 * @author yaqiao
 */

internal class UnionSelectStatementGroup<T> : StatementContainer {

    private val statementList = ArrayDeque<SelectStatement<T>>()

    infix fun addSelectStatement(selectStatement: SelectStatement<T>) {
        statementList.add(selectStatement)
    }

    internal fun unionStatements(isUnionAll: Boolean): FinalSelectStatement<T> {
        require(statementList.isNotEmpty()) { "Please write at least two 'select' statements on 'UNION' scope" }
        var parameters: MutableList<String>? = null
        val unionSqlStr = buildString {
            check(statementList.size > 1) { "Please write at least two 'select' statements on 'UNION' scope" }
            val unionKeyWord = if (isUnionAll) " UNION ALL " else " UNION "
            statementList.forEachIndexed { index, statement ->
                append(statement.sqlStr)
                if (parameters == null)
                    parameters = statement.parameters
                else statement.parameters?.let {
                    parameters!!.addAll(it)
                }
                if (index != statementList.lastIndex)
                    append(unionKeyWord)
            }
        }

        return statementList.first().run {
            FinalSelectStatement(
                sqlStr = unionSqlStr,
                deserializer = deserializer,
                connection = connection,
                container = container,
                parameters,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun changeLastStatement(statement: SingleStatement) {
        if (statementList.lastOrNull() is SelectStatement<*>) {
            statementList.removeLast()
            statementList.add(statement as SelectStatement<T>)
        } else
            throw IllegalStateException("Current statement can't append clause")
    }
}