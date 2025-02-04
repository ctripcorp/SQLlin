/*
 * Copyright (C) 2023 Ctrip.com.
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

package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.operation.Delete
import com.ctrip.sqllin.dsl.sql.operation.Insert
import com.ctrip.sqllin.dsl.sql.operation.Select
import com.ctrip.sqllin.dsl.sql.operation.Update
import com.ctrip.sqllin.dsl.sql.statement.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import kotlin.concurrent.Volatile

/**
 * The database scope, it's used to restrict the scope that write DSL SQL statements
 * @author yaqiao
 */

@Suppress("UNCHECKED_CAST")
public class DatabaseScope internal constructor(
    private val databaseConnection: DatabaseConnection,
    private val enableSimpleSQLLog: Boolean,
) {

    /**
     * Transaction.
     */

    @Volatile
    private var transactionStatementsGroup: TransactionStatementsGroup? = null

    private inline val isInTransaction
        get() = transactionStatementsGroup != null

    public fun beginTransaction(): Boolean {
        if (isInTransaction)
            return false
        transactionStatementsGroup = TransactionStatementsGroup(databaseConnection, enableSimpleSQLLog)
        executiveEngine.addStatement(transactionStatementsGroup!!)
        return true
    }

    public fun endTransaction() {
        transactionStatementsGroup = null
    }

    public inline fun <T> transaction(block: DatabaseScope.() -> T): T {
        beginTransaction()
        try {
            return block()
        } finally {
            endTransaction()
        }
    }

    /**
     * SQL execute.
     */

    private val executiveEngine = DatabaseExecuteEngine(enableSimpleSQLLog)

    private fun addStatement(statement: SingleStatement) {
        if (isInTransaction)
            transactionStatementsGroup!!.addStatement(statement)
        else
            executiveEngine.addStatement(statement)
    }

    private fun <T> addSelectStatement(statement: SelectStatement<T>) {
        if (unionSelectStatementGroupStack.isNotEmpty())
            (unionSelectStatementGroupStack.last() as UnionSelectStatementGroup<T>).addSelectStatement(statement)
        else
            addStatement(statement)
    }

    internal fun executeAllStatements() = executiveEngine.executeAllStatement()

    /**
     * Insert.
     */

    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(entities: Iterable<T>) {
        val statement = Insert.insert(this, databaseConnection, entities)
        addStatement(statement)
    }

    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(entity: T): Unit =
        INSERT(listOf(entity))

    /**
     * Update.
     */

    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        transactionStatementsGroup?.let {
            val statement = Update.update(this, databaseConnection, it, clause)
            it addStatement statement
            statement
        } ?: Update.update(this, databaseConnection, executiveEngine, clause).also {
            executiveEngine addStatement it
        }

    /**
     * Delete.
     */

    @StatementDslMaker
    public infix fun Table<*>.DELETE(x: X) {
        val statement = Delete.deleteAllEntities(this, databaseConnection)
        addStatement(statement)
    }

    @StatementDslMaker
    public infix fun <T> Table<T>.DELETE(clause: WhereClause<T>) {
        val statement = Delete.delete(this, databaseConnection, clause)
        addStatement(statement)
    }

    /**
     * Select.
     */

    /**
     * Select with no any clause.
     */

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), false)

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), true)

    @StatementDslMaker
    public fun <T> Table<T>.select(serializer: KSerializer<T>, isDistinct: Boolean): FinalSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'WHERE' clause.
     */

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T> Table<T>.select(serializer: KSerializer<T>, clause: WhereClause<T>, isDistinct: Boolean): WhereSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'ORDER BY' clause.
     */

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T> Table<T>.select(serializer: KSerializer<T>, clause: OrderByClause<T>, isDistinct: Boolean): OrderBySelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'LIMIT' clause.
     */

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T> Table<T>.select(serializer: KSerializer<T>, clause: LimitClause<T>, isDistinct: Boolean): LimitSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'GROUP BY' clause.
     */

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T> Table<T>.select(serializer: KSerializer<T>, clause: GroupByClause<T>, isDistinct: Boolean): GroupBySelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    public inline fun <reified T> getKSerializer(): KSerializer<T> = EmptySerializersModule().serializer()

    /**
     * The 'UNION' clause of Select.
     */

    private val unionSelectStatementGroupStack by lazy { ArrayDeque<UnionSelectStatementGroup<*>>() }

    private fun getSelectStatementGroup(): StatementContainer = unionSelectStatementGroupStack.lastOrNull() ?: transactionStatementsGroup ?: executiveEngine

    public inline fun <T> Table<T>.UNION(block: Table<T>.(Table<T>) -> Unit): FinalSelectStatement<T> {
        beginUnion<T>()
        var selectStatement: SelectStatement<T>? = null
        try {
            block(this)
            selectStatement = createUnionSelectStatement(false)
            return selectStatement
        } finally {
            endUnion(selectStatement)
        }
    }

    @StatementDslMaker
    public inline fun <T> Table<T>.UNION_ALL(block: Table<T>.(Table<T>) -> Unit): FinalSelectStatement<T> {
        beginUnion<T>()
        var selectStatement: SelectStatement<T>? = null
        try {
            block(this)
            selectStatement = createUnionSelectStatement(true)
            return selectStatement
        } finally {
            endUnion(selectStatement)
        }
    }

    public fun <T> beginUnion() {
        unionSelectStatementGroupStack.add(UnionSelectStatementGroup<T>())
    }

    public fun <T> createUnionSelectStatement(isUnionAll: Boolean): FinalSelectStatement<T> {
        check(unionSelectStatementGroupStack.isNotEmpty()) { "Please invoke the 'beginUnion' before you invoke this function!!!" }
        return (unionSelectStatementGroupStack.last() as UnionSelectStatementGroup<T>).unionStatements(isUnionAll)
    }

    public fun <T> endUnion(selectStatement: SelectStatement<T>?) {
        unionSelectStatementGroupStack.removeLast()
        selectStatement?.let { addSelectStatement(it) }
    }

    /**
     * Receive the 'JOIN' clause.
     */

    @StatementDslMaker
    public inline infix fun <T, reified R> Table<T>.SELECT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <T, reified R> Table<T>.SELECT_DISTINCT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, true)

    public fun <T, R> Table<T>.select(serializer: KSerializer<R>, clause: JoinClause<R>, isDistinct: Boolean): JoinStatementWithoutCondition<R> {
        val container = getSelectStatementGroup()
        return Select.select(this, clause, isDistinct, serializer, databaseConnection, container, ::addSelectStatement)
    }

    /**
     * Receive the natural join clause(includes 'NATURAL LEFT OUTER JOIN' and 'NATURAL INNER JOIN').
     */

    @StatementDslMaker
    public inline infix fun <T, reified R> Table<T>.SELECT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <T, reified R> Table<T>.SELECT_DISTINCT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, true)

    public fun <T, R> Table<T>.select(serializer: KSerializer<R>, clause: NaturalJoinClause<R>, isDistinct: Boolean): JoinSelectStatement<R> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }
}