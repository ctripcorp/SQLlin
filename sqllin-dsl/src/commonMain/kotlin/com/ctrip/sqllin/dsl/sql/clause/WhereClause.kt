package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.JoinSelectStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement
import com.ctrip.sqllin.dsl.sql.statement.UpdateStatementWithoutWhereClause
import com.ctrip.sqllin.dsl.sql.statement.WhereSelectStatement

/**
 * SQL "WHERE" clause
 * @author yaqiao
 */

public class WhereClause<T : DBEntity<T>> internal constructor(selectCondition: SelectCondition) : ConditionClause<T>(selectCondition) {

    override val clauseName: String = "WHERE"
}

public fun <T : DBEntity<T>> WHERE(condition: SelectCondition): WhereClause<T> = WhereClause(condition)

public infix fun <T : DBEntity<T>> JoinSelectStatement<T>.WHERE(condition: SelectCondition): WhereSelectStatement<T> =
    appendToWhere(WhereClause(condition)).also {
        container changeLastStatement it
    }

public infix fun <T : DBEntity<T>> UpdateStatementWithoutWhereClause<T>.WHERE(condition: SelectCondition): String {
    val statement = UpdateDeleteStatement(buildString {
        append(sqlStr)
        append(WhereClause<T>(condition).clauseStr)
    }, connection)
    statementContainer changeLastStatement statement
    return statement.sqlStr
}