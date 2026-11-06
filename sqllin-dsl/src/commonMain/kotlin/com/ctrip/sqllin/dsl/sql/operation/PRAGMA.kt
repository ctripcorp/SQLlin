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

import com.ctrip.sqllin.driver.CommonCursor
import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.compiler.sqlLiteral
import com.ctrip.sqllin.dsl.sql.pragma.CheckpointMode
import com.ctrip.sqllin.dsl.sql.pragma.CheckpointResult
import com.ctrip.sqllin.dsl.sql.pragma.ColumnInfo
import com.ctrip.sqllin.dsl.sql.pragma.ForeignKeyInfo
import com.ctrip.sqllin.dsl.sql.pragma.ForeignKeyViolation
import com.ctrip.sqllin.dsl.sql.pragma.IndexColumn
import com.ctrip.sqllin.dsl.sql.pragma.IndexInfo
import com.ctrip.sqllin.dsl.sql.pragma.IndexOrigin
import com.ctrip.sqllin.dsl.sql.statement.PragmaStatement
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * SQLite PRAGMA command operations for database configuration.
 *
 * This object provides methods to generate PRAGMA SQL statements that configure
 * various SQLite database settings. PRAGMA statements are special SQLite commands
 * that query or modify database operation parameters.
 *
 * ### Available PRAGMAs
 * - **foreign_keys**: Enable or disable foreign key constraint enforcement
 * - **optimize**: Gather the statistics that would help the query planner
 * - **integrity_check**, **quick_check**, **foreign_key_check**: Check the database
 * - **table_info**, **index_list**, **index_xinfo**, **foreign_key_list**: Read the schema
 * - **wal_checkpoint**, **page_count**, **freelist_count**, **page_size**: Checkpoint the WAL, and read the file's size
 * - **defer_foreign_keys**: Defer the checks of foreign keys to the commit
 *
 * The names a PRAGMA takes are written as string literals, which SQLite accepts there, so that any name is quoted.
 * The columns of the rows a PRAGMA returns are looked up by name once there is a row, as an older SQLite names no
 * columns for a PRAGMA that returns no rows, as `table_info` of a table that doesn't exist.
 *
 * ### Usage
 * This object is used internally by [DatabaseScope][com.ctrip.sqllin.dsl.DatabaseScope]
 * to generate PRAGMA statements when calling functions like `PRAGMA_FOREIGN_KEYS`.
 *
 * @author Yuang Qiao
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
internal object PRAGMA : Operation {

    override val sqlStr: String
        get() = "PRAGMA "

