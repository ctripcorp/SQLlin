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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateStatementWithoutWhereClause
import com.ctrip.sqllin.dsl.sql.statement.WhereSelectStatement

/**
 * WHERE clause for filtering rows in SELECT statements or targeting rows in UPDATE/DELETE statements.
 *
 * Wraps a [SelectCondition] and generates SQL in the format: ` WHERE condition`
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public class WhereClause<T> internal constructor(
    internal val selectCondition: SelectCondition,
) : ConditionClause<T>(selectCondition) {

    override val clauseName: String = "WHERE"
}

/**
 * Creates a WHERE clause for use in DSL operations.
 *
 * Usage:
 * ```kotlin
 * SELECT(user) WHERE (user.id EQ 42)
 * UPDATE(user) SET { it.name = "John" } WHERE (user.id EQ 42)
 * DELETE(user) WHERE (user.age GT 18)
 * ```
 */
@StatementDslMaker
public fun <T> WHERE(condition: SelectCondition): WhereClause<T> = WhereClause(condition)

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.WHERE(condition: SelectCondition): WhereSelectStatement<T> =
    appendToWhere(WhereClause(condition)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> UpdateStatementWithoutWhereClause<T>.WHERE(condition: SelectCondition): String {
    val statement = UpdateDeleteStatement(buildString {
        append(sqlStr)
        append(WhereClause<T>(condition).clauseStr)
    }, connection, condition.parameters)
    statementContainer changeLastStatement statement
    return statement.sqlStr
}