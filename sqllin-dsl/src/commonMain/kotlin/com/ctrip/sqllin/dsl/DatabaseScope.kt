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
import com.ctrip.sqllin.dsl.sql.Relation
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.View
import com.ctrip.sqllin.dsl.sql.ViewDefinition
import com.ctrip.sqllin.dsl.sql.ProjectedX
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.operation.Alter
import com.ctrip.sqllin.dsl.sql.operation.Create
import com.ctrip.sqllin.dsl.sql.operation.Delete
import com.ctrip.sqllin.dsl.sql.operation.Drop
import com.ctrip.sqllin.dsl.sql.operation.Insert
import com.ctrip.sqllin.dsl.sql.operation.PRAGMA
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
 * - **INSERT OR REPLACE**: Insert or replace entities on PRIMARY KEY / UNIQUE conflict
 * - **INSERT OR IGNORE**: Insert entities, skipping those that conflict on PRIMARY KEY / UNIQUE
 * - **UPDATE**: Modify existing records with SET and WHERE clauses
 * - **DELETE**: Remove records with WHERE clauses
 * - **SELECT**: Query records with WHERE, ORDER BY, LIMIT, GROUP BY, JOIN, and UNION, into the table's own row type
 *   or a narrower projection type, such as `PersonTable SELECT WHERE<NameAndAge>(...)`
 * - **CREATE**: Create tables from data class definitions
 * - **DROP**: Remove tables from the database
 * - **ALTER**: Modify table structures (add columns, rename tables/columns, drop columns)
 *
 * Transaction support:
 * - Use [transaction] to execute multiple statements atomically
 * - Transactions can be nested and are automatically committed or rolled back
 *
 * **Execution is deferred**: no statement runs until the scope exits. A [SelectStatement] built
 * inside the scope therefore holds no results while the scope is still open, and calling
 * `getResults()` on it there throws [IllegalStateException]. Hold the statement in a variable
 * declared outside the scope and read its results after the scope has exited, as shown below.
 * For the same reason a query's result cannot inform a write in the same scope: a read-modify-write
 * has to be split into two scopes.
 *
 * Example:
 * ```kotlin
 * // Create and modify table structure
 * database {
 *     CREATE(PersonTable)
 *     PersonTable ALTER_ADD_COLUMN PersonTable.email
 * }
 *
 * // Modify data, and build a query whose results are read once the scope has exited
 * lateinit var adults: SelectStatement<Person>
 * database {
 *     PersonTable { table ->
 *         transaction {
 *             table INSERT person
 *             table UPDATE SET { name = "Alice" } WHERE (age GTE 18)
 *         }
 *         adults = table SELECT WHERE(age GTE 18) LIMIT 10
 *     }
 * }
 * // Every statement above ran when the scope exited, so the results are available only here
 * val results = adults.getResults()
 *
 * // Cleanup
 * database {
 *     PersonTable.DROP()
 * }
 * ```
 *
 * @author Yuang Qiao
 */