    /**
     * Generates a PRAGMA statement to enable or disable foreign key constraint enforcement.
     *
     * SQLite disables foreign key constraints by default for backward compatibility.
     * This method creates a statement that enables or disables foreign key enforcement
     * for the current database connection.
     *
     * ### Important Notes
     * - This setting is **per-connection** and must be set each time a database is opened
     * - The setting **cannot be changed** inside a transaction
     * - When enabled, all INSERT, UPDATE, and DELETE operations will enforce foreign key constraints
     * - When disabled, foreign key constraints are part of the schema but not enforced
     *
     * ### Generated SQL
     * ```sql
     * PRAGMA foreign_keys=1;  -- Enable foreign keys
     * PRAGMA foreign_keys=0;  -- Disable foreign keys
     * ```
     *
     * ### Example Usage
     * ```kotlin
     * database {
     *     PRAGMA_FOREIGN_KEYS(true)  // Enable enforcement
     *     CREATE(OrderTable)  // Create table with foreign keys
     *
     *     // Now foreign key constraints will be enforced
     *     OrderTable INSERT Order(userId = 999)  // Fails if user 999 doesn't exist
     * }
     * ```
     *
     * @param enable `true` to enable foreign key enforcement, `false` to disable
     * @param connection The database connection to execute the statement on
     * @return A [SingleStatement] that executes the PRAGMA command
     *
     * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
     * @see com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup
     * @see com.ctrip.sqllin.dsl.annotation.References
     */
    fun foreignKeys(enable: Boolean, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append("foreign_keys=")
            append(if (enable) "1;" else "0;")
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * `PRAGMA optimize`, which runs ANALYZE on the tables whose statistics would help the query planner, if any.
     */
    fun optimize(connection: DatabaseConnection): SingleStatement = TableStructureStatement("${sqlStr}optimize", connection)

    /**
     * `PRAGMA integrity_check(N)` or `PRAGMA quick_check(N)`: the problems found, up to [maxErrors], or none, rather
     * than the single row `ok` the PRAGMA returns when it finds none.
     */
    fun check(pragma: String, maxErrors: Int, connection: DatabaseConnection): PragmaStatement<List<String>> =
        statement("$pragma($maxErrors)", connection) { cursor ->
            val messages = buildList { cursor.forEachRow { add(cursor.getString(0).orEmpty()) } }
            if (messages == listOf("ok")) emptyList() else messages
        }

    /**
     * `PRAGMA foreign_key_check`, of all tables, or of the table named [table].
     */
    fun foreignKeyCheck(table: String?, connection: DatabaseConnection): PragmaStatement<List<ForeignKeyViolation>> =
        statement(if (table == null) "foreign_key_check" else "foreign_key_check(${sqlLiteral(table)})", connection) { cursor ->
            cursor.mapRows { ForeignKeyViolation(string("table")!!, longOrNull("rowid"), string("parent")!!, int("fkid")) }
        }

    /**
     * `PRAGMA table_info`, the columns of the table or view named [relation].
     */
    fun tableInfo(relation: String, connection: DatabaseConnection): PragmaStatement<List<ColumnInfo>> =
        statement("table_info(${sqlLiteral(relation)})", connection) { cursor ->
            cursor.mapRows {
                ColumnInfo(int("cid"), string("name")!!, string("type").orEmpty(), boolean("notnull"), string("dflt_value"), int("pk"))
            }
        }

    /**
     * `PRAGMA index_list`, the indexes of the table named [table].
     */
    fun indexList(table: String, connection: DatabaseConnection): PragmaStatement<List<IndexInfo>> =
        statement("index_list(${sqlLiteral(table)})", connection) { cursor ->
            cursor.mapRows { IndexInfo(string("name")!!, boolean("unique"), indexOrigin(string("origin")), boolean("partial")) }
        }

    /**
     * `PRAGMA index_xinfo`, the columns of the index named [index], those it is keyed by and those it holds besides.
     */
    fun indexXinfo(index: String, connection: DatabaseConnection): PragmaStatement<List<IndexColumn>> =
        statement("index_xinfo(${sqlLiteral(index)})", connection) { cursor ->
            cursor.mapRows { IndexColumn(int("cid"), string("name"), boolean("desc"), string("coll"), boolean("key")) }
        }

    /**
     * `PRAGMA foreign_key_list`, the columns of the foreign keys of the table named [table].
     */
    fun foreignKeyList(table: String, connection: DatabaseConnection): PragmaStatement<List<ForeignKeyInfo>> =
        statement("foreign_key_list(${sqlLiteral(table)})", connection) { cursor ->
            cursor.mapRows {
                ForeignKeyInfo(
                    int("id"), int("seq"), string("table")!!, string("from")!!, string("to"),
                    string("on_update")!!, string("on_delete")!!, string("match")!!,
                )
            }
        }

    /**
     * `PRAGMA wal_checkpoint(mode)`, which returns a row, so that it runs as a query.
     */
    fun walCheckpoint(mode: CheckpointMode, connection: DatabaseConnection): PragmaStatement<CheckpointResult> =
        statement("wal_checkpoint(${mode.name})", connection) { cursor ->
            cursor.mapRows { CheckpointResult(boolean("busy"), int("log"), int("checkpointed")) }.single()
        }

    /**
     * `PRAGMA page_count`, `PRAGMA freelist_count` or `PRAGMA page_size`.
     */
    fun size(pragma: String, connection: DatabaseConnection): PragmaStatement<Long> = single(pragma, connection) { it.getLong(0) }

    /**
     * `PRAGMA defer_foreign_keys` set to [flag], which returns no row.
     */
    fun set(pragma: String, flag: Boolean, connection: DatabaseConnection): PragmaStatement<Unit> =
        PragmaStatement("$sqlStr$pragma=${if (flag) 1 else 0}", connection, null)

    private fun <R> statement(pragma: String, connection: DatabaseConnection, read: (CommonCursor) -> R): PragmaStatement<R> =
        PragmaStatement("$sqlStr$pragma", connection, read)

    /**
     * A PRAGMA that returns a single value, which [read] reads from its row.
     */
    private fun <R> single(pragma: String, connection: DatabaseConnection, read: (CommonCursor) -> R): PragmaStatement<R> =
        statement(pragma, connection) { cursor ->
            check(cursor.next()) { "PRAGMA $pragma returned no row." }
            read(cursor)
        }

    private fun indexOrigin(code: String?): IndexOrigin = when (code) {
        "c" -> IndexOrigin.CREATE_INDEX
        "u" -> IndexOrigin.UNIQUE
        "pk" -> IndexOrigin.PRIMARY_KEY
        else -> throw IllegalStateException("Unknown origin of an index: $code")
    }

    /**
     * Reads each row with [read], which looks the columns up by name once there is a row.
     */
    private fun <T> CommonCursor.mapRows(read: PragmaRow.() -> T): List<T> {
        val row = PragmaRow(this)
        return buildList { forEachRow { add(row.read()) } }
    }

    /**
     * A row of a PRAGMA, whose columns it looks up by name the first time they are read.
     */
    private class PragmaRow(private val cursor: CommonCursor) {

        private val columns = HashMap<String, Int>()

        private fun column(name: String): Int = columns.getOrPut(name) { cursor.getColumnIndex(name) }

        fun string(name: String): String? = cursor.getString(column(name))

        fun int(name: String): Int = cursor.getInt(column(name))

        fun longOrNull(name: String): Long? = column(name).let { if (cursor.isNull(it)) null else cursor.getLong(it) }

        fun boolean(name: String): Boolean = cursor.getLong(column(name)) != 0L
    }
}
