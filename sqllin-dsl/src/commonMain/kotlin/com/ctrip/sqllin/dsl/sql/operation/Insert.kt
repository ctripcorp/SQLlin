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
import com.ctrip.sqllin.dsl.sql.compiler.InsertRows
import com.ctrip.sqllin.dsl.sql.compiler.encodeInsertRows
import com.ctrip.sqllin.dsl.sql.statement.InsertBatch

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
    fun <T> insert(table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>, isInsertWithId: Boolean = false): SingleStatement =
        insertRows(sqlStr, table, connection, entities, isInsertWithId, isAtomic = true)

    /**
     * Builds an INSERT statement of [entities] with a conflict resolution, which [insert] starts with, as
     * `INSERT OR IGNORE INTO `.
     *
     * The primary key is written even when the database would assign it, as a conflict on it could never be seen
     * otherwise; a null key still lets the database assign it.
     */
    fun <T> insertOr(insert: String, table: Table<T>, connection: DatabaseConnection, entities: Iterable<T>): SingleStatement =
        insertRows(insert, table, connection, entities, isInsertWithId = true, isAtomic = insert != INSERT_OR_FAIL)

    /**
     * The most parameters that an INSERT of entities binds in one statement: the limit of SQLite before 3.32.0, as on
     * Android before API 31, by default, which is 32766 from then on. An INSERT of more rows runs as several INSERTs,
     * whatever the SQLite, as its limit is a compile-time option that a connection can lower, and that not every platform
     * can read.
     */
    const val MAX_PARAMETERS: Int = 999

    private const val INSERT_OR_FAIL = "INSERT OR FAIL INTO "

    /**
     * Builds the INSERT of [entities], which [insert] starts with, as `INSERT INTO ` or `INSERT OR IGNORE INTO `.
     *
     * Its SQL is one statement of all the rows, which a trigger writes into its body, where nothing is bound. Where the
     * rows bind more than [MAX_PARAMETERS] values, it runs as batches of rows that bind at most that many each, a row
     * never being split: in a transaction, so that it inserts all its rows or none, as one statement does, unless it is
     * [isAtomic] not, as `INSERT OR FAIL`, which keeps the rows a statement inserted before the one that fails, and so
     * those of the batches before.
     */
    private fun <T> insertRows(
        insert: String,
        table: Table<T>,
        connection: DatabaseConnection,
        entities: Iterable<T>,
        isInsertWithId: Boolean,
        isAtomic: Boolean,
    ): SingleStatement {
        val parameters = ArrayList<Any?>()
        val rows = encodeInsertRows(table, entities, parameters, isInsertWithId)
        val start = "$insert${table.tableName} ${rows.columns} values "
        val sql = start + rows.rows.joinToString(",")
        return InsertStatement(sql, connection, parameters, table.tableName, batchesOf(start, rows, parameters), isAtomic)
    }

    /**
     * The batches of [rows] that bind at most [MAX_PARAMETERS] values each, or null where all of them bind no more.
     */
    private fun batchesOf(start: String, rows: InsertRows, parameters: List<Any?>): List<InsertBatch>? {
        if (parameters.size <= MAX_PARAMETERS)
            return null
        val batches = ArrayList<InsertBatch>()
        var first = 0
        var firstParameter = 0
        var count = 0
        fun flush(end: Int) {
            val lastParameter = firstParameter + count
            batches.add(
                InsertBatch(
                    start + rows.rows.subList(first, end).joinToString(","),
                    parameters.subList(firstParameter, lastParameter).toTypedArray(),
                )
            )
            first = end
            firstParameter = lastParameter
            count = 0
        }
        rows.parameterCounts.forEachIndexed { index, rowCount ->
            if (index > first && count + rowCount > MAX_PARAMETERS)
                flush(index)
            count += rowCount
        }
        flush(rows.rows.size)
        return batches
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
