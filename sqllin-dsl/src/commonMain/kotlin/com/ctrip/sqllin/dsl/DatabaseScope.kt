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
import com.ctrip.sqllin.dsl.annotation.AdvancedInsertAPI
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.operation.Create
import com.ctrip.sqllin.dsl.sql.operation.Delete
import com.ctrip.sqllin.dsl.sql.operation.Insert
import com.ctrip.sqllin.dsl.sql.operation.Select
import com.ctrip.sqllin.dsl.sql.operation.Update
import com.ctrip.sqllin.dsl.sql.statement.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import kotlin.concurrent.Volatile
import kotlin.jvm.JvmName

/**
 * Scope for executing type-safe SQL DSL statements.
 *
 * DatabaseScope provides extension functions on [Table] objects that enable SQL operations
 * using Kotlin DSL syntax. All SQL statements written within this scope are collected and
 * executed in batch when the scope exits.
 *
 * Supported operations:
 * - **INSERT**: Add entities to tables
 * - **UPDATE**: Modify existing records with SET and WHERE clauses
 * - **DELETE**: Remove records with WHERE clauses
 * - **SELECT**: Query records with WHERE, ORDER BY, LIMIT, GROUP BY, JOIN, and UNION
 * - **CREATE**: Create tables from data class definitions
 *
 * Transaction support:
 * - Use [transaction] to execute multiple statements atomically
 * - Transactions can be nested and are automatically committed or rolled back
 *
 * Example:
 * ```kotlin
 * database {
 *     transaction {
 *         PersonTable INSERT person
 *         PersonTable UPDATE SET { name = "Alice" } WHERE (age GTE 18)
 *     }
 *     val adults = PersonTable SELECT WHERE(age GTE 18) LIMIT 10
 * }
 * ```
 *
 * @author Yuang Qiao
 */
