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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.CompleteOrderByClause
import com.ctrip.sqllin.dsl.sql.clause.SelectCondition
import com.ctrip.sqllin.dsl.sql.clause.WhereClause
import com.ctrip.sqllin.dsl.sql.operation.parametersOf

/**
 * The parts of an UPDATE or DELETE, from which it is written with the WHERE, ORDER BY, LIMIT and OFFSET it has.
 *
 * SQLite only takes ORDER BY and LIMIT in an UPDATE or DELETE when it is built with `SQLITE_ENABLE_UPDATE_DELETE_LIMIT`,
 * which Android's isn't, nor the libraries SQLlin links on Linux and Windows, and then turns them into a subquery of the
 * rowids of the rows they select, as its `sqlite3LimitWhere()` does. So SQLlin writes that subquery itself, which every
 * SQLite runs, as in
 * `DELETE FROM person WHERE rowid IN (SELECT rowid FROM person WHERE person.age<? ORDER BY person.age ASC LIMIT 10)`:
 * the subquery looks the rows up as INDEXED BY or NOT INDEXED says, and the statement finds each of them by its rowid,
 * as SQLite's own subquery does. An UPDATE or DELETE without LIMIT is written as it is.
 *
 * @property statement The SQL before the table, as `DELETE FROM ` or `UPDATE OR IGNORE `
 * @property target The table, or the table with INDEXED BY or NOT INDEXED, which the rows are looked up through
 * @property assignments The SET of an UPDATE, as ` SET age=?`, or an empty string for a DELETE
 * @property assignmentParameters The parameters of [assignments]
 */
internal data class UpdateDeleteParts(
    val statement: String,
    val table: Table<*>,
    val target: String,
    val assignments: String,
    val assignmentParameters: List<Any?>?,
    val where: SelectCondition? = null,
    val orderBy: CompleteOrderByClause<*>? = null,
    val limit: Int? = null,
    val offset: Int? = null,
) {

    val tableName: String
        get() = table.tableName

    val sql: String = buildString {
        append(statement)
        if (limit == null) {
            append(target)
            append(assignments)
            appendSelection()
        } else {
            val rowId = rowIdName()
            append(table.tableName)
            append(assignments)
            append(" WHERE ")
            append(rowId)
            append(" IN (SELECT ")
            append(rowId)
            append(" FROM ")
            append(target)
            appendSelection()
            append(" LIMIT ")
            append(limit)
            offset?.let {
                append(" OFFSET ")
                append(it)
            }
            append(')')
        }
    }

    /**
     * The parameters of the SET and of the WHERE, in the order of the SQL, in a new list, so that each statement that a
     * clause completes has its own.
     */
    val parameters: MutableList<Any?>?
        get() = parametersOf(assignmentParameters, where?.parameters)

    private fun StringBuilder.appendSelection() {
        where?.let { append(WhereClause<Any?>(it).clauseStr) }
        orderBy?.let { append(it.clauseStr) }
    }

    /**
     * The name the table's rowid can be read by: `rowid`, unless a column has that name, which then means the column,
     * and otherwise `_rowid_` or `oid`, which SQLite names it by too.
     */
    private fun rowIdName(): String {
        val descriptor = table.kSerializer().descriptor
        val columns = (0 ..< descriptor.elementsCount).mapTo(HashSet()) { descriptor.getElementName(it).lowercase() }
        return ROW_ID_NAMES.firstOrNull { it !in columns } ?: throw IllegalArgumentException(
            "Table '${table.tableName}' has columns named rowid, _rowid_ and oid, so its rowid can't be read, which ORDER BY and LIMIT of an UPDATE or DELETE select its rows by."
        )
    }

    private companion object {
        val ROW_ID_NAMES = listOf("rowid", "_rowid_", "oid")
    }
}

/**
 * UPDATE statement without WHERE clause.
 *
 * Represents an UPDATE operation that can either:
 * - Execute immediately (updates all rows in the table)
 * - Be refined by adding a WHERE clause to target specific rows, or ORDER BY and LIMIT to update some of them
 *
 * This intermediate state enables the DSL to support both:
 * ```kotlin
 * UPDATE(user) SET { /* ... */ }  // Updates all rows
 * UPDATE(user) SET { /* ... */ } WHERE { /* ... */ }  // Updates filtered rows
 * ```
 *
 * @param T The entity type being updated
 *
 * @author Yuang Qiao
 */
public class UpdateStatementWithoutWhereClause<T> internal constructor(
    internal val parts: UpdateDeleteParts,
    internal val statementContainer: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(parts.sql) {
    override val parameters: MutableList<Any?>? = parts.parameters
    internal val table: String
        get() = parts.tableName
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * An UPDATE with its WHERE, or a DELETE with its WHERE or of every row, which ORDER BY and LIMIT can follow, so that it
 * only writes the first rows in an order, as in
 * `PersonTable DELETE WHERE(PersonTable.age LT 18) ORDER_BY (PersonTable.age to ASC) LIMIT 10`.
 *
 * @param T The entity type of the table
 *
 * @author Yuang Qiao
 */
public class WhereUpdateDeleteStatement<T> internal constructor(
    internal val parts: UpdateDeleteParts,
    internal val container: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(parts.sql) {
    override val parameters: MutableList<Any?>? = parts.parameters
    internal val table: String
        get() = parts.tableName
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * An UPDATE or DELETE with ORDER BY, which has to be followed by LIMIT, as the order only decides which rows LIMIT
 * chooses: SQLite rejects ORDER BY without LIMIT in an UPDATE or DELETE, so the scope that has it fails before any of
 * its statements runs.
 *
 * @param T The entity type of the table
 *
 * @author Yuang Qiao
 */
public class OrderByUpdateDeleteStatement<T> internal constructor(
    internal val parts: UpdateDeleteParts,
    internal val container: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(parts.sql) {
    override val parameters: MutableList<Any?>? = parts.parameters
    internal val table: String
        get() = parts.tableName

    override fun checkComplete(): Unit =
        throw IllegalArgumentException("An UPDATE or DELETE with ORDER BY needs LIMIT, which chooses the rows in that order: $sqlStr")

    public override fun execute(): Unit = checkComplete()
}

/**
 * An UPDATE or DELETE with LIMIT, which only writes as many rows as it says, the first in the order of its ORDER BY if
 * it has one, and which OFFSET can follow, to skip as many before them.
 *
 * @param T The entity type of the table
 *
 * @author Yuang Qiao
 */
public class LimitUpdateDeleteStatement<T> internal constructor(
    internal val parts: UpdateDeleteParts,
    internal val container: StatementContainer,
    internal val connection: DatabaseConnection,
) : SingleStatement(parts.sql) {
    override val parameters: MutableList<Any?>? = parts.parameters
    internal val table: String
        get() = parts.tableName
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * UPDATE or DELETE statement with all of its clauses (final form), as after OFFSET.
 *
 * @author Yuang Qiao
 */
public class UpdateDeleteStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
    internal val table: String,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * INSERT statement (final form).
 *
 * Represents a complete INSERT operation with entities encoded as parameterized VALUES.
 * Executes as a single batch insert for all entities.
 *
 * @author Yuang Qiao
 */
public class InsertStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
    internal val table: String,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeInsert(sqlStr, params)
}

/**
 * CREATE, DROP, ALTER statement (final form).
 *
 * Represents a complete CREATE TABLE operation. Does not support parameterized queries
 * since DDL statements use direct SQL execution.
 *
 * @author Yuang Qiao
 */
public class TableStructureStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    override fun execute(): Unit = connection.execSQL(sqlStr, params)
    override val parameters: MutableList<Any?>? = null
}