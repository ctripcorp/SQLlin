package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity

/**
 * The SQL clause that could used for 'select' statement.
 * @author yaqiao
 */

sealed interface SelectClause<T : DBEntity<T>> : Clause<T> {
    val clauseStr: String
}