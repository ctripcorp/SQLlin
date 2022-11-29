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
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * SQL 'LIMIT' clause by select statement
 * @author yaqiao
 */

public class LimitClause<T : DBEntity<T>> internal constructor(
    private val count: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " LIMIT $count"
}

public fun <T : DBEntity<T>> LIMIT(count: Int): LimitClause<T> = LimitClause(count)

public infix fun <T : DBEntity<T>> WhereSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> OrderBySelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> HavingSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

/**
 * SQL 'OFFSET' clause by select statement
 */

public class OffsetClause<T : DBEntity<T>> internal constructor(
    private val rowNo: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " OFFSET $rowNo"
}

public infix fun <T : DBEntity<T>> LimitSelectStatement<T>.OFFSET(rowNo: Int): FinalSelectStatement<T> =
    appendToFinal(OffsetClause(rowNo)).also {
        container changeLastStatement it
    }