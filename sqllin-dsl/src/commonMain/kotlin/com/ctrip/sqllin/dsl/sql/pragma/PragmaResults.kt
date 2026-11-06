/*
 * Copyright (C) 2026 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.pragma

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI

/**
 * A column of a table or a view, a row of `PRAGMA table_info`, which `PRAGMA_TABLE_INFO(table)` returns, or of
 * `PRAGMA table_xinfo`, which `PRAGMA_TABLE_XINFO(table)` returns, with the hidden and generated columns.
 *
 * @property index The position of the column in the table, from 0, the `cid` of the PRAGMA
 * @property name The name of the column
 * @property type The type the column was declared with, as `TEXT` or `INT`, or an empty string if it has none
 * @property notNull Whether the column is NOT NULL
 * @property defaultValue The SQL of the column's default value, as `'new'` or `0`, or null if it has none, the
 * `dflt_value` of the PRAGMA
 * @property primaryKeyPosition The position of the column in the primary key, from 1, or 0 if it isn't part of it, the
 * `pk` of the PRAGMA
 * @property kind Whether the column is an ordinary one, a hidden one or a generated one, the `hidden` of
 * `PRAGMA table_xinfo`: `PRAGMA table_info` only lists ordinary columns
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class ColumnInfo(
    val index: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val primaryKeyPosition: Int,
    val kind: ColumnKind = ColumnKind.ORDINARY,
)

/**
 * What kind of column a column is, the `hidden` of `PRAGMA table_xinfo`.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public enum class ColumnKind {
    /** An ordinary column, `0` */
    ORDINARY,
    /** A hidden column of a virtual table, as the column of an FTS table named after the table, `1` */
    HIDDEN,
    /** A generated column that is computed when it is read, `GENERATED ALWAYS AS (...) VIRTUAL`, `2` */
    VIRTUAL_GENERATED,
    /** A generated column that is stored, `GENERATED ALWAYS AS (...) STORED`, `3` */
    STORED_GENERATED,
}

/**
 * A table or a view of the database, a row of `PRAGMA table_list`, which `PRAGMA_TABLE_LIST()` returns.
 *
 * @property schema The database the table belongs to: `main`, `temp`, or the name of an attached database
 * @property name The name of the table
 * @property type What the table is, in SQLite's words: `table`, `view`, `virtual` for a virtual table, as an FTS table,
 * or `shadow` for a table that a virtual table keeps its data in
 * @property columnCount The number of its columns, the `ncol` of the PRAGMA
 * @property isWithoutRowid Whether it is a WITHOUT ROWID table, the `wr` of the PRAGMA
 * @property isStrict Whether it is a STRICT table, the `strict` of the PRAGMA
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class RelationInfo(
    val schema: String,
    val name: String,
    val type: String,
    val columnCount: Int,
    val isWithoutRowid: Boolean,
    val isStrict: Boolean,
)

/**
 * A database of the connection, a row of `PRAGMA database_list`, which `PRAGMA_DATABASE_LIST()` returns.
 *
 * @property name The name of the database: `main`, `temp`, or the name an attached database was attached as
 * @property file The path of its file, or an empty string for a database in memory, or a TEMP one, which has none
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class DatabaseInfo(
    val name: String,
    val file: String,
)

/**
 * A form of an SQL function, a row of `PRAGMA function_list`, which `PRAGMA_FUNCTION_LIST()` returns: a function that
 * takes several numbers of arguments has a row for each.
 *
 * @property name The name of the function
 * @property isBuiltin Whether SQLite has it, rather than the app, the `builtin` of the PRAGMA
 * @property type What it is, in SQLite's words: `s` for a scalar function, `a` for an aggregate one, and `w` for a
 * window one
 * @property encoding The text encoding it takes, as `utf8`, the `enc` of the PRAGMA
 * @property argumentCount The number of arguments it takes, or -1 if it takes any number, the `narg` of the PRAGMA
 * @property flags The flags SQLite registered it with, as `SQLITE_DETERMINISTIC`
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class FunctionInfo(
    val name: String,
    val isBuiltin: Boolean,
    val type: String,
    val encoding: String,
    val argumentCount: Int,
    val flags: Long,
)

/**
 * Whether the database removes its unused pages, the `auto_vacuum` of the database file, which
 * `PRAGMA_AUTO_VACUUM(mode)` sets.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public enum class AutoVacuum {
    /** It keeps them, until a VACUUM removes them, `0` */
    NONE,
    /** It removes them at each commit, `1` */
    FULL,
    /** It keeps them, until the SQL `PRAGMA incremental_vacuum` removes them, `2` */
    INCREMENTAL,
}

