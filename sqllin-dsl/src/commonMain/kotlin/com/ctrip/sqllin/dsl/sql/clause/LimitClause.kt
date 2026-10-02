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
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * LIMIT clause for restricting the number of rows returned by a SELECT query.
 *
 * Generates SQL in the format: ` LIMIT count`
 *
 * Often combined with OFFSET for pagination:
 * ```kotlin
 * SELECT(user) ORDER_BY (user.id to ASC) LIMIT 10 OFFSET 20  // Skip 20, take 10
 * ```
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public class LimitClause<T> internal constructor(
    private val count: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " LIMIT $count"
}

/**
 * Creates a LIMIT clause to restrict result count.
 */
@StatementDslMaker
public fun <T> LIMIT(count: Int): LimitClause<T> = LimitClause(count)

@StatementDslMaker
public infix fun <T> WhereSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> OrderBySelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> HavingSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

@StatementDslMaker
public infix fun <T> JoinSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

/**
 * OFFSET clause for skipping rows in a SELECT query (pagination).
 *
 * Generates SQL in the format: ` OFFSET rowNo`
 *
 * Must follow a LIMIT clause. Used for pagination:
 * ```kotlin
 * SELECT(user) LIMIT 10 OFFSET 20  // Skip first 20 rows, return next 10
 * ```
 *
 * @param T The entity type this clause operates on
 *
 * @author Yuang Qiao
 */
public class OffsetClause<T> internal constructor(
    private val rowNo: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " OFFSET $rowNo"
}

@StatementDslMaker
public infix fun <T> LimitSelectStatement<T>.OFFSET(rowNo: Int): FinalSelectStatement<T> =
    appendToFinal(OffsetClause(rowNo)).also {
        container changeLastStatement it
    }