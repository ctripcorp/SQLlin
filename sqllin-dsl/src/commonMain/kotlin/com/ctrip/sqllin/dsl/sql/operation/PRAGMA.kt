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
import com.ctrip.sqllin.dsl.sql.pragma.AutoVacuum
import com.ctrip.sqllin.dsl.sql.pragma.CheckpointMode
import com.ctrip.sqllin.dsl.sql.pragma.CheckpointResult
import com.ctrip.sqllin.dsl.sql.pragma.ColumnInfo
import com.ctrip.sqllin.dsl.sql.pragma.ColumnKind
import com.ctrip.sqllin.dsl.sql.pragma.DatabaseInfo
import com.ctrip.sqllin.dsl.sql.pragma.ForeignKeyInfo
import com.ctrip.sqllin.dsl.sql.pragma.ForeignKeyViolation
import com.ctrip.sqllin.dsl.sql.pragma.FunctionInfo
import com.ctrip.sqllin.dsl.sql.pragma.IndexColumn
import com.ctrip.sqllin.dsl.sql.pragma.IndexInfo
import com.ctrip.sqllin.dsl.sql.pragma.IndexOrigin
import com.ctrip.sqllin.dsl.sql.pragma.RelationInfo
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
 * - **foreign_keys**: Enable or disable foreign key constraint enforcement, or read whether it is enabled
 * - **optimize**: Gather the statistics that would help the query planner
 * - **integrity_check**, **quick_check**, **foreign_key_check**: Check the database, or a table
 * - **table_info**, **table_xinfo**, **table_list**, **index_list**, **index_xinfo**, **foreign_key_list**: Read the
 *   schema
 * - **compile_options**, **collation_list**, **database_list**, **function_list**, **module_list**, **pragma_list**:
 *   Read what the SQLite and the connection have
 * - **wal_checkpoint**, **page_count**, **freelist_count**, **page_size**, **application_id**, **auto_vacuum**:
 *   Checkpoint the WAL, read the file's size, and read and set what its header holds
 * - **defer_foreign_keys**, **recursive_triggers**, **legacy_alter_table**, **ignore_check_constraints**: Set how
 *   statements run
 *
 * The names a PRAGMA takes are written as string literals, which SQLite accepts there, so that any name is quoted.
 * The columns of the rows a PRAGMA returns are looked up by name once there is a row, as an older SQLite names no
 * columns for a PRAGMA that returns no rows, as `table_info` of a table that doesn't exist. An older SQLite ignores a
 * PRAGMA it doesn't have, rather than failing, which then returns no columns: a PRAGMA that only newer versions have
 * checks that its rows have their first column, so that it fails rather than returns nothing, without asking the
 * SQLite for its version.
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
     * `PRAGMA foreign_keys`, whether this connection enforces foreign keys.
     */
    fun isForeignKeysEnabled(connection: DatabaseConnection): PragmaStatement<Boolean> =
        single("foreign_keys", connection) { it.getLong(0) != 0L }

    /**
     * `PRAGMA integrity_check(N)` or `PRAGMA quick_check(N)`: the problems found, up to [maxErrors], or none, rather
     * than the single row `ok` the PRAGMA returns when it finds none.
     */
    fun check(pragma: String, maxErrors: Int, connection: DatabaseConnection): PragmaStatement<List<String>> =
        statement("$pragma($maxErrors)", connection, read = ::problems)

    /**
     * `PRAGMA integrity_check(table)` or `PRAGMA quick_check(table)`, of the table named [table] and its indexes,
     * which SQLite 3.33.0 has; an older one takes the name for a number of errors, which it ignores, so it checks the
     * whole database.
     */
    fun check(pragma: String, table: String, connection: DatabaseConnection): PragmaStatement<List<String>> =
        statement("$pragma(${sqlLiteral(table)})", connection, read = ::problems)

    private fun problems(cursor: CommonCursor): List<String> {
        val messages = buildList { cursor.forEachRow { add(cursor.getString(0).orEmpty()) } }
        return if (messages == listOf("ok")) emptyList() else messages
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
     * `PRAGMA table_xinfo`, the columns of the table or view named [relation], its hidden and generated ones included,
     * which SQLite 3.26.0 has.
     */
    fun tableXinfo(relation: String, connection: DatabaseConnection): PragmaStatement<List<ColumnInfo>> =
        newer("table_xinfo(${sqlLiteral(relation)})", "cid", "SQLite 3.26.0, which Android has from API 30 on", connection) { cursor ->
            cursor.mapRows {
                ColumnInfo(
                    int("cid"), string("name")!!, string("type").orEmpty(), boolean("notnull"), string("dflt_value"), int("pk"),
                    columnKind(int("hidden")),
                )
            }
        }

    /**
     * `PRAGMA table_list`, the tables and views of all databases of the connection, which SQLite 3.37.0 has.
     */
    fun tableList(connection: DatabaseConnection): PragmaStatement<List<RelationInfo>> =
        newer("table_list", "schema", "SQLite 3.37.0, which Android has from API 34 on", connection) { cursor ->
            cursor.mapRows {
                RelationInfo(string("schema")!!, string("name")!!, string("type")!!, int("ncol"), boolean("wr"), boolean("strict"))
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
     * `PRAGMA compile_options`, `PRAGMA collation_list`, `PRAGMA module_list` or `PRAGMA pragma_list`: the values of
     * [column], one in each row. A SQLite can be built without compile_options, as Android's is, and the others, which
     * SQLite 3.30.0 has in every build, only [requirement] has.
     */
    fun names(pragma: String, column: String, requirement: String?, connection: DatabaseConnection): PragmaStatement<List<String>> =
        newer(pragma, column, requirement, connection) { cursor -> cursor.mapRows { string(column)!! } }

    /**
     * `PRAGMA database_list`, the databases of the connection.
     */
    fun databaseList(connection: DatabaseConnection): PragmaStatement<List<DatabaseInfo>> =
        newer("database_list", "name", null, connection) { cursor ->
            cursor.mapRows { DatabaseInfo(string("name")!!, string("file").orEmpty()) }
        }

    /**
     * `PRAGMA function_list`, the SQL functions of the connection, which SQLite 3.30.0 has in every build.
     */
    fun functionList(connection: DatabaseConnection): PragmaStatement<List<FunctionInfo>> =
        newer("function_list", "name", "SQLite 3.30.0, which Android has from API 31 on", connection) { cursor ->
            cursor.mapRows {
                FunctionInfo(string("name")!!, boolean("builtin"), string("type")!!, string("enc")!!, int("narg"), longOrNull("flags") ?: 0L)
            }
        }

    /**
     * `PRAGMA application_id`, the number at offset 68 of the header of the database file.
     */
    fun applicationId(connection: DatabaseConnection): PragmaStatement<Int> = single("application_id", connection) { it.getInt(0) }

    /**
     * `PRAGMA application_id = id`, which returns no row.
     */
    fun applicationId(id: Int, connection: DatabaseConnection): PragmaStatement<Unit> =
        PragmaStatement("${sqlStr}application_id=$id", connection, null)

    /**
     * `PRAGMA auto_vacuum`, as the header of the database file holds it, which SQLite takes from what the connection
     * last read of the header, so that it is read in a transaction of its own unless [inTransaction].
     */
    fun autoVacuum(inTransaction: Boolean, connection: DatabaseConnection): PragmaStatement<AutoVacuum> =
        single("auto_vacuum", connection, inOwnTransaction = !inTransaction) { cursor ->
            when (val mode = cursor.getInt(0)) {
                0 -> AutoVacuum.NONE
                1 -> AutoVacuum.FULL
                2 -> AutoVacuum.INCREMENTAL
                else -> throw IllegalStateException("Unknown auto_vacuum: $mode")
            }
        }

    /**
     * `PRAGMA auto_vacuum = mode`, which returns no row.
     */
    fun autoVacuum(mode: AutoVacuum, connection: DatabaseConnection): PragmaStatement<Unit> =
        PragmaStatement("${sqlStr}auto_vacuum=${mode.name}", connection, null)

    /**
     * `PRAGMA page_count`, `PRAGMA freelist_count` or `PRAGMA page_size`. SQLite takes page_size from what the
     * connection last read of the header of the database file, so that it is read in a transaction of its own unless
     * [inTransaction].
     */
    fun size(pragma: String, inTransaction: Boolean, connection: DatabaseConnection): PragmaStatement<Long> =
        single(pragma, connection, inOwnTransaction = pragma == "page_size" && !inTransaction) { it.getLong(0) }

    /**
     * `PRAGMA defer_foreign_keys`, `PRAGMA recursive_triggers`, `PRAGMA legacy_alter_table` or
     * `PRAGMA ignore_check_constraints` set to [flag], which returns no row. Android's driver runs the last three on
     * every connection of the database, as SQLite compiles them into the statements of each.
     */
    fun set(pragma: String, flag: Boolean, connection: DatabaseConnection): PragmaStatement<Unit> =
        PragmaStatement("$sqlStr$pragma=${if (flag) 1 else 0}", connection, null)

    private fun <R> statement(
        pragma: String,
        connection: DatabaseConnection,
        inOwnTransaction: Boolean = false,
        read: (CommonCursor) -> R,
    ): PragmaStatement<R> = PragmaStatement("$sqlStr$pragma", connection, read, inOwnTransaction)

    /**
     * A PRAGMA that returns a single value, which [read] reads from its row.
     */
    private fun <R> single(
        pragma: String,
        connection: DatabaseConnection,
        inOwnTransaction: Boolean = false,
        read: (CommonCursor) -> R,
    ): PragmaStatement<R> =
        statement(pragma, connection, inOwnTransaction) { cursor ->
            check(cursor.next()) { "PRAGMA $pragma returned no row." }
            read(cursor)
        }

    /**
     * A PRAGMA that only newer versions of SQLite have, or that a SQLite can be built without, which [requirement]
     * describes, if it is a version: one that doesn't have it ignores it, rather than failing, so that it returns no
     * columns, which the first of its columns, [column], checks, rather than the version of SQLite.
     */
    private fun <R> newer(
        pragma: String,
        column: String,
        requirement: String?,
        connection: DatabaseConnection,
        read: (CommonCursor) -> R,
    ): PragmaStatement<R> = statement(pragma, connection) { cursor ->
        // Every driver throws for a column that the rows don't have
        val hasColumn = runCatching { cursor.getColumnIndex(column) }.getOrNull()?.let { it >= 0 } ?: false
        check(hasColumn) {
            val name = pragma.substringBefore('(')
            if (requirement == null) "This SQLite doesn't have PRAGMA $name." else "This SQLite doesn't have PRAGMA $name, which needs $requirement."
        }
        read(cursor)
    }

    private fun columnKind(hidden: Int): ColumnKind = when (hidden) {
        0 -> ColumnKind.ORDINARY
        1 -> ColumnKind.HIDDEN
        2 -> ColumnKind.VIRTUAL_GENERATED
        3 -> ColumnKind.STORED_GENERATED
        else -> throw IllegalStateException("Unknown kind of column: $hidden")
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
