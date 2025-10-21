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
 * Container for building UNION queries from multiple SELECT statements.
 *
 * Accumulates SELECT statements written inside a UNION DSL scope and combines them
 * with UNION or UNION ALL operators. Supports progressive clause building on individual
 * SELECT statements before they are unioned.
 *
 * @param T The entity type returned by all SELECT statements (must be compatible)
 *
 * @author Yuang Qiao
 */
internal class UnionSelectStatementGroup<T> : StatementContainer {

    private val statementList = ArrayDeque<SelectStatement<T>>()

    infix fun addSelectStatement(selectStatement: SelectStatement<T>) {
        statementList.add(selectStatement)
    }

    /**
     * Combines all accumulated SELECT statements into a single UNION query.
     *
     * Merges parameters from all statements and joins their SQL with UNION or UNION ALL operators.
     * Requires at least two SELECT statements.
     *
     * @param isUnionAll If true, uses UNION ALL (includes duplicates); otherwise uses UNION (distinct rows)
     * @return Final SELECT statement representing the complete UNION query
     */
    internal fun unionStatements(isUnionAll: Boolean): FinalSelectStatement<T> {
        require(statementList.isNotEmpty()) { "Please write at least two 'select' statements on 'UNION' scope" }
        var parameters: MutableList<Any?>? = null
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