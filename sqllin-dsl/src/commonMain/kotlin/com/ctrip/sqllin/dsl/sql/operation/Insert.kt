/*
 * Copyright (C) 2022 Ctrip.com.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.InsertStatement
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.ResultColumn
import com.ctrip.sqllin.dsl.sql.clause.writtenColumns
import com.ctrip.sqllin.dsl.sql.compiler.appendDBColumnName
import com.ctrip.sqllin.dsl.sql.compiler.encodeEntities2InsertValues

/**
 * INSERT operation builder.
 *
 * Constructs INSERT statements by encoding entity objects into SQL VALUES clauses with
 * parameterized queries. Supports both auto-generated primary keys and user-provided keys.
 *
 * @author Yuang Qiao
 */
internal object Insert : Operation {

    override val sqlStr: String
        get() = "INSERT INTO "

    /**
     * Builds an INSERT statement for the given entities.
     *
     * Generates SQL in the format:
     * ```
     * INSERT INTO table_name (column1, column2, ...) VALUES (?, ?, ...), (?, ?, ...), ...
     * ```
     *
     * @param table Table definition containing serialization metadata
     * @param connection Database connection for execution
     * @param entities Entities to insert
     * @param isInsertWithId Whether to include the primary key column for auto-increment keys
     * @return INSERT statement ready for execution
     */
    fun <T> insert(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>, isInsertWithId: Boolean = false): SingleStatement {
        val parameters = ArrayList<Any?>()
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(' ')
            encodeEntities2InsertValues(table, this,entities, parameters, isInsertWithId)
        }
        return InsertStatement(sql, connection, parameters, table.tableName)
    }

    fun <T> insertOrReplace(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>): SingleStatement {
        val parameters = ArrayList<Any?>()
        val sql = buildString {
            append("INSERT OR REPLACE INTO ")
            append(table.tableName)
            append(' ')
            encodeEntities2InsertValues(table, this, entities, parameters, isInsertWithId = true)
        }
        return InsertStatement(sql, connection, parameters, table.tableName)
    }

    fun <T> insertOrIgnore(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>): SingleStatement {
        val parameters = ArrayList<Any?>()
        val sql = buildString {
            append("INSERT OR IGNORE INTO ")
            append(table.tableName)
            append(' ')
            // Write the primary key even when the database would assign it, or a conflict on it could never be seen
            encodeEntities2InsertValues(table, this, entities, parameters, isInsertWithId = true)
        }
        return InsertStatement(sql, connection, parameters, table.tableName)
    }

    /**
     * Builds an INSERT statement that inserts the rows [select] returns.
     *
     * Generates SQL in the format:
     * ```
     * INSERT INTO table_name (column1, column2, ...) SELECT ...
     * ```
     *
     * The columns are the properties of [select]'s result type, the table's row type, in the order the SELECT
     * selects them, so every column gets a value, the primary key included.
     *
     * @param insert The keywords that start the statement: `INSERT INTO `, `INSERT OR IGNORE INTO ` or
     * `INSERT OR REPLACE INTO `
     * @param table The table to insert into
     * @param connection Database connection for execution
     * @param select The SELECT whose rows are inserted
     * @return INSERT statement ready for execution
     */
    fun <T> insert(insert: String, table: Table<T>, connection: DatabaseConnection, select: SelectStatement<T>): SingleStatement {
        val sql = buildString {
            append(insert)
            append(table.tableName)
            append('(')
            appendDBColumnName(select.deserializer.descriptor)
            append(") ")
            append(select.sqlStr)
        }
        return InsertStatement(sql, connection, select.parameters?.toMutableList(), table.tableName)
    }

    /**
     * `INSERT INTO table(columns) VALUES (expressions)`, of [values], the expressions given to properties of the row
     * type, which can't read any table but the rows of a trigger. The columns left out take their default values, or
     * NULL.
     */
    fun <T> insert(insert: String, table: Table<T>, connection: DatabaseConnection, values: Iterable<ResultColumn<T>>): SingleStatement {
        val columns = writtenColumns(table, values, insert.trim().removeSuffix(" INTO"), emptySet())
        val sql = buildString {
            append(insert)
            append(table.tableName)
            append('(')
            append(columns.keys.joinToString(","))
            append(") VALUES (")
            columns.values.forEachIndexed { index, element ->
                if (index > 0)
                    append(',')
                element.appendSQL(this)
            }
            append(')')
        }
        return InsertStatement(sql, connection, null, table.tableName)
    }

    /**
     * `INSERT INTO table DEFAULT VALUES`, which inserts a row of the default values of all columns, or NULL where a
     * column has none.
     */
    fun insertDefaultValues(insert: String, table: Table<*>, connection: DatabaseConnection): SingleStatement =
        InsertStatement("$insert${table.tableName} DEFAULT VALUES", connection, null, table.tableName)
}
