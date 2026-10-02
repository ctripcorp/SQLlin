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
import com.ctrip.sqllin.dsl.sql.statement.GroupBySelectStatement
import com.ctrip.sqllin.dsl.sql.statement.HavingSelectStatement

/**
 * HAVING clause for filtering grouped rows in SELECT queries.
 *
 * Similar to WHERE but operates on aggregated data after GROUP BY. Generates SQL in the format:
 * ` HAVING condition`
 *
 * Used to filter groups based on aggregate function results:
 * ```kotlin
 * SELECT(user) GROUP_BY (user.department) HAVING (COUNT(user.id) GT 5)
 * // Returns only departments with more than 5 users
 * ```
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
internal class HavingClause<T>(val selectCondition: SelectCondition) : ConditionClause<T>(selectCondition) {

    override val clauseName: String = "HAVING"
}

@StatementDslMaker
public infix fun <T> GroupBySelectStatement<T>.HAVING(condition: SelectCondition): HavingSelectStatement<T> =
    appendToHaving(HavingClause(condition)).also {
        container changeLastStatement it
    }