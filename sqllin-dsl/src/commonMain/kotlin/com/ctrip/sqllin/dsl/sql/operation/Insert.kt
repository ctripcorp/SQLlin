package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.InsertStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.compiler.encodeEntities2InsertValues
import com.ctrip.sqllin.dsl.DBEntity

/**
 * SQL insert.
 * @author yaqiao
 */

internal object Insert : Operation {

    override val sqlStr: String
        get() = "INSERT INTO "

    fun <T : DBEntity<T>> insert(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>): SingleStatement {
        val serializer = entities.firstOrNull()?.kSerializer() ?: throw IllegalArgumentException("Param 'entities' must not be empty!!!")
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(' ')
            append(encodeEntities2InsertValues(serializer, entities))
        }
        return InsertStatement(sql, connection)
    }
}