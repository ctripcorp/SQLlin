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

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateStatementWithoutWhereClause
import com.ctrip.sqllin.dsl.sql.statement.WhereSelectStatement

/**
 * SQL "WHERE" clause
 * @author yaqiao
 */

public class WhereClause<T : DBEntity<T>> internal constructor(selectCondition: SelectCondition) : ConditionClause<T>(selectCondition) {

    override val clauseName: String = "WHERE"
}

public fun <T : DBEntity<T>> WHERE(condition: SelectCondition): WhereClause<T> = WhereClause(condition)

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.WHERE(condition: SelectCondition): WhereSelectStatement<T> =
    appendToWhere(WhereClause(condition)).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> UpdateStatementWithoutWhereClause<T>.WHERE(condition: SelectCondition): String {
    val statement = UpdateDeleteStatement(buildString {
        append(sqlStr)
        append(WhereClause<T>(condition).clauseStr)
    }, connection)
    statementContainer changeLastStatement statement
    return statement.sqlStr
}