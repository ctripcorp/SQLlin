package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.DBEntity

/**
 * Update statement without 'WHERE' clause,
 * that could execute or link 'WHERE' clause.
 * @author yaqiao
 */

public class UpdateStatementWithoutWhereClause<T : DBEntity<T>> internal constructor(
    preSQLStr: String,
    internal val statementContainer: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(preSQLStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr)
}

public class UpdateDeleteStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr)
}

public class InsertStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeInsert(sqlStr)
}
