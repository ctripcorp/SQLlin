package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.DBEntity

/**
 * Update statement without 'WHERE' clause,
 * that could execute or link 'WHERE' clause.
 * @author yaqiao
 */

class UpdateStatementWithoutWhereClause<T : DBEntity<T>> internal constructor(
    preSQLStr: String,
    internal val statementContainer: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(preSQLStr) {
    override fun execute() = connection.executeUpdateDelete(sqlStr)
}

class UpdateDeleteStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    override fun execute() = connection.executeUpdateDelete(sqlStr)
}

class InsertStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    override fun execute() = connection.executeInsert(sqlStr)
}
