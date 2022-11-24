package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.GroupBySelectStatement
import com.ctrip.sqllin.dsl.sql.statement.HavingSelectStatement

/**
 * SQL 'HAVING' clause by select statement.
 * @author yaqiao
 */

internal class HavingClause<T : DBEntity<T>>(selectCondition: SelectCondition) : ConditionClause<T>(selectCondition) {

    override val clauseName: String = "HAVING"
}

infix fun <T : DBEntity<T>> GroupBySelectStatement<T>.HAVING(condition: SelectCondition): HavingSelectStatement<T> =
    appendToHaving(HavingClause(condition)).also {
        container changeLastStatement it
    }