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
import com.ctrip.sqllin.dsl.DBEntity
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.appendDBColumnName
import com.ctrip.sqllin.dsl.sql.statement.*
import kotlinx.serialization.DeserializationStrategy

/**
 * SQL query
 * @author yaqiao
 */

internal object Select : Operation {

    override val sqlStr: String
        get() = "SELECT "

    fun <T : DBEntity<T>> select(
        table: Table<T>,
        clause: WhereClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): WhereSelectStatement<T> =
        WhereSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container)

    fun <T : DBEntity<T>> select(
        table: Table<T>,
        clause: OrderByClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): OrderBySelectStatement<T> =
        OrderBySelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container)

    fun <T : DBEntity<T>> select(
        table: Table<T>,
        clause: LimitClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): LimitSelectStatement<T> =
        LimitSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container)

    fun <T : DBEntity<T>> select(
        table: Table<T>,
        clause: GroupByClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): GroupBySelectStatement<T> =
        GroupBySelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container)

    fun <R : DBEntity<R>> select(
        table: Table<*>,
        clause: NaturalJoinClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ) : JoinSelectStatement<R> =
        JoinSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container)

    fun <R : DBEntity<R>> select(
        table: Table<*>,
        clause: JoinClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
        addSelectStatement: (SelectStatement<R>) -> Unit
    ) : JoinStatementWithoutCondition<R> =
        JoinStatementWithoutCondition(
            buildSQL(table, clause, isDistinct, deserializer),
            deserializer,
            connection,
            container,
            addSelectStatement,
        )

    private fun <T : DBEntity<T>> buildSQL(
        table: Table<*>,
        clause: SelectClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
    ): String = buildString {
        append(sqlStr)
        if (isDistinct)
            append("DISTINCT ")
        appendDBColumnName(deserializer.descriptor)
        append(" FROM ")
        append(table.tableName)
        append(clause.clauseStr)
    }

    fun <T : DBEntity<T>> select(
        table: Table<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): FinalSelectStatement<T> {
        val sql = buildString {
            append(sqlStr)
            if (isDistinct)
                append("DISTINCT ")
            appendDBColumnName(deserializer.descriptor)
            append(" FROM ")
            append(table.tableName)
        }
        return FinalSelectStatement(sql, deserializer, connection, container)
    }
}