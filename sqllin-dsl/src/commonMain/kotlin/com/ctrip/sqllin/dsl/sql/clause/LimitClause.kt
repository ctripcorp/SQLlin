package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.*

/**
 * SQL 'LIMIT' clause by select statement
 * @author yaqiao
 */

class LimitClause<T : DBEntity<T>> internal constructor(
    private val count: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " LIMIT $count"
}

fun <T : DBEntity<T>> LIMIT(count: Int): LimitClause<T> = LimitClause(count)

infix fun <T : DBEntity<T>> WhereSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

infix fun <T : DBEntity<T>> OrderBySelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

infix fun <T : DBEntity<T>> HavingSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

infix fun <T : DBEntity<T>> JoinSelectStatement<T>.LIMIT(count: Int): LimitSelectStatement<T> =
    appendToLimit(LimitClause(count)).also {
        container changeLastStatement it
    }

/**
 * SQL 'OFFSET' clause by select statement
 */

class OffsetClause<T : DBEntity<T>> internal constructor(
    private val rowNo: Int,
) : SelectClause<T> {
    override val clauseStr: String
        get() = " OFFSET $rowNo"
}

infix fun <T : DBEntity<T>> LimitSelectStatement<T>.OFFSET(rowNo: Int): FinalSelectStatement<T> =
    appendToFinal(OffsetClause(rowNo)).also {
        container changeLastStatement it
    }