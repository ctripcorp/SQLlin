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
 * Select statement
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

public class FinalSelectStatement<T> internal constructor(
    sqlStr: String,
    deserializer: DeserializationStrategy<T>,
    connection: DatabaseConnection,
    container: StatementContainer,
    parameters: MutableList<String>?,
) : SelectStatement<T>(sqlStr, deserializer, connection, container, parameters)