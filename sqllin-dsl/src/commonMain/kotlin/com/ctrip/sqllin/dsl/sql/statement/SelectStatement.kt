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
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.QueryDecoder
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
 * @property parameters Parameterized query values (strings only), or null if none
 *
 * @author Yuang Qiao
 */
public sealed class SelectStatement<T>(
    sqlStr: String,
    internal val deserializer: DeserializationStrategy<T>,
    internal val connection: DatabaseConnection,
    internal val container: StatementContainer,
    final override val parameters: MutableList<String>?,
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

    protected fun buildSQL(clause: SelectClause<T>): String = buildString {
        append(sqlStr)
        append(clause.clauseStr)
    }

    internal fun <R, S> crossJoin(
        table: Table<R>,
        newDeserializer: DeserializationStrategy<S>,
    ): FinalSelectStatement<S> {
        val sql = buildString {
            append(sqlStr)
            append(" CROSS JOIN ")
            append(table.tableName)
        }
        return FinalSelectStatement(sql, newDeserializer, connection, container, parameters)
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToWhere(clause: WhereClause<T>): WhereSelectStatement<T> {
        val clauseParams = clause.selectCondition.parameters
        val params = parameters?.also {
            clauseParams?.let { p ->
                it.addAll(p)
            }
        } ?: clauseParams
        return WhereSelectStatement(buildSQL(clause), deserializer, connection, container, params)
    }

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToGroupBy(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToHaving(clause: HavingClause<T>): HavingSelectStatement<T> {
        val clauseParams = clause.selectCondition.parameters
        val params = parameters?.also {
            clauseParams?.let { p ->
                it.addAll(p)
            }
        } ?: clauseParams
        return HavingSelectStatement(buildSQL(clause), deserializer, connection, container, params)
    }
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToOrderBy(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(clause), deserializer, connection, container, parameters)

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters)
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToLimit(clause: LimitClause<T>): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(clause), deserializer, connection, container, parameters)
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
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters) {

    internal infix fun appendToFinal(clause: OffsetClause<T>): FinalSelectStatement<T> =
        FinalSelectStatement(buildSQL(clause), deserializer, connection, container, parameters)
}

/**
 * Final SELECT statement with all clauses applied.
 *
 * This is the terminal state in the SELECT statement hierarchy - no further clauses can be added.
 * The statement is ready for execution.
 *
 * @author Yuang Qiao
 */
public class FinalSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters)