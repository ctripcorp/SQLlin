/*
 * Copyright (C) 2025 Ctrip.com.
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
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.View
import com.ctrip.sqllin.dsl.sql.clause.ClauseElement
import com.ctrip.sqllin.dsl.sql.compiler.appendDBColumnName
import com.ctrip.sqllin.dsl.sql.compiler.inlineParameters
import com.ctrip.sqllin.dsl.sql.statement.SelectStatement
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * CREATE TABLE operation builder.
 *
 * Constructs CREATE TABLE statements by inspecting entity serialization descriptors and
 * generating appropriate SQLite column definitions with type mappings, nullability constraints,
 * and primary key declarations.
 *
 * @author Yuang Qiao
 */
internal object Create : Operation {

    override val sqlStr: String
        get() = "CREATE "

    private const val INDEX = "INDEX "
    private const val UNIQUE_INDEX = "UNIQUE INDEX "

    /**
     * Builds a CREATE VIEW statement for [view], defined by [select].
     *
     * Generates SQL in the format: `CREATE VIEW view_name(column1, column2, ...) AS SELECT ...`, naming the view's
     * columns after the properties of its row type. SQLite doesn't let a view take parameters, so the values of
     * [select] are written into the SQL as literals.
     *
     * @return A [TableStructureStatement] representing the CREATE VIEW operation
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun <T> createView(view: View<T>, select: SelectStatement<T>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append("VIEW ")
            append(view.tableName)
            append('(')
            appendDBColumnName(view.kSerializer().descriptor)
            append(") AS ")
            append(inlineParameters(select.sqlStr, select.parameters))
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Builds a CREATE TABLE statement for the given table definition.
     *
     * @param table Table definition containing entity metadata
     * @param connection Database connection for execution
     * @return CREATE statement ready for execution
     */
    fun <T> createTable(table: Table<T>, connection: DatabaseConnection): SingleStatement =
        TableStructureStatement(table.createSQL, connection)

    /**
     * Builds a CREATE INDEX statement for the specified table and columns.
     *
     * Creates a regular (non-unique) index to improve query performance on the specified columns.
     * The generated SQL follows the format: `CREATE INDEX index_name ON table_name(column1, column2, ...)`
     *
     * @param table Table definition to create the index on
     * @param connection Database connection for execution
     * @param indexName Name for the new index
     * @param columns One or more columns to include in the index
     * @return CREATE INDEX statement ready for execution
     * @throws IllegalArgumentException if no columns are specified
     */
    fun <T> createIndex(table: Table<T>, connection: DatabaseConnection, indexName: String, vararg columns: ClauseElement<*>): SingleStatement {
        require(columns.isNotEmpty()) { "You must create an index for at least one column." }
        return createIndex(INDEX, table, connection, indexName, *columns)
    }

    /**
     * Builds a CREATE UNIQUE INDEX statement for the specified table and columns.
     *
     * Creates a unique index that enforces uniqueness constraints on the indexed columns
     * while also improving query performance. The generated SQL follows the format:
     * `CREATE UNIQUE INDEX index_name ON table_name(column1, column2, ...)`
     *
     * @param table Table definition to create the unique index on
     * @param connection Database connection for execution
     * @param indexName Name for the new unique index
     * @param columns One or more columns to include in the unique index
     * @return CREATE UNIQUE INDEX statement ready for execution
     * @throws IllegalArgumentException if no columns are specified
     */
    fun <T> createUniqueIndex(table: Table<T>, connection: DatabaseConnection, indexName: String, vararg columns: ClauseElement<*>): SingleStatement {
        require(columns.isNotEmpty()) { "You must create an index for at least one column." }
        return createIndex(UNIQUE_INDEX, table, connection, indexName, *columns)
    }

    /**
     * Internal helper function to build CREATE INDEX statements with different prefixes.
     *
     * Constructs the SQL string for creating either a regular or unique index based on the prefix.
     *
     * @param prefix Either "INDEX " or "UNIQUE INDEX " to specify the index type
     * @param table Table definition to create the index on
     * @param connection Database connection for execution
     * @param indexName Name for the new index
     * @param columns One or more columns to include in the index
     * @return CREATE INDEX statement ready for execution
     * @throws IllegalArgumentException if no columns are specified
     */
    private fun <T> createIndex(prefix: String, table: Table<T>, connection: DatabaseConnection, indexName: String, vararg columns: ClauseElement<*>): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(prefix)
            append(indexName)
            append(" ON ")
            append(table.tableName)
            append('(')
            if (columns.isEmpty())
                throw IllegalArgumentException("You must create an index for at least one column.")
            columns.forEachIndexed { index, column ->
                if (index > 0)
                    append(',')
                val otherTable = column.columnTables.firstOrNull { it != table.tableName }
                require(otherTable == null) {
                    "The index '$indexName' of table '${table.tableName}' can only hold the columns of that table, but '${column.valueName}' reads table '$otherTable'."
                }
                append(if (column.isFunction) unqualified(column.valueName, table.tableName) else column.valueName)
            }
            append(')')
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Returns the SQL of a function without the name [table] before its columns, outside its string literals, as
     * SQLite doesn't allow `.` in the expressions of an index, which can only read the indexed table anyway.
     */
    private fun unqualified(sql: String, table: String): String = buildString {
        val prefix = "$table."
        var quote: Char? = null
        var index = 0
        while (index < sql.length) {
            val char = sql[index]
            when {
                quote != null -> {
                    // A quote doubled to escape it closes the literal and opens it again
                    if (char == quote)
                        quote = null
                    append(char)
                    index++
                }
                char == '\'' || char == '"' -> {
                    quote = char
                    append(char)
                    index++
                }
                sql.startsWith(prefix, index) && (index == 0 || !sql[index - 1].isIdentifierPart()) -> index += prefix.length
                else -> {
                    append(char)
                    index++
                }
            }
        }
    }

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'
}