/**
 * An index of a table, a row of `PRAGMA index_list`, which `PRAGMA_INDEX_LIST(table)` returns.
 *
 * @property name The name of the index, which SQLite chooses for that of a UNIQUE or PRIMARY KEY constraint, as
 * `sqlite_autoindex_person_1`
 * @property isUnique Whether the index is unique
 * @property origin What created the index
 * @property isPartial Whether the index is a partial index, of the rows that satisfy its WHERE
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class IndexInfo(
    val name: String,
    val isUnique: Boolean,
    val origin: IndexOrigin,
    val isPartial: Boolean,
)

/**
 * What created an index, the `origin` of `PRAGMA index_list`.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public enum class IndexOrigin {
    /** `CREATE INDEX`, `c` */
    CREATE_INDEX,
    /** A UNIQUE constraint, `u` */
    UNIQUE,
    /** A PRIMARY KEY constraint, `pk` */
    PRIMARY_KEY,
}

/**
 * A column of an index, a row of `PRAGMA index_xinfo`, which `PRAGMA_INDEX_XINFO(index)` returns: a column it is keyed
 * by, or one it holds besides, as the rowid of a table's row.
 *
 * @property columnIndex The position of the column in the table, from 0, the `cid` of the PRAGMA: -1 for the rowid,
 * and -2 for an expression
 * @property name The name of the column, or null for the rowid or an expression
 * @property isDescending Whether the index sorts the column in descending order, the `desc` of the PRAGMA
 * @property collation The collation the index compares the column with, as `BINARY` or `NOCASE`, the `coll` of the
 * PRAGMA
 * @property isKey Whether the index is keyed by the column, rather than holding it besides, the `key` of the PRAGMA
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class IndexColumn(
    val columnIndex: Int,
    val name: String?,
    val isDescending: Boolean,
    val collation: String?,
    val isKey: Boolean,
)

/**
 * A column of a foreign key of a table, a row of `PRAGMA foreign_key_list`, which `PRAGMA_FOREIGN_KEY_LIST(table)`
 * returns. A foreign key of several columns has a row for each, with the same [id].
 *
 * @property id The foreign key's number in the table, from 0, which the [ForeignKeyViolation.foreignKeyId] of a
 * violation of it is
 * @property position The position of the column in the foreign key, from 0, the `seq` of the PRAGMA
 * @property parentTable The table the foreign key references, the `table` of the PRAGMA
 * @property column The column of the table, the `from` of the PRAGMA
 * @property parentColumn The column it references, or null if the foreign key names no columns, so that it references
 * the parent table's primary key, the `to` of the PRAGMA
 * @property onUpdate The action of ON UPDATE, as `NO ACTION` or `CASCADE`
 * @property onDelete The action of ON DELETE, as `NO ACTION` or `CASCADE`
 * @property match The MATCH clause, which SQLite parses but ignores, `NONE` if it has none
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class ForeignKeyInfo(
    val id: Int,
    val position: Int,
    val parentTable: String,
    val column: String,
    val parentColumn: String?,
    val onUpdate: String,
    val onDelete: String,
    val match: String,
)

/**
 * A row that violates a foreign key, a row of `PRAGMA foreign_key_check`, which `PRAGMA_FOREIGN_KEY_CHECK()` returns.
 *
 * @property table The table of the row
 * @property rowid The rowid of the row, or null if the table is a WITHOUT ROWID one
 * @property parentTable The table the foreign key references, the `parent` of the PRAGMA
 * @property foreignKeyId The number of the foreign key in the table, the [ForeignKeyInfo.id] of its columns, the
 * `fkid` of the PRAGMA
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class ForeignKeyViolation(
    val table: String,
    val rowid: Long?,
    val parentTable: String,
    val foreignKeyId: Int,
)

/**
 * How `PRAGMA_WAL_CHECKPOINT(mode)` checkpoints the WAL, copying its pages into the database file.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public enum class CheckpointMode {
    /** Copies as many pages as it can without waiting for readers or writers, as SQLite's automatic checkpoints do. */
    PASSIVE,
    /** Waits until no connection writes, and until every reader reads the latest pages, then copies all of them. */
    FULL,
    /** As FULL, then waits until no reader reads the WAL, so that the next writer starts it over. */
    RESTART,
    /** As RESTART, then truncates the WAL file to zero bytes. */
    TRUNCATE,
}

/**
 * The result of a checkpoint, the row of `PRAGMA wal_checkpoint`, which `PRAGMA_WAL_CHECKPOINT(mode)` returns.
 *
 * @property isBusy Whether the checkpoint couldn't finish, as another connection read or wrote, which only a FULL,
 * RESTART or TRUNCATE checkpoint tells, the `busy` of the PRAGMA
 * @property walPages The number of pages in the WAL, or -1 if the database isn't in WAL mode, the `log` of the PRAGMA
 * @property checkpointedPages The number of them copied into the database file, or -1 if the database isn't in WAL
 * mode, the `checkpointed` of the PRAGMA
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public data class CheckpointResult(
    val isBusy: Boolean,
    val walPages: Int,
    val checkpointedPages: Int,
)