@Suppress("UNCHECKED_CAST", "DSL_MARKER_APPLIED_TO_WRONG_TARGET")
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

    /**
     * What the statements of this scope may change when they run.
     */
    internal val changes: Changes
        get() = executiveEngine.changes

    /**
     * The tables the statements of this scope write rows to.
     */
    internal val writtenTables: Set<String>
        get() = executiveEngine.writtenTables

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
     * This only matters for a `Long?` primary key. If the key is always supplied by the caller,
     * declare it as a non-null `Long` instead, and a plain [INSERT] writes it.
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

    /**
     * Inserts multiple entities into the table, replacing any existing rows that conflict on
     * PRIMARY KEY or UNIQUE constraints.
     *
     * When a conflict is detected, the existing row is deleted and the new row is inserted in its
     * place (`INSERT OR REPLACE INTO ...`). When there is no conflict, the behaviour is identical
     * to a plain [INSERT].
     *
     * The primary key column is always included in the VALUES clause so that SQLite can detect
     * conflicts. If the primary key field is `null` for a rowid-backed key, SQLite auto-generates
     * the ID and no conflict can occur by primary key.
     *
     * Example:
     * ```kotlin
     * val person = PersonWithId(id = 42L, name = "Alice Updated", age = 26)
     * PersonWithIdTable INSERT_OR_REPLACE person
     * ```
     *
     * @see INSERT for standard inserts with auto-generated IDs
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(entities: Iterable<T>) {
        val statement = Insert.insertOrReplace(this, databaseConnection, entities)
        addStatement(statement)
    }

    /**
     * Inserts a single entity into the table, replacing any existing row that conflicts on
     * PRIMARY KEY or UNIQUE constraints.
     *
     * Example:
     * ```kotlin
     * val person = PersonWithId(id = 42L, name = "Alice Updated", age = 26)
     * PersonWithIdTable INSERT_OR_REPLACE person
     * ```
     *
     * @see INSERT_OR_REPLACE for batch inserts with conflict replacement
     * @see INSERT for standard inserts with auto-generated IDs
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(entity: T): Unit =
        INSERT_OR_REPLACE(listOf(entity))

    /**
     * Inserts multiple entities into the table, skipping each one that conflicts with an existing
     * row on a PRIMARY KEY or UNIQUE constraint (`INSERT OR IGNORE INTO ...`).
     *
     * Unlike [INSERT_OR_REPLACE], the existing row is left exactly as it is: it isn't deleted and
     * re-inserted, so its other columns keep their values. The entities that don't conflict are
     * inserted as by a plain [INSERT].
     *
     * The primary key column is always included in the VALUES clause so that SQLite can detect
     * conflicts on it. If the primary key field is `null` for a key the database assigns, SQLite
     * generates the ID and no conflict can occur on the primary key.
     *
     * SQLite also skips a row that would violate a NOT NULL constraint, which can't happen for a
     * non-null property. A FOREIGN KEY violation is not ignored and still fails the statement.
     *
     * Example:
     * ```kotlin
     * // Leaves the existing row with ID 42 untouched, and inserts the row with ID 43
     * PersonWithIdTable INSERT_OR_IGNORE listOf(
     *     PersonWithId(id = 42L, name = "Alice", age = 26),
     *     PersonWithId(id = 43L, name = "Bob", age = 31),
     * )
     * ```
     *
     * @see INSERT_OR_REPLACE to replace the conflicting row instead
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(entities: Iterable<T>) {
        val statement = Insert.insertOrIgnore(this, databaseConnection, entities)
        addStatement(statement)
    }

    /**
     * Inserts a single entity into the table, unless it conflicts with an existing row on a
     * PRIMARY KEY or UNIQUE constraint, in which case the existing row is left as it is.
     *
     * Example:
     * ```kotlin
     * PersonWithIdTable INSERT_OR_IGNORE PersonWithId(id = 42L, name = "Alice", age = 26)
     * ```
     *
     * @see INSERT_OR_IGNORE for batch inserts that skip conflicting entities
     * @see INSERT_OR_REPLACE to replace the conflicting row instead
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(entity: T): Unit =
        INSERT_OR_IGNORE(listOf(entity))

    // ========== INSERT INTO ... SELECT ==========
    //
    // These insert the rows a SELECT returns, as in `PersonTable INSERT (PersonV1Table SELECT X<Person>())`. The
    // SELECT's result type is the table's row type, so every column gets a value, the primary key included.

    /**
     * Inserts the rows [select] returns, as the SQL `INSERT INTO table SELECT ...` does.
     *
     * [select] reads rows of this table's type, from any table: a projection or result columns turn the rows of
     * another table into them. It becomes part of this statement, so it no longer runs on its own, and its
     * `getResults` can't be called. The primary key is copied as it is selected.
     *
     * This is how a table is rebuilt, for a change `ALTER TABLE` can't make, such as adding a constraint or
     * changing the primary key, or dropping a column on SQLite older than 3.35:
     * ```kotlin
     * val newPerson = PersonTable.withName("person_new")
     * CREATE(newPerson)
     * newPerson INSERT (PersonV1Table SELECT listOf(PersonV1Table.name AS Person::fullName))
     * DROP(PersonV1Table)
     * "person_new" ALTER_RENAME_TABLE_TO PersonTable
     * ```
     *
     * @throws IllegalArgumentException if [select] is incomplete, as an aggregate query that needs GROUP BY
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(select: SelectStatement<T>): Unit =
        insert("INSERT INTO ", select)

    /**
     * Inserts the rows [select] returns, skipping those that violate a constraint, as the SQL
     * `INSERT OR IGNORE INTO table SELECT ...` does.
     *
     * @see INSERT
     * @see INSERT_OR_IGNORE
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(select: SelectStatement<T>): Unit =
        insert("INSERT OR IGNORE INTO ", select)

    /**
     * Inserts the rows [select] returns, replacing the rows they conflict with, as the SQL
     * `INSERT OR REPLACE INTO table SELECT ...` does.
     *
     * @see INSERT
     * @see INSERT_OR_REPLACE
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(select: SelectStatement<T>): Unit =
        insert("INSERT OR REPLACE INTO ", select)

    private fun <T> Table<T>.insert(insert: String, select: SelectStatement<T>) {
        select.checkComplete()
        select.container removeStatement select
        val statement = Insert.insert(insert, this, databaseConnection, select)
        addStatement(statement)
    }

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
    public inline infix fun <reified T> Relation<T>.SELECT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), false)

    /**
     * Selects distinct records from the table.
     */
    @StatementDslMaker
    public inline infix fun <reified T> Relation<T>.SELECT_DISTINCT(x: X): FinalSelectStatement<T> =
        select(kSerializer(), true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, isDistinct: Boolean): FinalSelectStatement<R> {
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
    public inline infix fun <reified T> Relation<T>.SELECT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Relation<T>.SELECT_DISTINCT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: WhereClause<R>, isDistinct: Boolean): WhereSelectStatement<R> {
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
    public inline infix fun <reified T> Relation<T>.SELECT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Relation<T>.SELECT_DISTINCT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: OrderByClause<R>, isDistinct: Boolean): OrderBySelectStatement<R> {
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
    public inline infix fun <reified T> Relation<T>.SELECT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Relation<T>.SELECT_DISTINCT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: LimitClause<R>, isDistinct: Boolean): LimitSelectStatement<R> {
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
    public inline infix fun <reified T> Relation<T>.SELECT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(kSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <reified T> Relation<T>.SELECT_DISTINCT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(kSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: GroupByClause<R>, isDistinct: Boolean): GroupBySelectStatement<R> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Gets the KSerializer for the reified type parameter.
     */
    public inline fun <reified T> getKSerializer(): KSerializer<T> = EmptySerializersModule().serializer()

    // ========== SELECT with Projection ==========
    //
    // These read rows into a type R other than the table's own row type, given to the clause function, as in
    // `PersonTable SELECT WHERE<NameAndAge>(...)`. R's properties name the columns to select. Each overload taking a
    // clause has the same JVM signature as its counterpart above, hence its @JvmName.

    /**
     * Selects all records, reading each into [R], so that only the columns [R]'s properties name are selected.
     *
     * Example:
     * ```kotlin
     * @Serializable
     * data class NameAndAge(val name: String, val age: Int)
     *
     * val people = PersonTable SELECT X<NameAndAge>()
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(x: ProjectedX<R>): FinalSelectStatement<R> =
        select(getKSerializer<R>(), false)

    /**
     * Selects distinct records, reading each into [R], so that only the columns [R] names are selected and compared.
     *
     * Example:
     * ```kotlin
     * val authors = BookTable SELECT_DISTINCT X<AuthorOnly>()
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(x: ProjectedX<R>): FinalSelectStatement<R> =
        select(getKSerializer<R>(), true)

    /**
     * Selects records matching the WHERE clause, reading each into [R].
     *
     * Example:
     * ```kotlin
     * val adults = PersonTable SELECT WHERE<NameAndAge>(PersonTable.age GTE 18)
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectWhereProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: WhereClause<R>): WhereSelectStatement<R> =
        select(getKSerializer<R>(), clause, false)

    /**
     * Selects distinct records matching the WHERE clause, reading each into [R].
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectDistinctWhereProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: WhereClause<R>): WhereSelectStatement<R> =
        select(getKSerializer<R>(), clause, true)

    /**
     * Selects records in the order of the ORDER BY clause, reading each into [R].
     *
     * Example:
     * ```kotlin
     * val byAge = PersonTable SELECT ORDER_BY<NameAndAge>(PersonTable.age to ASC)
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectOrderByProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: OrderByClause<R>): OrderBySelectStatement<R> =
        select(getKSerializer<R>(), clause, false)

    /**
     * Selects distinct records in the order of the ORDER BY clause, reading each into [R].
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectDistinctOrderByProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: OrderByClause<R>): OrderBySelectStatement<R> =
        select(getKSerializer<R>(), clause, true)

    /**
     * Selects at most as many records as the LIMIT clause allows, reading each into [R].
     *
     * Example:
     * ```kotlin
     * val firstTen = PersonTable SELECT LIMIT<NameAndAge>(10)
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectLimitProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: LimitClause<R>): LimitSelectStatement<R> =
        select(getKSerializer<R>(), clause, false)

    /**
     * Selects at most as many distinct records as the LIMIT clause allows, reading each into [R].
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectDistinctLimitProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: LimitClause<R>): LimitSelectStatement<R> =
        select(getKSerializer<R>(), clause, true)

    /**
     * Selects records grouped by the GROUP BY clause, reading each into [R].
     *
     * Example:
     * ```kotlin
     * val authors = BookTable SELECT GROUP_BY<AuthorOnly>(BookTable.author)
     * ```
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectGroupByProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: GroupByClause<R>): GroupBySelectStatement<R> =
        select(getKSerializer<R>(), clause, false)

    /**
     * Selects distinct records grouped by the GROUP BY clause, reading each into [R].
     *
     * @throws IllegalArgumentException if [R] doesn't fit this table: one of its properties isn't a column,
     * is of a different type from its column, or isn't nullable while its column is
     */
    @JvmName("selectDistinctGroupByProjection")
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: GroupByClause<R>): GroupBySelectStatement<R> =
        select(getKSerializer<R>(), clause, true)

    // ========== SELECT with Result Columns ==========
    //
    // These select expressions, such as aggregate functions, into properties of a result type R with AS, as in
    // `BookTable SELECT listOf(count(X) AS AuthorStats::books)`. Every other property of R is read from its column.

    /**
     * Selects [column] into its property of [R], and every other property of [R] from its column.
     *
     * Example:
     * ```kotlin
     * @Serializable
     * data class BookCount(val books: Long)
     *
     * val total = BookTable SELECT (BookTable.count(X) AS BookCount::books)
     * // SELECT count(*) AS books FROM book
     * ```
     *
     * Can be followed by WHERE, GROUP BY, ORDER BY, or LIMIT.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the query: an expression can be NULL while its property isn't
     * nullable, or a property without an expression doesn't fit its column, as for [X]. A property that can only be
     * non-null in a group of GROUP BY is reported when the scope ends, before anything runs, if no GROUP BY follows.
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(column: ResultColumn<R>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), listOf(column), false)

    /**
     * Selects [column] into its property of [R], and every other property of [R] from its column, returning distinct
     * rows.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the query, as for [SELECT]
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(column: ResultColumn<R>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), listOf(column), true)

    /**
     * Selects each of [columns] into its property of [R], and every other property of [R] from its column.
     *
     * Example:
     * ```kotlin
     * @Serializable
     * data class AuthorStats(val author: String, val books: Long, val totalPages: Long)
     *
     * val stats = BookTable { table ->
     *     table SELECT listOf(count(X) AS AuthorStats::books, sum(pages) AS AuthorStats::totalPages) GROUP_BY author
     * }
     * // SELECT author,count(*) AS books,sum(pages) AS totalPages FROM book GROUP BY author
     * ```
     *
     * Can be followed by WHERE, GROUP BY, ORDER BY, or LIMIT.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the query, as for [SELECT], or [columns] give a property two
     * expressions, or none at all
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT(columns: Iterable<ResultColumn<R>>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), columns, false)

    /**
     * Selects each of [columns] into its property of [R], and every other property of [R] from its column, returning
     * distinct rows.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the query, as for [SELECT]
     */
    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(columns: Iterable<ResultColumn<R>>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), columns, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, columns: Iterable<ResultColumn<R>>, isDistinct: Boolean): ResultColumnSelectStatement<R> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, columns, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    // ========== Compound SELECT ==========
    //
    // These combine two SELECTs of the same result type, as in SQL: `(select1) UNION (select2)`. Each SELECT is put in
    // parentheses, as SELECT and these operators are infix functions of the same precedence. Chained, they are
    // evaluated from left to right, as SQLite does, and parentheses group them: `a UNION (b UNION_ALL c)`.

    /**
     * Combines the rows of this SELECT and [select], without duplicates, as the SQL `select1 UNION select2` does.
     *
     * Example:
     * ```kotlin
     * PersonTable { table ->
     *     (table SELECT WHERE(age LT 18)) UNION (table SELECT WHERE(age GTE 65))
     * }
     * ```
     *
     * Both SELECTs read rows of the same type, which is checked at compile time, from any tables: with a projection,
     * result columns or a join, they can combine rows of different tables. They become part of the compound, so they
     * no longer run on their own. A SELECT that ends with ORDER BY or LIMIT keeps them to itself, and the compound can
     * be followed by its own ORDER BY and LIMIT.
     *
     * @return The compound, which can be followed by another compound operator, ORDER BY or LIMIT
     * @throws IllegalArgumentException if either SELECT is incomplete, as an aggregate query that needs GROUP BY
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <R> SelectStatement<R>.UNION(select: SelectStatement<R>): CompoundSelectStatement<R> =
        compound(" UNION ", select)

    /**
     * Combines the rows of this SELECT and [select], keeping duplicates, as the SQL `select1 UNION ALL select2` does.
     *
     * @see UNION
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <R> SelectStatement<R>.UNION_ALL(select: SelectStatement<R>): CompoundSelectStatement<R> =
        compound(" UNION ALL ", select)

    /**
     * Keeps the rows of this SELECT that [select] also returns, as the SQL `select1 INTERSECT select2` does.
     *
     * @see UNION
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <R> SelectStatement<R>.INTERSECT(select: SelectStatement<R>): CompoundSelectStatement<R> =
        compound(" INTERSECT ", select)

    /**
     * Keeps the rows of this SELECT that [select] doesn't return, as the SQL `select1 EXCEPT select2` does.
     *
     * @see UNION
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <R> SelectStatement<R>.EXCEPT(select: SelectStatement<R>): CompoundSelectStatement<R> =
        compound(" EXCEPT ", select)

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    private fun <R> SelectStatement<R>.compound(operator: String, select: SelectStatement<R>): CompoundSelectStatement<R> {
        checkComplete()
        select.checkComplete()
        container removeStatement this
        select.container removeStatement select
        val statement = Select.compound(this, operator, select, getSelectStatementGroup())
        addSelectStatement(statement)
        return statement
    }

    // ========== UNION Operations (deprecated) ==========

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
     *
     * **Deprecated**: the result is typed by the table's row type, but read into the first SELECT's result type, so
     * a union of projections, result columns or joins fails with a `ClassCastException` when its results are used.
     * A nested block is flattened into the SQL, which SQLite evaluates from left to right, so it changes its meaning
     * when it isn't the first statement. Use the infix [UNION] instead: `(select1) UNION (select2)`.
     */
    @Deprecated("The UNION {} block reads the rows into its first SELECT's result type while returning the table's row type, so a union of projections, result columns or joins fails with a ClassCastException when its results are used, and a nested block is flattened into the SQL, which changes its meaning. " +
            "Use the infix operators instead, as in (select1) UNION (select2), which also offer INTERSECT and EXCEPT. This block will be removed in a future version.")
    @Suppress("DEPRECATION") // Its body calls the plumbing deprecated with it
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
     *
     * **Deprecated** for the reasons given for the `UNION {}` block. Use the infix [UNION_ALL] instead:
     * `(select1) UNION_ALL (select2)`.
     */
    @Deprecated("The UNION {} block reads the rows into its first SELECT's result type while returning the table's row type, so a union of projections, result columns or joins fails with a ClassCastException when its results are used, and a nested block is flattened into the SQL, which changes its meaning. " +
            "Use the infix operators instead, as in (select1) UNION (select2), which also offer INTERSECT and EXCEPT. This block will be removed in a future version.")
    @Suppress("DEPRECATION") // Its body calls the plumbing deprecated with it
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
    @Deprecated("Part of the deprecated UNION {} block, which will be removed in a future version. Use the infix UNION instead.")
    public fun <T> beginUnion() {
        unionSelectStatementGroupStack.add(UnionSelectStatementGroup<T>())
    }

    /**
     * Creates the final UNION select statement from accumulated SELECT statements.
     */
    @Deprecated("Part of the deprecated UNION {} block, which will be removed in a future version. Use the infix UNION instead.")
    public fun <T> createUnionSelectStatement(isUnionAll: Boolean): FinalSelectStatement<T> {
        check(unionSelectStatementGroupStack.isNotEmpty()) { "Please invoke the 'beginUnion' before you invoke this function!!!" }
        return (unionSelectStatementGroupStack.last() as UnionSelectStatementGroup<T>).unionStatements(isUnionAll)
    }

    /**
     * Ends the UNION statement group and adds the final statement.
     */
    @Deprecated("Part of the deprecated UNION {} block, which will be removed in a future version. Use the infix UNION instead.")
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
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: JoinClause<R>, isDistinct: Boolean): JoinStatementWithoutCondition<R> {
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
    public inline infix fun <T, reified R> Relation<T>.SELECT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, false)

    @StatementDslMaker
    public inline infix fun <T, reified R> Relation<T>.SELECT_DISTINCT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, true)

    public fun <T, R> Relation<T>.select(serializer: KSerializer<R>, clause: NaturalJoinClause<R>, isDistinct: Boolean): JoinSelectStatement<R> {
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
    @StatementDslMaker
    public infix fun <T> CREATE(table: Table<T>) {
        val statement = Create.createTable(table, databaseConnection)
        addStatement(statement)
    }

    /**
     * Creates this table from its definition (extension function variant).
     */
    @StatementDslMaker
    @JvmName("create")
    public fun <T> Table<T>.CREATE(): Unit = CREATE(this)

    /**
     * Creates an index on the specified columns of this table.
     *
     * Indexes improve query performance by allowing faster lookups on the indexed columns.
     * However, they also consume additional storage space and may slow down INSERT, UPDATE,
     * and DELETE operations.
     *
     * Example:
     * ```kotlin
     * database {
     *     UserTable.CREATE_INDEX("idx_user_email", UserTable.email)
     *     UserTable.CREATE_INDEX("idx_user_name_age", UserTable.name, UserTable.age)
     * }
     * ```
     *
     * @param indexName The name of the index to create
     * @param columns One or more column elements to include in the index
     * @throws IllegalArgumentException if no columns are specified
     */
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_INDEX(indexName: String, vararg columns: ClauseElement<*>) {
        val statement = Create.createIndex(this, databaseConnection, indexName, *columns)
        addStatement(statement)
    }

    /**
     * Creates a unique index on the specified columns of this table.
     *
     * A unique index enforces uniqueness constraints on the indexed columns, preventing
     * duplicate values. It also improves query performance like a regular index.
     *
     * Example:
     * ```kotlin
     * database {
     *     UserTable.CREATE_UNIQUE_INDEX("idx_unique_email", UserTable.email)
     *     ProductTable.CREATE_UNIQUE_INDEX("idx_unique_sku", ProductTable.sku)
     * }
     * ```
     *
     * @param indexName The name of the unique index to create
     * @param columns One or more column elements to include in the unique index
     * @throws IllegalArgumentException if no columns are specified
     */
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_UNIQUE_INDEX(indexName: String, vararg columns: ClauseElement<*>) {
        val statement = Create.createUniqueIndex(this, databaseConnection, indexName, *columns)
        addStatement(statement)
    }

    // ========== DROP Operations ==========

    /**
     * Drops (removes) a table from the database.
     *
     * **⚠️ WARNING**: This is a destructive operation that permanently deletes
     * the table and all its data. Use with caution.
     *
     * Example:
     * ```kotlin
     * database {
     *     DROP(PersonTable)
     *     // or using extension function
     *     PersonTable.DROP()
     * }
     * ```
     *
     * @param table The table to drop
     */
    @StatementDslMaker
    public infix fun <T> DROP(table: Table<T>) {
        val statement = Drop.drop(table, databaseConnection)
        addStatement(statement)
    }

    /**
     * Drops (removes) this table from the database (extension function variant).
     *
     * **⚠️ WARNING**: This is a destructive operation that permanently deletes
     * the table and all its data. Use with caution.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable.DROP()
     * }
     * ```
     */
    @StatementDslMaker
    @JvmName("drop")
    public fun <T> Table<T>.DROP(): Unit = DROP(this)

    // ========== CREATE VIEW and DROP VIEW ==========

    /**
     * Starts the creation of [view], which `AS` completes with the SELECT that defines it.
     *
     * Example:
     * ```kotlin
     * CREATE_VIEW(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))
     * // CREATE VIEW adults(name,age) AS SELECT name,age FROM person WHERE age>=18
     * ```
     *
     * @return The view to create, which becomes a statement once `AS` gives it its SELECT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> CREATE_VIEW(view: View<T>): ViewDefinition<T> = ViewDefinition(view)

    /**
     * Creates the view, defined by [select], as the SQL `CREATE VIEW view AS SELECT ...` does.
     *
     * [select] reads rows of the view's row type, which is checked at compile time, from any tables, through a
     * projection, result columns, a join or a compound SELECT. It becomes part of this statement, so it no longer runs
     * on its own. As SQLite doesn't let a view take parameters, its values are written into the view's SQL.
     *
     * @throws IllegalArgumentException if [select] is incomplete, as an aggregate query that needs GROUP BY
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> ViewDefinition<T>.AS(select: SelectStatement<T>) {
        select.checkComplete()
        select.container removeStatement select
        addStatement(Create.createView(view, select, databaseConnection))
    }

    /**
     * Drops [view], as the SQL `DROP VIEW view` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> DROP(view: View<T>) {
        addStatement(Drop.dropView(view, databaseConnection))
    }

    /**
     * Drops this view (extension function variant).
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("drop")
    public fun <T> View<T>.DROP(): Unit = DROP(this)

    // ========== ALTER Operations ==========

    /**
     * Adds a new column to an existing table.
     *
     * This operation modifies the table structure by adding a new column definition.
     * Note: SQLite has limitations on ALTER TABLE - some operations like adding columns
     * with constraints may require table recreation.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable ALTER_ADD_COLUMN email
     * }
     * ```
     *
     * @param column The column definition to add to the table
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.ALTER_ADD_COLUMN(column: ClauseElement<*>) {
        val statement = Alter.addColumn(this, column, databaseConnection)
        addStatement(statement)
    }

    /**
     * Renames this table to a new name.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable ALTER_RENAME_TABLE_TO NewPersonTable
     * }
     * ```
     *
     * @param newTable The new table definition containing the target name
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.ALTER_RENAME_TABLE_TO(newTable: Table<*>) {
        val statement = Alter.renameTable(tableName, newTable, databaseConnection)
        addStatement(statement)
    }

    /**
     * Renames a table from an old name (as String) to a new table definition.
     *
     * This variant is useful when you don't have a Table object for the old table name.
     *
     * Example:
     * ```kotlin
     * database {
     *     "old_person" ALTER_RENAME_TABLE_TO NewPersonTable
     * }
     * ```
     *
     * @receiver The current name of the table to rename
     * @param newTable The new table definition containing the target name
     */
    @StatementDslMaker
    public infix fun String.ALTER_RENAME_TABLE_TO(newTable: Table<*>) {
        val statement = Alter.renameTable(this, newTable, databaseConnection)
        addStatement(statement)
    }

    /**
     * Renames a column within this table using ClauseElement references.
     *
     * This variant allows you to use strongly-typed column references for both
     * the old and new column names.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable.RENAME_COLUMN(PersonTable.age, PersonTable.yearsOld)
     * }
     * ```
     *
     * @param oldColumn The current column to rename
     * @param newColumn The new column definition with the target name
     */
    @StatementDslMaker
    public fun <T, R : ClauseElement<*>> Table<T>.RENAME_COLUMN(oldColumn: R, newColumn: R) {
        val statement = Alter.renameColumn(this, oldColumn.valueName, newColumn, databaseConnection)
        addStatement(statement)
    }

    /**
     * Renames a column within this table using a String for the old column name.
     *
     * This variant is useful when you don't have a ClauseElement reference for
     * the old column name.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable.RENAME_COLUMN("age", PersonTable.yearsOld)
     * }
     * ```
     *
     * @param oldColumnName The current name of the column to rename
     * @param newColumn The new column definition with the target name
     */
    @StatementDslMaker
    public fun <T> Table<T>.RENAME_COLUMN(oldColumnName: String, newColumn: ClauseElement<*>) {
        val statement = Alter.renameColumn(this, oldColumnName, newColumn, databaseConnection)
        addStatement(statement)
    }

    /**
     * Removes a column from this table.
     *
     * **⚠️ WARNING**: This permanently deletes the column and all its data.
     * Note: SQLite has limited support for DROP COLUMN (added in version 3.35.0).
     * Older SQLite versions may require table recreation to drop columns.
     *
     * Example:
     * ```kotlin
     * database {
     *     PersonTable DROP_COLUMN PersonTable.email
     * }
     * ```
     *
     * @param column The column to remove from the table
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.DROP_COLUMN(column: ClauseElement<*>) {
        val statement = Alter.dropColumn(this, column, databaseConnection)
        addStatement(statement)
    }

    /**
     * Enables or disables foreign key constraint enforcement in SQLite.
     *
     * **⚠️ IMPORTANT**: By default, SQLite **does not enforce** foreign key constraints.
     * You must explicitly enable them using this function before foreign key constraints
     * will take effect. This setting is per-connection and must be set each time you
     * open a database connection.
     *
     * ### When to Use
     * - Call this **before** creating tables with foreign key constraints
     * - Call this at the **beginning** of each database session if you want foreign key enforcement
     * - Set to `false` if you need to temporarily disable constraints (e.g., during bulk operations)
     *
     * ### Example
     * ```kotlin
     * database {
     *     // Enable foreign key enforcement
     *     PRAGMA_FOREIGN_KEYS(true)
     *
     *     // Now foreign key constraints will be enforced
     *     CREATE(OrderTable)  // Table with foreign key to UserTable
     *     OrderTable INSERT Order(userId = 999)  // Will fail if user 999 doesn't exist
     * }
     * ```
     *
     * ### SQLite Behavior
     * - When enabled (`true`): SQLite enforces all foreign key constraints
     *   - INSERT/UPDATE operations that violate constraints will fail
     *   - DELETE operations trigger ON DELETE actions (CASCADE, SET NULL, etc.)
     * - When disabled (`false`): Foreign key constraints are ignored
     *   - Constraints are still part of the schema but not enforced
     *   - Useful for data migration or bulk operations
     *
     * @param flag `true` to enable foreign key enforcement, `false` to disable
     *
     * @see ForeignKeyGroup
     * @see ForeignKey
     * @see References
     */
    @StatementDslMaker
    public infix fun PRAGMA_FOREIGN_KEYS(flag: Boolean) {
        val statement = PRAGMA.foreignKeys(flag, databaseConnection)
        addStatement(statement)
    }
}