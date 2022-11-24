package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table

/**
 * SQL "INNER JOIN" clause
 * @author yaqiao
 */

internal class InnerJoinClause<R : DBEntity<R>>(
    vararg tables: Table<*>,
) : JoinClause<R>(*tables) {

    override val clauseName: String = " JOIN "
}

fun <R : DBEntity<R>> JOIN(vararg tables: Table<*>): JoinClause<R> = InnerJoinClause(*tables)

inline fun <R : DBEntity<R>> INNER_JOIN(vararg tables: Table<*>): JoinClause<R> = JOIN(*tables)

internal class NaturalInnerJoinClause<R : DBEntity<R>>(
    vararg tables: Table<*>,
) : NaturalJoinClause<R>(*tables) {

    override val clauseName: String = " NATURAL JOIN "
}

fun <R : DBEntity<R>> NATURAL_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NaturalInnerJoinClause(*tables)

inline fun <R : DBEntity<R>> NATURAL_INNER_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = NATURAL_JOIN(*tables)