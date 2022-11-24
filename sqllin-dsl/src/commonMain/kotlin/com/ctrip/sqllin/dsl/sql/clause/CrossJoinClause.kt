package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table

/**
 * SQL "CROSS JOIN" clause
 * @author yaqiao
 */

internal class CrossJoinClause<R : DBEntity<R>>(vararg tables: Table<*>) : NaturalJoinClause<R>(*tables) {
    override val clauseName: String = " CROSS JOIN "
}

public fun <R : DBEntity<R>> CROSS_JOIN(vararg tables: Table<*>): NaturalJoinClause<R> = CrossJoinClause(*tables)