@Suppress("UNCHECKED_CAST")
public class DatabaseScope internal constructor(
    private val databaseConnection: DatabaseConnection,
    private val enableSimpleSQLLog: Boolean,
) {

    // ========== Transaction Management ==========

    @Volatile
    private var transactionStatementsGroup: TransactionStatementsGroup? = null

    private inline val isInTransaction
        get() = transactionStatementsGroup != null

    /**
     * Begins a new transaction.
     *
     * @return `true` if transaction started successfully, `false` if already in a transaction
     */
    public fun beginTransaction(): Boolean {
        if (isInTransaction)
            return false
        transactionStatementsGroup = TransactionStatementsGroup(databaseConnection, enableSimpleSQLLog)
        executiveEngine.addStatement(transactionStatementsGroup!!)
        return true
    }

    /**
     * Ends the current transaction.
     *
     * The transaction will be committed or rolled back based on whether
     * [endTransaction] was called.
     */
    public fun endTransaction() {
        transactionStatementsGroup = null
    }

    /**
     * Executes a block of SQL statements as a single transaction.
     *
     * If the block completes successfully, the transaction is committed.
     * If an exception is thrown, the transaction is rolled back.
     *
     * @param block The block of SQL statements to execute
     * @return The result of the block
     */
    public inline fun <T> transaction(block: DatabaseScope.() -> T): T {
        beginTransaction()
        try {
            return block()
        } finally {
            endTransaction()
        }
    }

    // ========== Statement Execution Management ==========

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

    // ========== INSERT Operations ==========

    /**
     * Inserts multiple entities into the table, allowing the database to auto-generate primary keys.
     *
     * For entities with `Long?` primary keys annotated with [@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey],
     * set the ID property to `null` to let SQLite automatically generate the ID. If you need to insert
     * entities with pre-defined IDs (e.g., during data migration), use [INSERT_WITH_ID] instead.
     *
     * Example:
     * ```kotlin
     * val person = Person(id = null, name = "Alice", age = 25) // ID will be auto-generated
     * PersonTable INSERT listOf(person1, person2)
     * ```
     *
     * @see INSERT_WITH_ID for inserting with pre-defined primary key values
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(entities: Iterable<T>) {
        val statement = Insert.insert(this, databaseConnection, entities)
        addStatement(statement)
    }

    /**
     * Inserts a single entity into the table, allowing the database to auto-generate the primary key.
     *
     * For entities with `Long?` primary keys annotated with [@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey],
     * set the ID property to `null` to let SQLite automatically generate the ID.
     *
     * Example:
     * ```kotlin
     * val person = Person(id = null, name = "Alice", age = 25) // ID will be auto-generated
     * PersonTable INSERT person
     * ```
     *
     * @see INSERT_WITH_ID for inserting with a pre-defined primary key value
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(entity: T): Unit =
        INSERT(listOf(entity))

    /**
     * Inserts multiple entities with pre-defined primary key values (advanced API).
     *
     * **⚠️ This is an advanced API for special use cases like data migration or testing.**
     * Use this function when you need to manually specify the primary key ID instead of letting
     * the database auto-generate it. For normal inserts where the database should generate IDs
     * automatically, use [INSERT] instead.
     *
     * This function is particularly useful for:
     * - Data migration from another database where you need to preserve existing IDs
     * - Testing scenarios where you need predictable, specific ID values
     * - Restoring backup data with original IDs
     *
     * Example:
     * ```kotlin
     * @OptIn(AdvancedInsertAPI::class)
     * fun migrateData() {
     *     val person = Person(id = 12345L, name = "Alice", age = 25) // Use specific ID
     *     PersonTable INSERT_WITH_ID listOf(person1, person2)
     * }
     * ```
     *
     * **Important**: This function requires explicit opt-in via `@OptIn(AdvancedInsertAPI::class)`
     * to acknowledge that you understand the implications of manually specifying primary keys.
     *
     * @see INSERT for standard inserts with auto-generated IDs
     */
    @AdvancedInsertAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_WITH_ID(entities: Iterable<T>) {
        val statement = Insert.insert(this, databaseConnection, entities, true)
        addStatement(statement)
    }

    /**
     * Inserts a single entity with a pre-defined primary key value (advanced API).
     *
     * **⚠️ This is an advanced API for special use cases like data migration or testing.**
     * Use this function when you need to manually specify the primary key ID. For normal inserts,
     * use [INSERT] instead.
     *
     * Example:
     * ```kotlin
     * @OptIn(AdvancedInsertAPI::class)
     * fun migrateData() {
     *     val person = Person(id = 12345L, name = "Alice", age = 25) // Use specific ID
     *     PersonTable INSERT_WITH_ID person
     * }
     * ```
     *
     * @see INSERT for standard inserts with auto-generated IDs
     * @see INSERT_WITH_ID for batch inserts with pre-defined IDs
     */
    @AdvancedInsertAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_WITH_ID(entity: T): Unit =
        INSERT_WITH_ID(listOf(entity))

    // ========== UPDATE Operations ==========

    /**
     * Updates records in the table with SET clause.
     *
     * Can be followed by WHERE to target specific records.
     *
     * Example:
     * ```kotlin
     * PersonTable UPDATE SET { name = "Alice" } WHERE (age GTE 18)
     * ```
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

    // ========== DELETE Operations ==========

    /**
     * Deletes all records from the table.
     *
     * Example:
     * ```kotlin
     * PersonTable DELETE X
     * ```
     */
    @StatementDslMaker
    public infix fun Table<*>.DELETE(x: X) {
        val statement = Delete.deleteAllEntities(this, databaseConnection)
        addStatement(statement)
    }

    /**
     * Deletes records matching the WHERE clause.
     *
     * Example:
     * ```kotlin
     * PersonTable DELETE WHERE(age LT 18)
     * ```
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.DELETE(clause: WhereClause<T>) {
        val statement = Delete.delete(this, databaseConnection, clause)
        addStatement(statement)
    }

    // ========== SELECT Operations ==========

    /**
     * Selects all records from the table.
     *
     * Example:
     * ```kotlin
     * val people = PersonTable SELECT X
     * ```
     */
    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), false)

    /**
     * Selects distinct records from the table.
     */
    @StatementDslMaker
    public inline infix fun <reified T> Table<T>.SELECT_DISTINCT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), true)

    public fun <T> Table<T>.select(serializer: KSerializer<T>, isDistinct: Boolean): FinalSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Selects records matching the WHERE clause.
     *
     * Can be followed by ORDER BY, LIMIT, etc.
     *
     * Example:
     * ```kotlin
     * val adults = PersonTable SELECT WHERE(age GTE 18)
     * ```
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
     * Selects records with ORDER BY clause.
     *
     * Example:
     * ```kotlin
     * val sorted = PersonTable SELECT ORDER_BY(age.DESC())
     * ```
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
     * Selects a limited number of records.
     *
     * Example:
     * ```kotlin
     * val first10 = PersonTable SELECT LIMIT(0, 10)
     * ```
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
     * Selects records with GROUP BY clause.
     *
     * Example:
     * ```kotlin
     * val grouped = PersonTable SELECT GROUP_BY(age)
     * ```
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

    /**
     * Gets the KSerializer for the reified type parameter.
     */
    public inline fun <reified T> getKSerializer(): KSerializer<T> = EmptySerializersModule().serializer()

    // ========== UNION Operations ==========

    private val unionSelectStatementGroupStack by lazy { ArrayDeque<UnionSelectStatementGroup<*>>() }

    private fun getSelectStatementGroup(): StatementContainer = unionSelectStatementGroupStack.lastOrNull() ?: transactionStatementsGroup ?: executiveEngine

    /**
     * Combines multiple SELECT statements with UNION (removes duplicates).
     *
     * Example:
     * ```kotlin
     * val combined = PersonTable.UNION {
     *     it SELECT WHERE(age LT 18)
     *     it SELECT WHERE(age GTE 65)
     * }
     * ```
     */
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

    /**
     * Combines multiple SELECT statements with UNION ALL (keeps duplicates).
     */
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

    /**
     * Begins a UNION statement group (for advanced usage).
     */
    public fun <T> beginUnion() {
        unionSelectStatementGroupStack.add(UnionSelectStatementGroup<T>())
    }

    /**
     * Creates the final UNION select statement from accumulated SELECT statements.
     */
    public fun <T> createUnionSelectStatement(isUnionAll: Boolean): FinalSelectStatement<T> {
        check(unionSelectStatementGroupStack.isNotEmpty()) { "Please invoke the 'beginUnion' before you invoke this function!!!" }
        return (unionSelectStatementGroupStack.last() as UnionSelectStatementGroup<T>).unionStatements(isUnionAll)
    }

    /**
     * Ends the UNION statement group and adds the final statement.
     */
    public fun <T> endUnion(selectStatement: SelectStatement<T>?) {
        unionSelectStatementGroupStack.removeLast()
        selectStatement?.let { addSelectStatement(it) }
    }

    // ========== JOIN Operations ==========

    /**
     * Selects with JOIN clause (requires ON condition).
     *
     * Example:
     * ```kotlin
     * val joined = PersonTable SELECT INNER_JOIN(AddressTable) ON ...
     * ```
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
     * Selects with NATURAL JOIN (joins on columns with same names).
     *
     * Example:
     * ```kotlin
     * val joined = PersonTable SELECT NATURAL_INNER_JOIN(AddressTable)
     * ```
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

    // ========== CREATE Operations ==========

    /**
     * Creates a table from its Table definition.
     *
     * Example:
     * ```kotlin
     * CREATE(PersonTable)
     * // or
     * PersonTable.CREATE()
     * ```
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> CREATE(table: Table<T>) {
        val statement = Create.create(table, databaseConnection)
        addStatement(statement)
    }

    /**
     * Creates this table from its definition (extension function variant).
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("create")
    public fun <T> Table<T>.CREATE(): Unit = CREATE(this)
}