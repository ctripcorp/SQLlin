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

    private var statementLinkedList: StatementLinkedList<SelectStatement<T>>? = null

    infix fun addSelectStatement(selectStatement: SelectStatement<T>) {
        if (statementLinkedList != null)
            statementLinkedList!!.addStatement(selectStatement)
        else
            statementLinkedList = StatementLinkedList(selectStatement)
    }

    internal fun unionStatements(isUnionAll: Boolean): FinalSelectStatement<T> {
        require(statementLinkedList?.hasNext() == true) { "Please write at least two 'select' statements on 'UNION' scope" }
        var firstStatement: SelectStatement<T>? = null
        var parameters: MutableList<String>? = null
        val unionSqlStr = buildString {
            statementLinkedList!!.run {
                val unionKeyWord = if (isUnionAll) " UNION ALL " else " UNION "
                do {
                    val next = next()
                    append(next.sqlStr)
                    val hasNext = hasNext()
                    if (firstStatement == null) {
                        firstStatement = next
                        if (!hasNext)
                            throw IllegalStateException("Please write at least two 'select' statements on 'UNION' scope")
                    }
                    if (parameters == null) {
                        parameters = next.parameters
                    } else next.parameters?.let {
                        parameters!!.addAll(it)
                    }
                    if (hasNext)
                        append(unionKeyWord)
                } while (hasNext)
            }
        }

        return firstStatement!!.run {
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
        if (statementLinkedList?.lastStatement is SelectStatement<*>)
            statementLinkedList!!.resetLastStatement(statement as SelectStatement<T>)
        else
            throw IllegalStateException("Current statement can't append clause")
    }
}