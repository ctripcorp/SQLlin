package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * The SQL clause that could used for 'select' statement.
 * @author yaqiao
 */

public sealed interface SelectClause<T : DBEntity<T>> : Clause<T> {
    public val clauseStr: String
}