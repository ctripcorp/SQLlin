package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table

/**
 * SQL "LEFT OUTER JOIN" clause
 * @author yaqiao
 */

internal class LeftOuterJoinClause<R : DBEntity<R>>(
    vararg tables: Table<*>
) : JoinClause<R>(*tables) {

    override val clauseName: String = " LEFT OUTER JOIN "
}

fun <R : DBEntity<R>> LEFT_OUTER_JOIN(vararg tables: Table<*>): JoinClause<R> = LeftOuterJoinClause(*tables)

internal class NaturalLeftOuterJoinClause<R : DBEntity<R>>(
    vararg tables: Table<*>
) : NaturalJoinClause<R>(*tables) {

    override val clauseName: String = " NATURAL LEFT OUTER JOIN "
}

fun <R : DBEntity<R>> NATURAL_LEFT_OUTER_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NaturalLeftOuterJoinClause(*tables)