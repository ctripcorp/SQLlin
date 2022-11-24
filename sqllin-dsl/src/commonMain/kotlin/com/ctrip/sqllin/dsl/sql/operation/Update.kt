package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.statement.StatementContainer
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.clause.SetClause
import com.ctrip.sqllin.dsl.sql.statement.UpdateStatementWithoutWhereClause

/**
 * SQL update.
 * @author yaqiao
 */

internal object Update : Operation {

    override val sqlStr: String
        get() = "UPDATE "

    fun <T : DBEntity<T>> update(
        table: Table<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
        clause: SetClause<T>,
    ): UpdateStatementWithoutWhereClause<T> {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(" SET ")
            append(clause.finalize())
        }
        return UpdateStatementWithoutWhereClause(sql, container, connection)
    }
}