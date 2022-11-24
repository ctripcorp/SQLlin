package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.WhereClause
import com.ctrip.sqllin.dsl.sql.statement.UpdateDeleteStatement

/**
 * SQL delete.
 * @author yaqiao
 */

internal object Delete : Operation {

    override val sqlStr: String
        get() = "DELETE FROM "

    fun <T : DBEntity<T>> delete(table: Table<*>, connection: DatabaseConnection, whereClause: WhereClause<T>): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
            append(whereClause.clauseStr)
        }
        return UpdateDeleteStatement(sql, connection)
    }

    fun deleteAllEntity(table: Table<*>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            buildBaseDeleteStatement(table)
        }
        return UpdateDeleteStatement(sql, connection)
    }

    private fun StringBuilder.buildBaseDeleteStatement(table: Table<*>) {
        append(sqlStr)
        append(table.tableName)
    }
}