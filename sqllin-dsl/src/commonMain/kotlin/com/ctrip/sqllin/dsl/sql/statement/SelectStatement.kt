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

import com.ctrip.sqllin.driver.CommonCursor
import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.clause.elementTables
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.QueryDecoder
import com.ctrip.sqllin.dsl.sql.operation.parametersOf
import kotlinx.serialization.DeserializationStrategy
import kotlin.concurrent.Volatile

/**
 * Base class for SELECT statements with progressive clause building.
 *
 * Represents a SELECT query that can be executed to retrieve and deserialize entities from the database.
 * The class hierarchy enforces SQL clause ordering at compile time - each subclass accepts only valid
 * subsequent clauses (e.g., WHERE can be followed by GROUP BY, ORDER BY, or LIMIT).
 *
 * Results are lazily evaluated and cached after [execute] is called. Use [getResults] to retrieve
 * the deserialized entities.
 *
 * @param T The entity type returned by this query
 * @property deserializer kotlinx.serialization strategy for decoding cursor rows to entities
 * @property connection Database connection for executing the query
 * @property container Statement container for managing this statement in the DSL scope
 * @property parameters Parameterized query values, or null if none
 * @property ungroupedError The error to report if no GROUP BY is appended, or null if there is none. It is set for an
 * aggregate query whose result type has non-null properties that can only be relied on in a group: without GROUP BY,
 * an aggregate query returns one row even when no rows match, in which they are NULL. GROUP BY clears it, and
 * [checkComplete] reports it.
 * @property tables The names of the tables the statement reads, which an observed query watches for changes
 *
 * @author Yuang Qiao
 */
public sealed class SelectStatement<T>(
    sqlStr: String,
    internal val deserializer: DeserializationStrategy<T>,
    internal val connection: DatabaseConnection,
    internal val container: StatementContainer,
    final override val parameters: MutableList<Any?>?,
    internal val ungroupedError: String?,
    internal val tables: Set<String>,
) : SingleStatement(sqlStr) {

    @Volatile
    private var result: List<T>? = null

    @Volatile
    private var cursor: CommonCursor? = null

    final override fun execute() {
        cursor = connection.query(sqlStr, params)
    }

    /**
     * Retrieves the query results as a list of deserialized entities.
     *
     * Results are lazily computed on first call and cached for subsequent calls.
     * Throws [IllegalStateException] if called before [execute].
     *
     * @return List of entities matching the query
     */
    public fun getResults(): List<T> = result ?: cursor?.use {
        val decoder = QueryDecoder(it)
        result = buildList {
            it.forEachRow {
                add(decoder.decodeSerializableValue(deserializer))
            }
        }
        result!!
    } ?: throw IllegalStateException("You have to call 'execute' function before call 'getResults'!!!")

    /**
     * Checks what can only be checked once no clause can be appended anymore: that a statement that needs GROUP BY has
     * it. Called for every statement of a scope before any of them runs, so that a failing check runs nothing.
     *
     * @throws IllegalArgumentException if the statement isn't complete
     */
    internal fun checkComplete() {
        ungroupedError?.let { throw IllegalArgumentException(it) }
    }

    protected fun buildSQL(clause: SelectClause<T>): String = buildString {
        append(sqlStr)
        append(clause.clauseStr)
    }
}

/**
 * SELECT statement with WHERE clause applied.
 *
 * Can be followed by:
 * - GROUP BY
 * - ORDER BY
 * - LIMIT
 *
 * @author Yuang Qiao
 */
public class WhereSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables + clause.elementTables)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, null, tables + clause.elementTables)
}

/**
 * SELECT statement with result columns, as `table SELECT listOf(count(X) AS AuthorStats::books)` starts.
 *
 * Can be followed by:
 * - WHERE
 * - GROUP BY
 * - ORDER BY
 * - LIMIT
 *
 * @author Yuang Qiao
 */
public class ResultColumnSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToWhere(clause: WhereClause<T>): WhereSelectStatement<T> =
        WhereSelectStatement(buildSQL(clause), deserializer, connection, container, parametersOf(parameters, clause.selectCondition.parameters), ungroupedError, tables + clause.selectCondition.tables)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables + clause.elementTables)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, null, tables + clause.elementTables)
}

/**
 * SELECT statement with JOIN clause applied.
 *
 * Can be followed by:
 * - WHERE
 * - GROUP BY
 * - ORDER BY
 * - LIMIT
 *
 * @author Yuang Qiao
 */
public class JoinSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToWhere(clause: WhereClause<T>): WhereSelectStatement<T> =
        WhereSelectStatement(buildSQL(clause), deserializer, connection, container, parametersOf(parameters, clause.selectCondition.parameters), ungroupedError, tables + clause.selectCondition.tables)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables + clause.elementTables)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, null, tables + clause.elementTables)
}

/**
 * SELECT statement with GROUP BY clause applied.
 *
 * Can be followed by:
 * - HAVING
 * - ORDER BY
 *
 * @author Yuang Qiao
 */
public class GroupBySelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables + clause.elementTables)

    internal infix fun appendToHaving(clause: HavingClause<T>): HavingSelectStatement<T> =
        HavingSelectStatement(buildSQL(clause), deserializer, connection, container, parametersOf(parameters, clause.selectCondition.parameters), ungroupedError, tables + clause.selectCondition.tables)
}

/**
 * SELECT statement with HAVING clause applied.
 *
 * Can be followed by:
 * - ORDER BY
 * - LIMIT
 *
 * @author Yuang Qiao
 */
public class HavingSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables + clause.elementTables)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables)
}

/**
 * SELECT statement with ORDER BY clause applied.
 *
 * Can be followed by:
 * - LIMIT
 *
 * @author Yuang Qiao
 */
public class OrderBySelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables)
}

/**
 * SELECT statement with LIMIT clause applied.
 *
 * Can be followed by:
 * - OFFSET
 *
 * @author Yuang Qiao
 */
public class LimitSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables) {

    internal infix fun appendToFinal(clause: OffsetClause<T>): FinalSelectStatement<T> =
        FinalSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, ungroupedError, tables, isSimple = false)
}

/**
 * Final SELECT statement with all clauses applied.
 *
 * This is the terminal state in the SELECT statement hierarchy - no further clauses can be added.
 * The statement is ready for execution.
 *
 * @property isSimple Whether the statement is a single SELECT without LIMIT, as `SELECT X` builds, rather than one
 * with OFFSET, or a compound built by the deprecated `UNION {}` block
 *
 * @author Yuang Qiao
 */
public class FinalSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    ungroupedError: String?,
    tables: Set<String>,
    internal val isSimple: Boolean,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, ungroupedError, tables)

/**
 * Compound SELECT statement: SELECTs combined with UNION, UNION ALL, INTERSECT or EXCEPT, as
 * `(select1) UNION (select2)` builds.
 *
 * Can be followed by:
 * - UNION, UNION ALL, INTERSECT or EXCEPT, with another SELECT
 * - ORDER BY
 * - LIMIT
 *
 * ORDER BY and LIMIT apply to the whole compound, as in SQL.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class CompoundSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<Any?>?,
    tables: Set<String>,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters, null, tables) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters, null, tables + clause.elementTables)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters, null, tables)
}