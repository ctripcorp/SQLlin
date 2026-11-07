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
import com.ctrip.sqllin.driver.TransactionMode
import com.ctrip.sqllin.dsl.annotation.AdvancedInsertAPI
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker
import com.ctrip.sqllin.dsl.annotation.PlatformDependentSQLiteAPI
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.DefaultValues
import com.ctrip.sqllin.dsl.sql.DerivedTable
import com.ctrip.sqllin.dsl.sql.From
import com.ctrip.sqllin.dsl.sql.FtsTable
import com.ctrip.sqllin.dsl.sql.ProjectedX
import com.ctrip.sqllin.dsl.sql.RaiseIgnore
import com.ctrip.sqllin.dsl.sql.RaiseResolution
import com.ctrip.sqllin.dsl.sql.IndexedTable
import com.ctrip.sqllin.dsl.sql.Relation
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.TriggerDefinition
import com.ctrip.sqllin.dsl.sql.TriggerEvent
import com.ctrip.sqllin.dsl.sql.TriggerName
import com.ctrip.sqllin.dsl.sql.TriggerTiming
import com.ctrip.sqllin.dsl.sql.View
import com.ctrip.sqllin.dsl.sql.ViewDefinition
import com.ctrip.sqllin.dsl.sql.X
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.inlineParameters
import com.ctrip.sqllin.dsl.sql.compiler.sqlLiteral
import com.ctrip.sqllin.dsl.sql.operation.Alter
import com.ctrip.sqllin.dsl.sql.operation.Create
import com.ctrip.sqllin.dsl.sql.operation.Delete
import com.ctrip.sqllin.dsl.sql.operation.Drop
import com.ctrip.sqllin.dsl.sql.operation.Insert
import com.ctrip.sqllin.dsl.sql.operation.Maintenance
import com.ctrip.sqllin.dsl.sql.operation.PRAGMA
import com.ctrip.sqllin.dsl.sql.operation.Select
import com.ctrip.sqllin.dsl.sql.operation.Trigger
import com.ctrip.sqllin.dsl.sql.operation.Update
import com.ctrip.sqllin.dsl.sql.operation.Vacuum
import com.ctrip.sqllin.dsl.sql.pragma.*
import com.ctrip.sqllin.dsl.sql.statement.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.reflect.KProperty1

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
 * **A scope isn't thread-safe**: use it only in the code that runs its block, and don't call its functions from
 * threads or coroutines that run at the same time, as coroutines launched in the block of `suspendedScope` on a
 * dispatcher of several threads would. A block that suspends may resume on another thread, which is safe, as the steps
 * of a coroutine run one after another.
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

    private var transactionStatementsGroup: TransactionStatementsGroup? = null

    private inline val isInTransaction
        get() = transactionStatementsGroup != null

    /**
     * How deeply the transactions being built are nested: 1 in the outermost one, and 0 outside any. Only the outermost
     * one collects statements, and only its end ends it, so that the statements after an inner one are still in it.
     */
    private var transactionDepth = 0

    /**
     * Begins a new transaction, which begins IMMEDIATE, unless one has begun already, whose statements the new ones
     * join. Each call has to be followed by one of [endTransaction], as [transaction] does.
     *
     * @return `true` if transaction started successfully, `false` if already in a transaction
     */
    public fun beginTransaction(): Boolean = beginTransaction(TransactionMode.IMMEDIATE)

    /**
     * Begins a new transaction, which begins in [mode], unless one has begun already, whose statements the new ones join.
     *
     * @return `true` if transaction started successfully, `false` if already in a transaction
     */
    @PublishedApi
    internal fun beginTransaction(mode: TransactionMode): Boolean {
        check(triggerBody == null) { "A transaction can't begin in the body of a trigger, which runs in the statement that fires it." }
        if (transactionDepth++ > 0)
            return false
        transactionStatementsGroup = TransactionStatementsGroup(databaseConnection, enableSimpleSQLLog, mode)
        executiveEngine.addStatement(transactionStatementsGroup!!)
        return true
    }

    /**
     * Ends the transaction that the last [beginTransaction] began, so that the statements after it aren't in it. A
     * transaction in another one only ends itself: its statements are the outer one's, and so are those after it,
     * until the outer one ends.
     *
     * The statements run, and the transaction commits, or rolls back if one of them fails, once the scope ends.
     */
    public fun endTransaction() {
        if (transactionDepth > 0 && --transactionDepth == 0)
            transactionStatementsGroup = null
    }

    /**
     * Executes a block of SQL statements as a single transaction.
     *
     * If the block completes successfully, the transaction is committed.
     * If an exception is thrown, the transaction is rolled back.
     *
     * A transaction in another one joins it: its statements, and those after it, are the outer one's, which commits or
     * rolls back all of them.
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

    /**
     * Executes a block of SQL statements as a single transaction, which begins in [mode], as SQL's `BEGIN DEFERRED`,
     * `BEGIN IMMEDIATE` or `BEGIN EXCLUSIVE` does, which decides when it takes the write lock. [transaction] without a
     * mode begins IMMEDIATE.
     *
     * Example:
     * ```kotlin
     * transaction(TransactionMode.EXCLUSIVE) {
     *     PersonTable INSERT person
     * }
     * ```
     *
     * A transaction in another one joins it, whose mode it keeps. Android's framework can't begin a DEFERRED transaction
     * that writes, so there it begins an IMMEDIATE one.
     *
     * @param mode How the transaction begins
     * @param block The block of SQL statements to execute
     * @return The result of the block
     */
    @ExperimentalDSLDatabaseAPI
    public inline fun <T> transaction(mode: TransactionMode, block: DatabaseScope.() -> T): T {
        beginTransaction(mode)
        try {
            return block()
        } finally {
            endTransaction()
        }
    }

    // ========== Statement Execution Management ==========

    private val executiveEngine = DatabaseExecuteEngine(enableSimpleSQLLog)

    private fun addStatement(statement: SingleStatement) {
        val body = triggerBody
        when {
            body != null -> body.addStatement(statement)
            isInTransaction -> transactionStatementsGroup!!.addStatement(statement)
            else -> executiveEngine.addStatement(statement)
        }
    }

    /**
     * The container the statements are added to: the body of the trigger being defined, the transaction, or the scope.
     */
    private val currentContainer: StatementContainer
        get() = triggerBody ?: transactionStatementsGroup ?: executiveEngine

    private fun <T> addSelectStatement(statement: SelectStatement<T>) = addStatement(statement)

    internal fun executeAllStatements() {
        check(definingTrigger == null) {
            "The trigger '${definingTrigger?.timing?.trigger?.name}' has no BEGIN with its statements, so it would never be created."
        }
        executiveEngine.executeAllStatement()
    }

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

    /**
     * The tables and views the SELECTs of this scope read.
     */
    internal val readTables: Set<String>
        get() = executiveEngine.readTables

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
        val statement = Insert.insertOr("INSERT OR REPLACE INTO ", this, databaseConnection, entities)
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
        val statement = Insert.insertOr("INSERT OR IGNORE INTO ", this, databaseConnection, entities)
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

    // ========== INSERT of expressions ==========

    /**
     * Inserts a row of expressions, as SQL's `INSERT INTO table(columns) VALUES (expressions)` does, each given to a
     * property of the row type with `AS`, which checks its type at compile time:
     * ```kotlin
     * // INSERT INTO audit(action,at) VALUES ('purge',datetime('now'))
     * AuditTable INSERT listOf(literal("purge") AS Audit::action, AuditTable.datetime("now") AS Audit::at)
     * ```
     * The columns left out take their default values, or NULL, which a NOT NULL column without a default rejects when
     * the statement runs. An expression that can be NULL can only be written to a nullable column, and the values can't
     * read any table, but the rows of a trigger: use `INSERT` with a SELECT to insert the values of other rows.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertExpressions")
    public infix fun <T> Table<T>.INSERT(values: Iterable<ResultColumn<T>>) {
        addStatement(Insert.insert("INSERT INTO ", this, databaseConnection, values))
    }

    /**
     * Inserts a row of a single expression, as `INSERT(listOf(value))` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(value: ResultColumn<T>): Unit = INSERT(listOf(value))

    /**
     * Inserts a row of expressions, or nothing if it conflicts with a row, as SQL's `INSERT OR IGNORE` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertOrIgnoreExpressions")
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(values: Iterable<ResultColumn<T>>) {
        addStatement(Insert.insert("INSERT OR IGNORE INTO ", this, databaseConnection, values))
    }

    /**
     * Inserts a row of a single expression, or nothing if it conflicts with a row.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(value: ResultColumn<T>): Unit = INSERT_OR_IGNORE(listOf(value))

    /**
     * Inserts a row of expressions, replacing the rows it conflicts with, as SQL's `INSERT OR REPLACE` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertOrReplaceExpressions")
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(values: Iterable<ResultColumn<T>>) {
        addStatement(Insert.insert("INSERT OR REPLACE INTO ", this, databaseConnection, values))
    }

    /**
     * Inserts a row of a single expression, replacing the rows it conflicts with.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(value: ResultColumn<T>): Unit = INSERT_OR_REPLACE(listOf(value))

    /**
     * `DEFAULT VALUES`, which `table INSERT DEFAULT_VALUES` inserts a row of.
     */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val DEFAULT_VALUES: DefaultValues
        get() = DefaultValues()

    /**
     * Inserts a row of the default values of all columns, as SQL's `INSERT INTO table DEFAULT VALUES` does:
     * `table INSERT DEFAULT_VALUES`. A column without a default value takes NULL, which a NOT NULL one rejects when the
     * statement runs, and a database-assigned primary key its next value.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT(defaultValues: DefaultValues) {
        addStatement(Insert.insertDefaultValues("INSERT INTO ", this, databaseConnection))
    }

    /**
     * Inserts a row of the default values of all columns, or nothing if it conflicts with a row, as SQL's
     * `INSERT OR IGNORE INTO table DEFAULT VALUES` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_IGNORE(defaultValues: DefaultValues) {
        addStatement(Insert.insertDefaultValues("INSERT OR IGNORE INTO ", this, databaseConnection))
    }

    /**
     * Inserts a row of the default values of all columns, replacing the rows it conflicts with, as SQL's
     * `INSERT OR REPLACE INTO table DEFAULT VALUES` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_REPLACE(defaultValues: DefaultValues) {
        addStatement(Insert.insertDefaultValues("INSERT OR REPLACE INTO ", this, databaseConnection))
    }

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

    // ========== INSERT OR ABORT, OR FAIL and OR ROLLBACK ==========
    //
    // Like INSERT_OR_IGNORE and INSERT_OR_REPLACE, they write the primary key even when the database assigns it, so that
    // a conflict on it is seen; a null key still lets the database assign one.

    /**
     * Inserts [entities], and fails the statement on a conflict with a constraint, undoing what it changed, as SQL's `INSERT OR ABORT` does:
     * what a plain [INSERT] does, but in place of the conflict clause of a constraint, such as
     * `@Unique(onConflict = OnConflict.IGNORE)`.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ABORT(entities: Iterable<T>): Unit =
        addStatement(Insert.insertOr("INSERT OR ABORT INTO ", this, databaseConnection, entities))

    /**
     * Inserts [entity], and fails on a conflict, undoing the statement, as `INSERT_OR_ABORT(listOf(entity))` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ABORT(entity: T): Unit = INSERT_OR_ABORT(listOf(entity))

    /**
     * Inserts a row of expressions, and fails on a conflict, undoing the statement, as SQL's `INSERT OR ABORT` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertOrAbortExpressions")
    public infix fun <T> Table<T>.INSERT_OR_ABORT(values: Iterable<ResultColumn<T>>): Unit =
        addStatement(Insert.insert("INSERT OR ABORT INTO ", this, databaseConnection, values))

    /**
     * Inserts a row of a single expression, and fails on a conflict, undoing the statement.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ABORT(value: ResultColumn<T>): Unit = INSERT_OR_ABORT(listOf(value))

    /**
     * Inserts a row of the default values of all columns, and fails on a conflict, undoing the statement, as SQL's
     * `INSERT OR ABORT INTO table DEFAULT VALUES` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ABORT(defaultValues: DefaultValues): Unit =
        addStatement(Insert.insertDefaultValues("INSERT OR ABORT INTO ", this, databaseConnection))

    /**
     * Inserts the rows [select] returns, and fails on a conflict, undoing the statement, as SQL's `INSERT OR ABORT INTO table SELECT ...` does.
     *
     * @see INSERT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ABORT(select: SelectStatement<T>): Unit =
        insert("INSERT OR ABORT INTO ", select)

    /**
     * Inserts [entities], and fails the statement on a conflict with a constraint, keeping the rows it inserted before the one that
     * conflicts, as SQL's `INSERT OR FAIL` does, in place of the conflict clause of the constraint.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_FAIL(entities: Iterable<T>): Unit =
        addStatement(Insert.insertOr("INSERT OR FAIL INTO ", this, databaseConnection, entities))

    /**
     * Inserts [entity], and fails on a conflict, keeping the rows inserted before, as `INSERT_OR_FAIL(listOf(entity))` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_FAIL(entity: T): Unit = INSERT_OR_FAIL(listOf(entity))

    /**
     * Inserts a row of expressions, and fails on a conflict, keeping the rows inserted before, as SQL's `INSERT OR FAIL` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertOrFailExpressions")
    public infix fun <T> Table<T>.INSERT_OR_FAIL(values: Iterable<ResultColumn<T>>): Unit =
        addStatement(Insert.insert("INSERT OR FAIL INTO ", this, databaseConnection, values))

    /**
     * Inserts a row of a single expression, and fails on a conflict, keeping the rows inserted before.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_FAIL(value: ResultColumn<T>): Unit = INSERT_OR_FAIL(listOf(value))

    /**
     * Inserts a row of the default values of all columns, and fails on a conflict, keeping the rows inserted before, as SQL's
     * `INSERT OR FAIL INTO table DEFAULT VALUES` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_FAIL(defaultValues: DefaultValues): Unit =
        addStatement(Insert.insertDefaultValues("INSERT OR FAIL INTO ", this, databaseConnection))

    /**
     * Inserts the rows [select] returns, and fails on a conflict, keeping the rows inserted before, as SQL's `INSERT OR FAIL INTO table SELECT ...` does.
     *
     * @see INSERT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_FAIL(select: SelectStatement<T>): Unit =
        insert("INSERT OR FAIL INTO ", select)

    /**
     * Inserts [entities], and rolls back the transaction it runs in on a conflict with a constraint, as SQL's `INSERT OR ROLLBACK` does,
     * in place of the conflict clause of the constraint; outside a transaction, it fails as [INSERT_OR_ABORT] does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(entities: Iterable<T>): Unit =
        addStatement(Insert.insertOr("INSERT OR ROLLBACK INTO ", this, databaseConnection, entities))

    /**
     * Inserts [entity], and rolls back the transaction on a conflict, as `INSERT_OR_ROLLBACK(listOf(entity))` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(entity: T): Unit = INSERT_OR_ROLLBACK(listOf(entity))

    /**
     * Inserts a row of expressions, and rolls back the transaction on a conflict, as SQL's `INSERT OR ROLLBACK` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("insertOrRollbackExpressions")
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(values: Iterable<ResultColumn<T>>): Unit =
        addStatement(Insert.insert("INSERT OR ROLLBACK INTO ", this, databaseConnection, values))

    /**
     * Inserts a row of a single expression, and rolls back the transaction on a conflict.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(value: ResultColumn<T>): Unit = INSERT_OR_ROLLBACK(listOf(value))

    /**
     * Inserts a row of the default values of all columns, and rolls back the transaction on a conflict, as SQL's
     * `INSERT OR ROLLBACK INTO table DEFAULT VALUES` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(defaultValues: DefaultValues): Unit =
        addStatement(Insert.insertDefaultValues("INSERT OR ROLLBACK INTO ", this, databaseConnection))

    /**
     * Inserts the rows [select] returns, and rolls back the transaction on a conflict, as SQL's `INSERT OR ROLLBACK INTO table SELECT ...` does.
     *
     * @see INSERT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.INSERT_OR_ROLLBACK(select: SelectStatement<T>): Unit =
        insert("INSERT OR ROLLBACK INTO ", select)

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
     * Can be followed by WHERE to target specific records, and by ORDER BY and LIMIT, to update the first of them in
     * an order.
     *
     * Example:
     * ```kotlin
     * PersonTable UPDATE SET { name = "Alice" } WHERE (age GTE 18)
     * PersonTable UPDATE SET { name = "Alice" } WHERE (age GTE 18) ORDER_BY (age to ASC) LIMIT 1
     * ```
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, and fails the statement on a conflict with a constraint, undoing what it changed: what a plain [UPDATE] does, but in
     * place of the conflict clause of a constraint, such as `@Unique(onConflict = OnConflict.IGNORE)`, as SQL's `UPDATE OR ABORT` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE_OR_ABORT(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause, "UPDATE OR ABORT ").also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, and fails the statement on a conflict with a constraint, keeping the rows it updated before the one that conflicts, as SQL's `UPDATE OR FAIL` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE_OR_FAIL(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause, "UPDATE OR FAIL ").also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, and leaves each row whose new values would conflict with a constraint as it was, and goes on, as SQL's `UPDATE OR IGNORE` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE_OR_IGNORE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause, "UPDATE OR IGNORE ").also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, and deletes the rows that a row's new values conflict with on a PRIMARY KEY or UNIQUE constraint, writes the
     * default value of a NOT NULL column it would set to NULL, and fails on a CHECK constraint, as SQL's `UPDATE OR REPLACE` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE_OR_REPLACE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause, "UPDATE OR REPLACE ").also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, and rolls back the transaction it runs in on a conflict with a constraint, and outside a transaction fails as
     * [UPDATE_OR_ABORT] does, as SQL's `UPDATE OR ROLLBACK` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.UPDATE_OR_ROLLBACK(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(this, databaseConnection, currentContainer, clause, "UPDATE OR ROLLBACK ").also { addStatement(it) }

    // ========== DELETE Operations ==========

    /**
     * Deletes all records from the table, or the first in an order, which ORDER BY and LIMIT that follow choose.
     *
     * Example:
     * ```kotlin
     * PersonTable DELETE X
     * PersonTable DELETE X ORDER_BY (PersonTable.age to ASC) LIMIT 10
     * ```
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.DELETE(x: X): WhereUpdateDeleteStatement<T> =
        Delete.deleteAllEntities(this, databaseConnection, currentContainer).also { addStatement(it) }

    /**
     * Deletes records matching the WHERE clause, or the first of them in an order, which ORDER BY and LIMIT that follow
     * choose.
     *
     * Example:
     * ```kotlin
     * PersonTable DELETE WHERE(age LT 18)
     * PersonTable DELETE WHERE(age LT 18) ORDER_BY (age to ASC) LIMIT 10
     * ```
     */
    @StatementDslMaker
    public infix fun <T> Table<T>.DELETE(clause: WhereClause<T>): WhereUpdateDeleteStatement<T> =
        Delete.delete(this, databaseConnection, currentContainer, clause).also { addStatement(it) }

    // ========== UPDATE and DELETE of a table with INDEXED BY or NOT INDEXED ==========

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says, as SQL's `UPDATE table INDEXED BY index` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, target = fromSQL).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE_OR_ABORT] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE_OR_ABORT(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, "UPDATE OR ABORT ", fromSQL).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE_OR_FAIL] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE_OR_FAIL(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, "UPDATE OR FAIL ", fromSQL).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE_OR_IGNORE] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE_OR_IGNORE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, "UPDATE OR IGNORE ", fromSQL).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE_OR_REPLACE] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE_OR_REPLACE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, "UPDATE OR REPLACE ", fromSQL).also { addStatement(it) }

    /**
     * Updates the rows that WHERE selects with [clause], as [UPDATE_OR_ROLLBACK] does, looking them up as `INDEXED BY` or
     * `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.UPDATE_OR_ROLLBACK(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        Update.update(table, databaseConnection, currentContainer, clause, "UPDATE OR ROLLBACK ", fromSQL).also { addStatement(it) }

    /**
     * Deletes all rows of the table, looking them up as `INDEXED BY` or `NOT INDEXED` says.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.DELETE(x: X): WhereUpdateDeleteStatement<T> =
        Delete.deleteAllEntities(table, databaseConnection, currentContainer, fromSQL).also { addStatement(it) }

    /**
     * Deletes the rows that [clause] selects, looking them up as `INDEXED BY` or `NOT INDEXED` says, as SQL's
     * `DELETE FROM table INDEXED BY index WHERE ...` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> IndexedTable<T>.DELETE(clause: WhereClause<T>): WhereUpdateDeleteStatement<T> =
        Delete.delete(table, databaseConnection, currentContainer, clause, fromSQL).also { addStatement(it) }

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
        val container = currentContainer
        val statement = Select.select(From.of(this), isDistinct, serializer, databaseConnection, container)
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
        val container = currentContainer
        val statement = Select.select(From.of(this), clause, isDistinct, serializer, databaseConnection, container)
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
        val container = currentContainer
        val statement = Select.select(From.of(this), clause, isDistinct, serializer, databaseConnection, container)
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
        val container = currentContainer
        val statement = Select.select(From.of(this), clause, isDistinct, serializer, databaseConnection, container)
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
        val container = currentContainer
        val statement = Select.select(From.of(this), clause, isDistinct, serializer, databaseConnection, container)
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
     * // SELECT author,count(*) AS books,sum(book.pages) AS totalPages FROM book GROUP BY book.author
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
        val container = currentContainer
        val statement = Select.select(From.of(this), columns, isDistinct, serializer, databaseConnection, container)
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
        val statement = Select.compound(this, operator, select, currentContainer)
        addSelectStatement(statement)
        return statement
    }

    // ========== Subqueries ==========
    //
    // A SELECT can be a derived table, in the FROM clause or a JOIN of another query, or the subquery of an IN or
    // EXISTS condition. It becomes part of that query, so it no longer runs on its own.

    /**
     * Makes this SELECT a derived table named after [view], which a query reads like a table: `(SELECT ...) AS name`.
     *
     * The derived table has [view]'s rows, which the SELECT has to return, as is checked at compile time, and [view]'s
     * column properties name its columns. [view] is the object generated for a class annotated with `@DBView`, which
     * only declares the rows and their name here: no such view has to exist in the database.
     *
     * Example:
     * ```kotlin
     * @DBView("author_books")
     * @Serializable
     * data class AuthorBooks(val authorId: Long, val books: Long)
     *
     * val authorBooks = BookTable { table ->
     *     table SELECT listOf(count(X) AS AuthorBooks::books) GROUP_BY authorId
     * } AS AuthorBooksView
     * (FROM(PersonTable) INNER_JOIN authorBooks ON (PersonTable.id EQ AuthorBooksView.authorId)) SELECT X<Author>()
     * // SELECT ... FROM person INNER JOIN (SELECT authorId,count(*) AS books FROM book GROUP BY book.authorId) AS author_books
     * //     ON person.id=author_books.authorId
     * ```
     *
     * As each derived table is named after its view, a query can't read two derived tables of the same view. A
     * derived table of a table's rows gives that table another name, which a query joining the table with itself
     * needs: `(PersonTable SELECT X) AS ManagerView`.
     *
     * @return The derived table, which can be selected from and joined like a table or a view, but not written to
     * @throws IllegalArgumentException if this SELECT is incomplete, as an aggregate query that needs GROUP BY
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <R> SelectStatement<R>.AS(view: View<R>): Relation<R> {
        takeOut()
        return DerivedTable(view, this)
    }

    /**
     * Whether this number is among the values the single column of [select] returns: `column IN (SELECT ...)`.
     *
     * Example:
     * ```kotlin
     * @Serializable
     * data class AuthorId(val authorId: Long)
     *
     * PersonTable SELECT WHERE(PersonTable.id IN (BookTable SELECT X<AuthorId>()))
     * // SELECT ... FROM person WHERE person.id IN (SELECT authorId FROM book)
     * ```
     *
     * @throws IllegalArgumentException if [select] doesn't return one column of numbers, or is incomplete
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun ClauseNumber<*>.IN(select: SelectStatement<*>): SelectCondition =
        subqueryIn(this, select, "numbers") { it.serialName.removeSuffix("?") in NUMBER_TYPES }

    /**
     * Whether this text is among the values the single column of [select] returns: `column IN (SELECT ...)`.
     *
     * @throws IllegalArgumentException if [select] doesn't return one column of text, or is incomplete
     * @see IN
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun ClauseString<*>.IN(select: SelectStatement<*>): SelectCondition =
        subqueryIn(this, select, "text") { it.serialName.removeSuffix("?") in TEXT_TYPES }

    /**
     * Whether this BLOB is among the values the single column of [select] returns: `column IN (SELECT ...)`.
     *
     * @throws IllegalArgumentException if [select] doesn't return one column of BLOBs, or is incomplete
     * @see IN
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun ClauseBlob.IN(select: SelectStatement<*>): SelectCondition =
        subqueryIn(this, select, "BLOBs") { it.serialName.removeSuffix("?") == BYTE_ARRAY_TYPE }

    /**
     * Whether [select] returns any row: `EXISTS (SELECT ...)`.
     *
     * The SELECT can refer to the columns of the query it is in, as columns are written with their tables' names,
     * which makes it run for each of that query's rows:
     * ```kotlin
     * PersonTable SELECT WHERE(EXISTS(BookTable SELECT WHERE(BookTable.authorId EQ PersonTable.id)))
     * // SELECT ... FROM person WHERE EXISTS (SELECT ... FROM book WHERE book.authorId=person.id)
     * ```
     * Inside the SELECT, a column of a table that both queries read is the SELECT's own, as both have the same name,
     * so it can't refer to the other query's rows of that table. `NOT(EXISTS(select))` checks that it returns no rows.
     *
     * @throws IllegalArgumentException if [select] is incomplete, as an aggregate query that needs GROUP BY
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun EXISTS(select: SelectStatement<*>): SelectCondition {
        select.takeOut()
        return SelectCondition("EXISTS (${select.sqlStr})", select.parameters?.toMutableList(), select.tables)
    }

    // A SELECT of a single column can also be a value, a scalar subquery: `select[R::property]` names the column, so
    // that the value has the property's type.

    /**
     * This SELECT as a number, a scalar subquery: the value of its single column, [property], in its first row, or NULL
     * if it returns no rows, as in `price GT (BookTable SELECT listOf(avg(price) AS AveragePrice::value))[AveragePrice::value]`.
     * It can be NULL, and the values of the SELECT are written into the SQL, as the values of any expression are.
     *
     * @throws IllegalArgumentException if the SELECT returns more than one column
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun <R, V : Number> SelectStatement<R>.get(property: KProperty1<R, V?>): ClauseNumber<V> =
        scalar(this, property) { sql, tables -> ClauseNumber(sql, ExpressionRelation, isFunction = true, isNullable = true, isAggregate = false, isNullOnNoRows = true, columnTables = emptySet(), tables = tables) }

    /**
     * This SELECT as a string, a scalar subquery: the value of its single column, [property], in its first row, or NULL
     * if it returns no rows.
     *
     * @throws IllegalArgumentException if the SELECT returns more than one column
     */
    @ExperimentalDSLDatabaseAPI
    @JvmName("getString")
    public operator fun <R> SelectStatement<R>.get(property: KProperty1<R, String?>): ClauseString<String> =
        scalar(this, property) { sql, tables -> ClauseString(sql, ExpressionRelation, isFunction = true, isNullable = true, isAggregate = false, isNullOnNoRows = true, columnTables = emptySet(), tables = tables) }

    /**
     * This SELECT as a Boolean, a scalar subquery: the value of its single column, [property], in its first row, or NULL
     * if it returns no rows.
     *
     * @throws IllegalArgumentException if the SELECT returns more than one column
     */
    @ExperimentalDSLDatabaseAPI
    @JvmName("getBoolean")
    public operator fun <R> SelectStatement<R>.get(property: KProperty1<R, Boolean?>): ClauseBoolean =
        scalar(this, property) { sql, tables -> ClauseBoolean(sql, ExpressionRelation, isFunction = true, isNullable = true, isAggregate = false, isNullOnNoRows = true, columnTables = emptySet(), tables = tables) }

    /**
     * This SELECT as a BLOB, a scalar subquery: the value of its single column, [property], in its first row, or NULL if
     * it returns no rows.
     *
     * @throws IllegalArgumentException if the SELECT returns more than one column
     */
    @ExperimentalDSLDatabaseAPI
    @JvmName("getBlob")
    public operator fun <R> SelectStatement<R>.get(property: KProperty1<R, ByteArray?>): ClauseBlob =
        scalar(this, property) { sql, tables -> ClauseBlob(sql, ExpressionRelation, isFunction = true, isNullable = true, isAggregate = false, isNullOnNoRows = true, columnTables = emptySet(), tables = tables) }

    /**
     * This SELECT as an enum entry, a scalar subquery: the value of its single column, [property], in its first row, or
     * NULL if it returns no rows.
     *
     * @throws IllegalArgumentException if the SELECT returns more than one column
     */
    @ExperimentalDSLDatabaseAPI
    @JvmName("getEnum")
    public operator fun <R, T : Enum<T>> SelectStatement<R>.get(property: KProperty1<R, T?>): ClauseEnum<T> =
        scalar(this, property) { sql, tables -> ClauseEnum(sql, ExpressionRelation, isFunction = true, isNullable = true, isAggregate = false, isNullOnNoRows = true, columnTables = emptySet(), tables = tables) }

    @OptIn(ExperimentalSerializationApi::class)
    private fun <E : ClauseElement<*>> scalar(select: SelectStatement<*>, property: KProperty1<*, *>, element: (String, Set<String>) -> E): E {
        val descriptor = select.deserializer.descriptor
        require(descriptor.elementsCount == 1 && descriptor.getElementName(0) == property.name) {
            "A SELECT of '${descriptor.serialName}' can't be the value '${property.name}': a scalar subquery returns a single column, so its result type has to have that property only."
        }
        select.takeOut()
        return element("(${inlineParameters(select.sqlStr, select.parameters)})", select.tables)
    }

    /**
     * Takes this SELECT out of the statements the scope runs, as it becomes part of another one: a subquery, or the
     * SELECT that `EXPLAIN QUERY PLAN` plans.
     *
     * @throws IllegalArgumentException if this SELECT is incomplete, as an aggregate query that needs GROUP BY
     */
    private fun SelectStatement<*>.takeOut() {
        checkComplete()
        container removeStatement this
    }

    /**
     * Returns the condition that [element] is among the values of the single column of [select], checking that the
     * column [holds] values [element] can be compared with, which are [values].
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun subqueryIn(element: ClauseElement<*>, select: SelectStatement<*>, values: String, holds: (SerialDescriptor) -> Boolean): SelectCondition {
        val descriptor = select.deserializer.descriptor
        val prefix = "Can't find '${element.valueName}' IN the results of a SELECT of '${descriptor.serialName}'"
        require(descriptor.elementsCount == 1) {
            "$prefix: it returns ${descriptor.elementsCount} columns, while IN compares with one. Select a type with a single property."
        }
        val column = descriptor.getElementDescriptor(0)
        require(holds(column)) {
            "$prefix: its column '${descriptor.getElementName(0)}' holds ${column.serialName.removeSuffix("?")}, while '${element.valueName}' holds $values."
        }
        select.takeOut()
        val sql = buildString {
            element.appendSQL(this)
            append(" IN (")
            append(select.sqlStr)
            append(')')
        }
        return SelectCondition(sql, select.parameters?.toMutableList(), select.tables)
    }

    private companion object {
        val NUMBER_TYPES = setOf(
            "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long", "kotlin.Float", "kotlin.Double",
            "kotlin.UByte", "kotlin.UShort", "kotlin.UInt", "kotlin.ULong",
        )
        val TEXT_TYPES = setOf("kotlin.String", "kotlin.Char")
        const val BYTE_ARRAY_TYPE = "kotlin.ByteArray"
    }

    // ========== SELECT from Joins ==========
    //
    // A join of relations, such as `PersonTable INNER_JOIN BookTable ON (...)`, is selected from like a table, but has
    // to be given the type its rows are read into, as rows of a join have no type of their own.

    /**
     * Selects rows of this join into [R], with X<R>(), checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(x: ProjectedX<R>): FinalSelectStatement<R> =
        select(getKSerializer<R>(), isDistinct = false)

    /**
     * Selects rows of this join into [R], with X<R>(), returning distinct rows, checking [R] against the joined
     * relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(x: ProjectedX<R>): FinalSelectStatement<R> =
        select(getKSerializer<R>(), isDistinct = true)

    /**
     * Selects rows of this join into [R], with WHERE<R>(...), checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(clause: WhereClause<R>): WhereSelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = false)

    /**
     * Selects rows of this join into [R], with WHERE<R>(...), returning distinct rows, checking [R] against the joined
     * relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(clause: WhereClause<R>): WhereSelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = true)

    /**
     * Selects rows of this join into [R], with ORDER_BY<R>(...), checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(clause: OrderByClause<R>): OrderBySelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = false)

    /**
     * Selects rows of this join into [R], with ORDER_BY<R>(...), returning distinct rows, checking [R] against the
     * joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(clause: OrderByClause<R>): OrderBySelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = true)

    /**
     * Selects rows of this join into [R], with LIMIT<R>(...), checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(clause: LimitClause<R>): LimitSelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = false)

    /**
     * Selects rows of this join into [R], with LIMIT<R>(...), returning distinct rows, checking [R] against the joined
     * relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(clause: LimitClause<R>): LimitSelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = true)

    /**
     * Selects rows of this join into [R], with GROUP_BY<R>(...), checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(clause: GroupByClause<R>): GroupBySelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = false)

    /**
     * Selects rows of this join into [R], with GROUP_BY<R>(...), returning distinct rows, checking [R] against the
     * joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(clause: GroupByClause<R>): GroupBySelectStatement<R> =
        select(getKSerializer<R>(), clause, isDistinct = true)

    /**
     * Selects rows of this join into [R], with a result column, checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(column: ResultColumn<R>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), listOf(column), isDistinct = false)

    /**
     * Selects rows of this join into [R], with a result column, returning distinct rows, checking [R] against the
     * joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(column: ResultColumn<R>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), listOf(column), isDistinct = true)

    /**
     * Selects rows of this join into [R], with result columns, checking [R] against the joined relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT(columns: Iterable<ResultColumn<R>>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), columns, isDistinct = false)

    /**
     * Selects rows of this join into [R], with result columns, returning distinct rows, checking [R] against the joined
     * relations.
     *
     * @throws IllegalArgumentException if [R] doesn't fit the join
     * @see JoinedRelation
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public inline infix fun <reified R> JoinedRelation.SELECT_DISTINCT(columns: Iterable<ResultColumn<R>>): ResultColumnSelectStatement<R> =
        select(getKSerializer<R>(), columns, isDistinct = true)

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, isDistinct: Boolean): FinalSelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, clause: WhereClause<R>, isDistinct: Boolean): WhereSelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, clause: OrderByClause<R>, isDistinct: Boolean): OrderBySelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, clause: LimitClause<R>, isDistinct: Boolean): LimitSelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, clause: GroupByClause<R>, isDistinct: Boolean): GroupBySelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    @ExperimentalDSLDatabaseAPI
    public fun <R> JoinedRelation.select(serializer: KSerializer<R>, columns: Iterable<ResultColumn<R>>, isDistinct: Boolean): ResultColumnSelectStatement<R> {
        val container = currentContainer
        val statement = Select.select(from, columns, isDistinct, serializer, databaseConnection, container)
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
     * Creates [table] unless a table of its name exists, as the SQL `CREATE TABLE IF NOT EXISTS` does, so that a
     * migration step can run more than once. An FTS table is created with `CREATE VIRTUAL TABLE IF NOT EXISTS`.
     *
     * The existing table isn't checked against [table]'s structure: if it differs, it stays as it is.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> CREATE_IF_NOT_EXISTS(table: Table<T>) {
        addStatement(Create.createTable(table, databaseConnection, isIfNotExists = true))
    }

    /**
     * Creates this table unless a table of its name exists.
     *
     * @see CREATE_IF_NOT_EXISTS
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("createIfNotExists")
    public fun <T> Table<T>.CREATE_IF_NOT_EXISTS(): Unit = CREATE_IF_NOT_EXISTS(this)

    /**
     * Creates an index on the specified columns of this table.
     *
     * Indexes improve query performance by allowing faster lookups on the indexed columns.
     * However, they also consume additional storage space and may slow down INSERT, UPDATE,
     * and DELETE operations.
     *
     * A function of the table's columns makes an expression index, which the queries comparing the same expression
     * use, as `lower(email)` for case-insensitive lookups. The COLLATE operator gives a column of the index another
     * collation than the column's, as `email COLLATE NOCASE`, which the queries comparing `(email COLLATE NOCASE)`
     * use. `WHERE` after it makes a partial index of the rows that satisfy a condition.
     *
     * Example:
     * ```kotlin
     * database {
     *     UserTable.CREATE_INDEX("idx_user_email", UserTable.email)
     *     UserTable.CREATE_INDEX("idx_user_name_age", UserTable.name, UserTable.age)
     *     UserTable.CREATE_INDEX("idx_user_lower_email", UserTable.lower(UserTable.email))
     *     UserTable.CREATE_INDEX("idx_user_email_nocase", UserTable.email COLLATE NOCASE)
     * }
     * ```
     *
     * @param indexName The name of the index to create
     * @param columns One or more columns, or functions of them, to include in the index
     * @throws IllegalArgumentException if no columns are specified, or one reads another table
     */
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_INDEX(indexName: String, vararg columns: ClauseElement<*>): IndexStatement =
        indexStatement(indexName, Create.createIndex(this, databaseConnection, indexName, *columns))

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
     *     UserTable.CREATE_UNIQUE_INDEX("idx_unique_lower_email", UserTable.lower(UserTable.email))
     * }
     * ```
     *
     * A function of the table's columns makes the values of that expression unique, as `lower(email)` does for emails
     * that differ only in case. `WHERE` after it makes the columns unique among the rows that satisfy a condition only,
     * as `CREATE_UNIQUE_INDEX("idx_user_email", UserTable.email) WHERE (UserTable.isDeleted IS false)` does for the
     * users that aren't deleted.
     *
     * @param indexName The name of the unique index to create
     * @param columns One or more columns, or functions of them, to include in the unique index
     * @throws IllegalArgumentException if no columns are specified, or one reads another table
     */
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_UNIQUE_INDEX(indexName: String, vararg columns: ClauseElement<*>): IndexStatement =
        indexStatement(indexName, Create.createUniqueIndex(this, databaseConnection, indexName, *columns))

    /**
     * Creates an index on [columns] of this table unless an index named [indexName] exists, as the SQL
     * `CREATE INDEX IF NOT EXISTS` does.
     *
     * @see CREATE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_INDEX_IF_NOT_EXISTS(indexName: String, vararg columns: ClauseElement<*>): IndexStatement =
        indexStatement(indexName, Create.createIndex(this, databaseConnection, indexName, *columns, isIfNotExists = true))

    /**
     * Creates a unique index on [columns] of this table unless an index named [indexName] exists, as the SQL
     * `CREATE UNIQUE INDEX IF NOT EXISTS` does.
     *
     * @see CREATE_UNIQUE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_UNIQUE_INDEX_IF_NOT_EXISTS(indexName: String, vararg columns: ClauseElement<*>): IndexStatement =
        indexStatement(indexName, Create.createUniqueIndex(this, databaseConnection, indexName, *columns, isIfNotExists = true))

    /**
     * Creates an index on [column] and [columns] of this table, each sorted in the order given, as the SQL
     * `CREATE INDEX name ON table(column DESC)` does: `CREATE_INDEX("idx_person_age", PersonTable.age to DESC)`.
     * A query that sorts in that order, or the reverse, reads the index in order.
     *
     * @see CREATE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_INDEX(indexName: String, column: Pair<ClauseElement<*>, OrderByWay>, vararg columns: Pair<ClauseElement<*>, OrderByWay>): IndexStatement =
        indexStatement(indexName, Create.createIndex(this, databaseConnection, indexName, listOf(column) + columns))

    /**
     * Creates a unique index on [column] and [columns] of this table, each sorted in the order given, as the SQL
     * `CREATE UNIQUE INDEX name ON table(column DESC)` does: `CREATE_UNIQUE_INDEX("idx_person_age", PersonTable.age to DESC)`.
     * A query that sorts in that order, or the reverse, reads the index in order.
     *
     * @see CREATE_UNIQUE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_UNIQUE_INDEX(indexName: String, column: Pair<ClauseElement<*>, OrderByWay>, vararg columns: Pair<ClauseElement<*>, OrderByWay>): IndexStatement =
        indexStatement(indexName, Create.createUniqueIndex(this, databaseConnection, indexName, listOf(column) + columns))

    /**
     * Creates an index, unless one of that name exists, on [column] and [columns] of this table, each sorted in the order given, as the SQL
     * `CREATE INDEX name ON table(column DESC)` does: `CREATE_INDEX_IF_NOT_EXISTS("idx_person_age", PersonTable.age to DESC)`.
     * A query that sorts in that order, or the reverse, reads the index in order.
     *
     * @see CREATE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_INDEX_IF_NOT_EXISTS(indexName: String, column: Pair<ClauseElement<*>, OrderByWay>, vararg columns: Pair<ClauseElement<*>, OrderByWay>): IndexStatement =
        indexStatement(indexName, Create.createIndex(this, databaseConnection, indexName, listOf(column) + columns, isIfNotExists = true))

    /**
     * Creates a unique index, unless one of that name exists, on [column] and [columns] of this table, each sorted in the order given, as the SQL
     * `CREATE UNIQUE INDEX name ON table(column DESC)` does: `CREATE_UNIQUE_INDEX_IF_NOT_EXISTS("idx_person_age", PersonTable.age to DESC)`.
     * A query that sorts in that order, or the reverse, reads the index in order.
     *
     * @see CREATE_UNIQUE_INDEX
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> Table<T>.CREATE_UNIQUE_INDEX_IF_NOT_EXISTS(indexName: String, column: Pair<ClauseElement<*>, OrderByWay>, vararg columns: Pair<ClauseElement<*>, OrderByWay>): IndexStatement =
        indexStatement(indexName, Create.createUniqueIndex(this, databaseConnection, indexName, listOf(column) + columns, isIfNotExists = true))

    /**
     * Adds [statement], which creates the index named [indexName] of this table, and returns it for a `WHERE` to make a
     * partial index.
     */
    private fun Table<*>.indexStatement(indexName: String, statement: SingleStatement): IndexStatement {
        addStatement(statement)
        return IndexStatement(this, indexName, databaseConnection, currentContainer, statement)
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

    /**
     * Drops [table] if it exists, as the SQL `DROP TABLE IF EXISTS` does, so that a migration step can run more than
     * once.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> DROP_IF_EXISTS(table: Table<T>) {
        addStatement(Drop.drop(table, databaseConnection, isIfExists = true))
    }

    /**
     * Drops this table if it exists.
     *
     * @see DROP_IF_EXISTS
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("dropIfExists")
    public fun <T> Table<T>.DROP_IF_EXISTS(): Unit = DROP_IF_EXISTS(this)

    /**
     * Drops the index named [indexName], as the SQL `DROP INDEX name` does, such as one that [CREATE_INDEX] or
     * [CREATE_UNIQUE_INDEX] created.
     *
     * Example:
     * ```kotlin
     * database {
     *     DROP_INDEX("idx_user_email")
     * }
     * ```
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun DROP_INDEX(indexName: String) {
        addStatement(Drop.dropIndex(indexName, databaseConnection))
    }

    /**
     * Drops the index named [indexName] if it exists, as the SQL `DROP INDEX IF EXISTS` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun DROP_INDEX_IF_EXISTS(indexName: String) {
        addStatement(Drop.dropIndex(indexName, databaseConnection, isIfExists = true))
    }

    // ========== CREATE VIEW and DROP VIEW ==========

    /**
     * Starts the creation of [view], which `AS` completes with the SELECT that defines it.
     *
     * Example:
     * ```kotlin
     * CREATE_VIEW(AdultView) AS (PersonTable SELECT WHERE<Adult>(PersonTable.age GTE 18))
     * // CREATE VIEW adults(name,age) AS SELECT name,age FROM person WHERE person.age>=18
     * ```
     *
     * @return The view to create, which becomes a statement once `AS` gives it its SELECT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> CREATE_VIEW(view: View<T>): ViewDefinition<T> = ViewDefinition(view)

    /**
     * Starts the definition of [view] unless a view of its name exists, as the SQL `CREATE VIEW IF NOT EXISTS` does,
     * which [AS] completes with the SELECT that defines it.
     *
     * The existing view isn't checked against the SELECT: if it differs, it stays as it is.
     *
     * @see CREATE_VIEW
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun <T> CREATE_VIEW_IF_NOT_EXISTS(view: View<T>): ViewDefinition<T> = ViewDefinition(view, isIfNotExists = true)

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
        addStatement(Create.createView(view, select, databaseConnection, isIfNotExists))
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

    /**
     * Drops [view] if it exists, as the SQL `DROP VIEW IF EXISTS` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> DROP_IF_EXISTS(view: View<T>) {
        addStatement(Drop.dropView(view, databaseConnection, isIfExists = true))
    }

    /**
     * Drops this view if it exists.
     *
     * @see DROP_IF_EXISTS
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    @JvmName("dropIfExists")
    public fun <T> View<T>.DROP_IF_EXISTS(): Unit = DROP_IF_EXISTS(this)

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
     * `ALTER TABLE ... RENAME COLUMN` needs SQLite 3.25.0, which Android has from API 30 on.
     *
     * @param oldColumn The current column to rename
     * @param newColumn The new column definition with the target name
     */
    @PlatformDependentSQLiteAPI
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
     * `ALTER TABLE ... RENAME COLUMN` needs SQLite 3.25.0, which Android has from API 30 on.
     *
     * @param oldColumnName The current name of the column to rename
     * @param newColumn The new column definition with the target name
     */
    @PlatformDependentSQLiteAPI
    @StatementDslMaker
    public fun <T> Table<T>.RENAME_COLUMN(oldColumnName: String, newColumn: ClauseElement<*>) {
        val statement = Alter.renameColumn(this, oldColumnName, newColumn, databaseConnection)
        addStatement(statement)
    }

    /**
     * Removes a column from this table.
     *
     * **⚠️ WARNING**: This permanently deletes the column and all its data.
     * `ALTER TABLE ... DROP COLUMN` needs SQLite 3.35.0, which Android has from API 34 on. Where SQLite is older,
     * rebuild the table without the column, as `withName` describes.
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
    @PlatformDependentSQLiteAPI
    @StatementDslMaker
    public infix fun <T> Table<T>.DROP_COLUMN(column: ClauseElement<*>) {
        val statement = Alter.dropColumn(this, column, databaseConnection)
        addStatement(statement)
    }

    // ========== ANALYZE, REINDEX and PRAGMA optimize ==========

    /**
     * Gathers the statistics of all tables and indexes, as the SQL `ANALYZE` does: the query planner chooses indexes
     * with them, and only reads them when the database is opened. It reads every row, so it takes long for a large
     * database; `PRAGMA_OPTIMIZE()` only analyzes what would help.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun ANALYZE() {
        addStatement(Maintenance.analyze(null, databaseConnection))
    }

    /**
     * Gathers the statistics of [table] and its indexes, as the SQL `ANALYZE table` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun ANALYZE(table: Table<*>) {
        addStatement(Maintenance.analyze(table.tableName, databaseConnection))
    }

    /**
     * Gathers the statistics of the table or index named [name], as the SQL `ANALYZE name` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun ANALYZE(name: String) {
        addStatement(Maintenance.analyze(name, databaseConnection))
    }

    /**
     * Rebuilds all indexes, as the SQL `REINDEX` does, such as after a change of a collation the app registers.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun REINDEX() {
        addStatement(Maintenance.reindex(null, databaseConnection))
    }

    /**
     * Rebuilds the indexes of [table], as the SQL `REINDEX table` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun REINDEX(table: Table<*>) {
        addStatement(Maintenance.reindex(table.tableName, databaseConnection))
    }

    /**
     * Rebuilds the index named [name], or the indexes of the table named so, as the SQL `REINDEX name` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun REINDEX(name: String) {
        addStatement(Maintenance.reindex(name, databaseConnection))
    }

    /**
     * Rebuilds the indexes that use [collation], as the SQL `REINDEX collation` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun REINDEX(collation: Collation) {
        addStatement(Maintenance.reindex(collation, databaseConnection))
    }

    /**
     * Gathers the statistics that would help the query planner, if any, as the SQL `PRAGMA optimize` does. It is quick,
     * and SQLite advises to run it before closing a database, or every few hours in a long-lived one.
     *
     * `PRAGMA optimize` needs SQLite 3.18.0, which Android has from API 26 on.
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_OPTIMIZE() {
        addStatement(PRAGMA.optimize(databaseConnection))
    }

    // ========== EXPLAIN QUERY PLAN ==========

    /**
     * Tells how SQLite would run [select], as the SQL `EXPLAIN QUERY PLAN` does, without running it: which tables it
     * scans, which indexes it searches, and where it sorts the rows with a temporary B-tree. The plan is read, as the
     * results of a SELECT are, once the scope has run.
     *
     * Example:
     * ```kotlin
     * val plan = database {
     *     EXPLAIN_QUERY_PLAN(BookTable SELECT WHERE(BookTable.author EQ "Dan Brown") ORDER_BY (BookTable.price to ASC))
     * }.getResults()
     * println(plan)
     * // QUERY PLAN
     * // |--SEARCH book USING INDEX idx_book_author (author=?)
     * // `--USE TEMP B-TREE FOR ORDER BY
     * check(plan.details.any { "idx_book_author" in it })
     * ```
     *
     * [select] can be any SELECT, with joins, subqueries or compound operators. It becomes part of the statement, so
     * it no longer runs on its own, and it is planned with its parameters, as it would run. The plan depends on the
     * indexes, and on the statistics that [ANALYZE] and [PRAGMA_OPTIMIZE] gather. Its details are SQLite's text, which
     * differs between SQLite versions, as [QueryPlan] describes. A trigger can't run it.
     *
     * @return The statement, whose `getResults()` returns the plan once the scope has run
     * @throws IllegalArgumentException if [select] is incomplete, as an aggregate query that needs GROUP BY
     * @see QueryPlan
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun EXPLAIN_QUERY_PLAN(select: SelectStatement<*>): QueryPlanStatement {
        select.takeOut()
        val statement = QueryPlanStatement("EXPLAIN QUERY PLAN ${select.sqlStr}", databaseConnection, select.parameters?.toMutableList())
        addStatement(statement)
        return statement
    }

    // ========== PRAGMAs that check the database, read its schema and its file, or set how statements run ==========
    //
    // A PRAGMA that reads returns a PragmaStatement, whose getResults() returns what it read once the scope has run, as
    // the results of a SELECT are read. Their types are in com.ctrip.sqllin.dsl.sql.pragma. A trigger can't run them.
    // A PRAGMA that only newer versions of SQLite have fails with an IllegalStateException where the SQLite lacks it,
    // which ignores it rather than failing, as its rows have no columns then.

    /**
     * Whether the database enforces foreign keys, as the SQL `PRAGMA foreign_keys` tells, which
     * `PRAGMA_FOREIGN_KEYS(flag)` sets.
     *
     * @return The statement, whose `getResults()` returns whether it does once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEYS(): PragmaStatement<Boolean> = pragma(PRAGMA.isForeignKeysEnabled(databaseConnection))

    /**
     * Checks the whole database, as the SQL `PRAGMA integrity_check(N)` does: that its pages, rows and indexes are
     * well-formed and agree with each other, and that the rows satisfy their NOT NULL and CHECK constraints, but not
     * their foreign keys, which [PRAGMA_FOREIGN_KEY_CHECK] checks.
     *
     * Example:
     * ```kotlin
     * val problems = database { PRAGMA_INTEGRITY_CHECK() }.getResults()
     * if (problems.isNotEmpty()) {
     *     // The database is corrupt
     * }
     * ```
     *
     * It reads the whole database, so it takes long for a large one, which [PRAGMA_QUICK_CHECK] takes less long for.
     *
     * @param maxErrors How many problems to report at most
     * @return The statement, whose `getResults()` returns the problems, in SQLite's words, or an empty list if there
     * are none, once the scope has run
     * @throws IllegalArgumentException if [maxErrors] isn't positive
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INTEGRITY_CHECK(maxErrors: Int = 100): PragmaStatement<List<String>> {
        require(maxErrors > 0) { "PRAGMA integrity_check needs a positive number of errors, rather than $maxErrors." }
        return pragma(PRAGMA.check("integrity_check", maxErrors, databaseConnection))
    }

    /**
     * Checks the whole database as [PRAGMA_INTEGRITY_CHECK] does, as the SQL `PRAGMA quick_check(N)` does, except that
     * the indexes hold the rows of their tables, and that the rows satisfy their UNIQUE constraints, so that it takes
     * time in proportion to the size of the database, rather than more.
     *
     * @param maxErrors How many problems to report at most
     * @return The statement, whose `getResults()` returns the problems, in SQLite's words, or an empty list if there
     * are none, once the scope has run
     * @throws IllegalArgumentException if [maxErrors] isn't positive
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_QUICK_CHECK(maxErrors: Int = 100): PragmaStatement<List<String>> {
        require(maxErrors > 0) { "PRAGMA quick_check needs a positive number of errors, rather than $maxErrors." }
        return pragma(PRAGMA.check("quick_check", maxErrors, databaseConnection))
    }

    /**
     * Checks [table] and its indexes as [PRAGMA_INTEGRITY_CHECK] checks the whole database, as the SQL
     * `PRAGMA integrity_check(table)` does, reporting up to 100 problems.
     *
     * It needs SQLite 3.33.0, which Android has from API 34 on: an older SQLite takes the name of the table for a number
     * of errors, which it ignores, so that it checks the whole database instead.
     *
     * @return The statement, whose `getResults()` returns the problems, in SQLite's words, or an empty list if there
     * are none, once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INTEGRITY_CHECK(table: Table<*>): PragmaStatement<List<String>> =
        pragma(PRAGMA.check("integrity_check", table.tableName, databaseConnection))

    /**
     * Checks the table named [table] and its indexes, as the SQL `PRAGMA integrity_check(table)` does.
     *
     * @see PRAGMA_INTEGRITY_CHECK
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INTEGRITY_CHECK(table: String): PragmaStatement<List<String>> =
        pragma(PRAGMA.check("integrity_check", table, databaseConnection))

    /**
     * Checks [table] and its indexes as [PRAGMA_QUICK_CHECK] checks the whole database, as the SQL
     * `PRAGMA quick_check(table)` does, reporting up to 100 problems.
     *
     * It needs SQLite 3.33.0, which Android has from API 34 on: an older SQLite takes the name of the table for a number
     * of errors, which it ignores, so that it checks the whole database instead.
     *
     * @return The statement, whose `getResults()` returns the problems, in SQLite's words, or an empty list if there
     * are none, once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_QUICK_CHECK(table: Table<*>): PragmaStatement<List<String>> =
        pragma(PRAGMA.check("quick_check", table.tableName, databaseConnection))

    /**
     * Checks the table named [table] and its indexes, as the SQL `PRAGMA quick_check(table)` does.
     *
     * @see PRAGMA_QUICK_CHECK
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_QUICK_CHECK(table: String): PragmaStatement<List<String>> =
        pragma(PRAGMA.check("quick_check", table, databaseConnection))

    /**
     * Finds the rows of all tables that violate their foreign keys, as the SQL `PRAGMA foreign_key_check` does, such as
     * after a migration that turned foreign keys off with `PRAGMA_FOREIGN_KEYS(false)`. It checks them whether or not
     * the database enforces them.
     *
     * @return The statement, whose `getResults()` returns the violations, or an empty list if there are none, once the
     * scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEY_CHECK(): PragmaStatement<List<ForeignKeyViolation>> =
        pragma(PRAGMA.foreignKeyCheck(null, databaseConnection))

    /**
     * Finds the rows of [table] that violate its foreign keys, as the SQL `PRAGMA foreign_key_check(table)` does.
     *
     * @see PRAGMA_FOREIGN_KEY_CHECK
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEY_CHECK(table: Table<*>): PragmaStatement<List<ForeignKeyViolation>> =
        pragma(PRAGMA.foreignKeyCheck(table.tableName, databaseConnection))

    /**
     * Finds the rows of the table named [table] that violate its foreign keys, as the SQL
     * `PRAGMA foreign_key_check(table)` does.
     *
     * @see PRAGMA_FOREIGN_KEY_CHECK
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEY_CHECK(table: String): PragmaStatement<List<ForeignKeyViolation>> =
        pragma(PRAGMA.foreignKeyCheck(table, databaseConnection))

    /**
     * The columns of [table] in the database, as the SQL `PRAGMA table_info(table)` tells, such as to check in a
     * migration whether a column exists. It doesn't list the hidden and generated columns. A table that doesn't exist
     * has no columns.
     *
     * Example:
     * ```kotlin
     * val columns = database { PRAGMA_TABLE_INFO(PersonTable) }.getResults()
     * val hasEmail = columns.any { it.name == "email" }
     * ```
     *
     * @return The statement, whose `getResults()` returns the columns, in the order of the table, once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_INFO(table: Table<*>): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableInfo(table.tableName, databaseConnection))

    /**
     * The columns of [view] in the database, as the SQL `PRAGMA table_info(view)` tells.
     *
     * @see PRAGMA_TABLE_INFO
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_INFO(view: View<*>): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableInfo(view.tableName, databaseConnection))

    /**
     * The columns of the table or view named [name], as the SQL `PRAGMA table_info(name)` tells, such as of a table
     * that the app no longer declares.
     *
     * @see PRAGMA_TABLE_INFO
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_INFO(name: String): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableInfo(name, databaseConnection))

    /**
     * The columns of [table] in the database, its hidden and generated ones included, as the SQL
     * `PRAGMA table_xinfo(table)` tells: [PRAGMA_TABLE_INFO] with the [ColumnInfo.kind] of each column, such as the
     * hidden columns of an FTS table. A table that doesn't exist has no columns.
     *
     * It needs SQLite 3.26.0, which Android has from API 30 on, and fails with an [IllegalStateException] where the
     * SQLite lacks it.
     *
     * @return The statement, whose `getResults()` returns the columns, in the order of the table, once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_XINFO(table: Table<*>): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableXinfo(table.tableName, databaseConnection))

    /**
     * The columns of [view] in the database, as the SQL `PRAGMA table_xinfo(view)` tells.
     *
     * @see PRAGMA_TABLE_XINFO
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_XINFO(view: View<*>): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableXinfo(view.tableName, databaseConnection))

    /**
     * The columns of the table or view named [name], as the SQL `PRAGMA table_xinfo(name)` tells.
     *
     * @see PRAGMA_TABLE_XINFO
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_XINFO(name: String): PragmaStatement<List<ColumnInfo>> =
        pragma(PRAGMA.tableXinfo(name, databaseConnection))

    /**
     * The tables and views of the databases of the connection, as the SQL `PRAGMA table_list` tells: those of `main`,
     * with the virtual tables, as FTS tables, and the tables they keep their data in, and `sqlite_schema`, and those of
     * `temp`, as the TEMP table that observed queries count changes with.
     *
     * It needs SQLite 3.37.0, which Android has from API 34 on, and fails with an [IllegalStateException] where the
     * SQLite lacks it.
     *
     * @return The statement, whose `getResults()` returns the tables and views once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_TABLE_LIST(): PragmaStatement<List<RelationInfo>> = pragma(PRAGMA.tableList(databaseConnection))

    /**
     * The indexes of [table], as the SQL `PRAGMA index_list(table)` tells: those that CREATE INDEX created, and those
     * that SQLite created for its UNIQUE and PRIMARY KEY constraints, except for an INTEGER PRIMARY KEY, which is the
     * rowid, which needs none.
     *
     * @return The statement, whose `getResults()` returns the indexes once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INDEX_LIST(table: Table<*>): PragmaStatement<List<IndexInfo>> =
        pragma(PRAGMA.indexList(table.tableName, databaseConnection))

    /**
     * The indexes of the table named [table], as the SQL `PRAGMA index_list(table)` tells.
     *
     * @see PRAGMA_INDEX_LIST
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INDEX_LIST(table: String): PragmaStatement<List<IndexInfo>> =
        pragma(PRAGMA.indexList(table, databaseConnection))

    /**
     * The columns of the index named [index], as the SQL `PRAGMA index_xinfo(index)` tells: those it is keyed by, in
     * order, with their sort orders and collations, then those it holds besides, as the rowid. An index that doesn't
     * exist has no columns.
     *
     * @return The statement, whose `getResults()` returns the columns once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_INDEX_XINFO(index: String): PragmaStatement<List<IndexColumn>> =
        pragma(PRAGMA.indexXinfo(index, databaseConnection))

    /**
     * The foreign keys of [table], as the SQL `PRAGMA foreign_key_list(table)` tells, a row for each of their columns.
     *
     * @return The statement, whose `getResults()` returns the columns of the foreign keys once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEY_LIST(table: Table<*>): PragmaStatement<List<ForeignKeyInfo>> =
        pragma(PRAGMA.foreignKeyList(table.tableName, databaseConnection))

    /**
     * The foreign keys of the table named [table], as the SQL `PRAGMA foreign_key_list(table)` tells.
     *
     * @see PRAGMA_FOREIGN_KEY_LIST
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FOREIGN_KEY_LIST(table: String): PragmaStatement<List<ForeignKeyInfo>> =
        pragma(PRAGMA.foreignKeyList(table, databaseConnection))

    /**
     * The options the SQLite was compiled with, as the SQL `PRAGMA compile_options` tells, without their `SQLITE_`
     * prefix, as `ENABLE_FTS4` or `THREADSAFE=1`, which tell what the SQLite of a platform has.
     *
     * A SQLite can be built without the PRAGMA, as Android's is, where it fails with an [IllegalStateException].
     *
     * @return The statement, whose `getResults()` returns the options once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_COMPILE_OPTIONS(): PragmaStatement<List<String>> =
        pragma(PRAGMA.names("compile_options", "compile_options", null, databaseConnection))

    /**
     * The names of the collations of the connection, as the SQL `PRAGMA collation_list` tells: SQLite's `BINARY`,
     * `NOCASE` and `RTRIM`, and those the platform or the app registers, as Android's `LOCALIZED` and `UNICODE`.
     *
     * @return The statement, whose `getResults()` returns the names once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_COLLATION_LIST(): PragmaStatement<List<String>> =
        pragma(PRAGMA.names("collation_list", "name", null, databaseConnection))

    /**
     * The databases of the connection, as the SQL `PRAGMA database_list` tells: `main`, with the path of its file,
     * and `temp` once the connection has TEMP tables.
     *
     * @return The statement, whose `getResults()` returns the databases once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_DATABASE_LIST(): PragmaStatement<List<DatabaseInfo>> = pragma(PRAGMA.databaseList(databaseConnection))

    /**
     * The SQL functions of the connection, as the SQL `PRAGMA function_list` tells, such as to check whether the SQLite
     * of a platform has the math functions, which Android's lacks.
     *
     * It needs SQLite 3.30.0, which Android has from API 31 on, and fails with an [IllegalStateException] where the
     * SQLite lacks it.
     *
     * @return The statement, whose `getResults()` returns a row for each form of each function once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FUNCTION_LIST(): PragmaStatement<List<FunctionInfo>> = pragma(PRAGMA.functionList(databaseConnection))

    /**
     * The names of the modules of virtual tables of the connection, as the SQL `PRAGMA module_list` tells, as `fts4` or
     * `rtree`, which tell the virtual tables the SQLite of a platform can create.
     *
     * It needs SQLite 3.30.0, which Android has from API 31 on, and fails with an [IllegalStateException] where the
     * SQLite lacks it.
     *
     * @return The statement, whose `getResults()` returns the names once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_MODULE_LIST(): PragmaStatement<List<String>> =
        pragma(PRAGMA.names("module_list", "name", "SQLite 3.30.0, which Android has from API 31 on", databaseConnection))

    /**
     * The names of the PRAGMAs the SQLite has, as the SQL `PRAGMA pragma_list` tells.
     *
     * It needs SQLite 3.30.0, which Android has from API 31 on, and fails with an [IllegalStateException] where the
     * SQLite lacks it.
     *
     * @return The statement, whose `getResults()` returns the names once the scope has run
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_PRAGMA_LIST(): PragmaStatement<List<String>> =
        pragma(PRAGMA.names("pragma_list", "name", "SQLite 3.30.0, which Android has from API 31 on", databaseConnection))

    /**
     * Checkpoints the WAL, as the SQL `PRAGMA wal_checkpoint(mode)` does: copies the pages it holds into the database
     * file, as SQLite does on its own once it holds 1000 pages, such as before copying the database file, which
     * doesn't hold what the WAL does. A FULL, RESTART or TRUNCATE checkpoint waits for the other connections of the
     * database as long as `busyTimeout`, and tells whether they kept it from finishing. A database that isn't in WAL
     * mode has nothing to checkpoint.
     *
     * @param mode How to checkpoint
     * @return The statement, whose `getResults()` returns the result of the checkpoint once the scope has run
     * @throws IllegalStateException inside a transaction, as SQLite can't checkpoint there
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_WAL_CHECKPOINT(mode: CheckpointMode = CheckpointMode.PASSIVE): PragmaStatement<CheckpointResult> {
        check(!isInTransaction) { "SQLite can't checkpoint the WAL inside a transaction." }
        return pragma(PRAGMA.walCheckpoint(mode, databaseConnection))
    }

    /**
     * The number of pages of the database file, as the SQL `PRAGMA page_count` tells, which, times
     * [PRAGMA_PAGE_SIZE], is its size, without what its WAL holds.
     *
     * @return The statement, whose `getResults()` returns the number once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_PAGE_COUNT(): PragmaStatement<Long> = pragma(PRAGMA.size("page_count", isInTransaction, databaseConnection))

    /**
     * The number of unused pages of the database file, as the SQL `PRAGMA freelist_count` tells, which rows that were
     * deleted leave, and which [VACUUM] removes, so that the file shrinks. A database whose auto_vacuum is on removes them
     * at each commit, so that it has none: Android's SQLite creates a database with auto_vacuum FULL by default.
     *
     * @return The statement, whose `getResults()` returns the number once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_FREELIST_COUNT(): PragmaStatement<Long> = pragma(PRAGMA.size("freelist_count", isInTransaction, databaseConnection))

    /**
     * The size of a page of the database file in bytes, as the SQL `PRAGMA page_size` tells. Outside a transaction,
     * it is read in a transaction of its own, as SQLite takes it from what the connection last read of the header of
     * the file, which a transaction reads again.
     *
     * @return The statement, whose `getResults()` returns the size once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_PAGE_SIZE(): PragmaStatement<Long> = pragma(PRAGMA.size("page_size", isInTransaction, databaseConnection))

    /**
     * The number that the header of the database file holds to tell the app it belongs to, as the SQL
     * `PRAGMA application_id` tells, which SQLite doesn't use, 0 unless it is set.
     *
     * @return The statement, whose `getResults()` returns the number once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_APPLICATION_ID(): PragmaStatement<Int> = pragma(PRAGMA.applicationId(databaseConnection))

    /**
     * Sets the number that the header of the database file holds to tell the app it belongs to, as the SQL
     * `PRAGMA application_id = id` does, such as to tell a file of the app from others it opens.
     *
     * @param id The number
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_APPLICATION_ID(id: Int) {
        pragma(PRAGMA.applicationId(id, databaseConnection))
    }

    /**
     * Whether the database removes its unused pages, as the SQL `PRAGMA auto_vacuum` tells: Android's SQLite creates a
     * database with [AutoVacuum.FULL] by default, and the others with [AutoVacuum.NONE]. Outside a transaction, it is
     * read in a transaction of its own, as SQLite takes it from what the connection last read of the header of the
     * file, which a transaction reads again: from Android 13 on, a query outside a transaction may run on a connection
     * that reads, which would tell the mode before the last VACUUM.
     *
     * @return The statement, whose `getResults()` returns the mode once the scope has run
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_AUTO_VACUUM(): PragmaStatement<AutoVacuum> = pragma(PRAGMA.autoVacuum(isInTransaction, databaseConnection))

    /**
     * Sets whether the database removes its unused pages, as the SQL `PRAGMA auto_vacuum = mode` does. It applies to a
     * database that has no tables yet, as in `create` of the configuration before the first CREATE, and otherwise
     * only once [VACUUM] rebuilds the database, which it has to be followed by:
     * ```kotlin
     * database {
     *     PRAGMA_AUTO_VACUUM(AutoVacuum.INCREMENTAL)
     *     VACUUM()
     * }
     * ```
     *
     * @param mode Whether to remove them at each commit, or to keep them, as [AutoVacuum.INCREMENTAL] does until the
     * SQL `PRAGMA incremental_vacuum` removes them, which SQLlin doesn't run: it removes a page each time it returns a
     * row, which has no columns, and sqlite-jdbc runs such a statement only as far as its first row
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_AUTO_VACUUM(mode: AutoVacuum) {
        pragma(PRAGMA.autoVacuum(mode, databaseConnection))
    }

    /**
     * Defers the checks of foreign keys to the end of the transaction, or stops deferring them, as the SQL
     * `PRAGMA defer_foreign_keys` does, so that the statements after it may violate them for a while, as when a row is
     * inserted before the row it references. A violation that remains when the transaction commits fails the commit.
     *
     * Example:
     * ```kotlin
     * database { PRAGMA_FOREIGN_KEYS(true) }
     * database {
     *     transaction {
     *         PRAGMA_DEFER_FOREIGN_KEYS(true)
     *         OrderTable INSERT Order(userId = 1)
     *         UserTable INSERT User(id = 1)
     *     }
     * }
     * ```
     *
     * SQLite turns it off again when the transaction ends, so it can only be set in one. It only matters while the
     * database enforces foreign keys, which `PRAGMA_FOREIGN_KEYS(true)` turns on, outside a transaction.
     *
     * @param flag Whether to defer the checks
     * @throws IllegalStateException outside a transaction
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_DEFER_FOREIGN_KEYS(flag: Boolean) {
        check(isInTransaction) { "PRAGMA defer_foreign_keys only lasts until the transaction ends, so it can only be set in transaction { }." }
        pragma(PRAGMA.set("defer_foreign_keys", flag, databaseConnection))
    }

    /**
     * Lets triggers fire recursively, or stops them, as the SQL `PRAGMA recursive_triggers` does: a statement that a
     * trigger runs then fires the triggers of its table, the trigger itself included, which SQLite doesn't by default,
     * up to a depth of 1000 by default. A REPLACE conflict resolution then also fires the DELETE triggers of the rows
     * it deletes.
     *
     * It lasts as long as the database is open, for the scopes after this one too, until it is set again. In a
     * transaction, it applies to the statements after it.
     *
     * @param flag Whether triggers fire recursively
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_RECURSIVE_TRIGGERS(flag: Boolean) {
        pragma(PRAGMA.set("recursive_triggers", flag, databaseConnection))
    }

    /**
     * Makes `ALTER_RENAME_TABLE_TO` rename a table as SQLite 3.25.0 and earlier did, or as it does now, as the SQL
     * `PRAGMA legacy_alter_table` does: without rewriting the views and triggers that read the table, which then read
     * a table of the old name, such as in a migration that rebuilds a table under the old name.
     *
     * It lasts as long as the database is open, for the scopes after this one too, until it is set again. Android's
     * SQLite turns it on by default, so that a rename there doesn't rewrite them unless it is turned off.
     *
     * @param flag Whether to rename tables as SQLite 3.25.0 and earlier did
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_LEGACY_ALTER_TABLE(flag: Boolean) {
        pragma(PRAGMA.set("legacy_alter_table", flag, databaseConnection))
    }

    /**
     * Stops checking CHECK constraints, or checks them again, as the SQL `PRAGMA ignore_check_constraints` does, such
     * as to import rows that predate a constraint; [PRAGMA_INTEGRITY_CHECK] then finds the rows that violate them.
     *
     * It lasts as long as the database is open, for the scopes after this one too, until it is set again.
     *
     * @param flag Whether to ignore CHECK constraints
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun PRAGMA_IGNORE_CHECK_CONSTRAINTS(flag: Boolean) {
        pragma(PRAGMA.set("ignore_check_constraints", flag, databaseConnection))
    }

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    private fun <R> pragma(statement: PragmaStatement<R>): PragmaStatement<R> {
        addStatement(statement)
        return statement
    }

    // ========== CREATE TRIGGER and DROP TRIGGER ==========

    /** The trigger being defined, from ON to the end of BEGIN, whose rows `new()` and `old()` read. */
    private var definingTrigger: TriggerDefinition? = null

    /** The body of the trigger being defined, which collects the statements written in BEGIN. */
    private var triggerBody: TriggerBody? = null

    /**
     * Creates a trigger named [name], as the SQL `CREATE TRIGGER` does: statements that run whenever a row of a table is
     * inserted, deleted or updated, before or after, where a condition holds:
     * ```kotlin
     * // CREATE TRIGGER person_visits AFTER UPDATE OF visits ON person WHEN NEW.visits>OLD.visits
     * // BEGIN UPDATE stats SET visits=(stats.visits + 1); END
     * CREATE_TRIGGER("person_visits") AFTER UPDATE_OF(PersonTable.visits) ON PersonTable WHEN (new(PersonTable.visits) GT old(PersonTable.visits)) BEGIN {
     *     StatsTable UPDATE SET((StatsTable.visits + 1) AS Stats::visits)
     * }
     * ```
     * `new(column)` and `old(column)` read the row being written, in the condition and the statements: a trigger on
     * INSERT has a new row, one on DELETE an old row, and one on UPDATE both. The body can insert, update and delete
     * rows, and `RAISE` can stop the statement that fired the trigger. Its values are written into the SQL, as SQLite
     * doesn't let a trigger take parameters.
     *
     * A trigger can't be created on an FTS table, which is a virtual table, nor on a view, as an INSTEAD OF trigger is
     * of no use while SQLlin can't write to views. Dropping a table drops its triggers.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun CREATE_TRIGGER(name: String): TriggerName = TriggerName(name, isIfNotExists = false)

    /**
     * Creates a trigger named [name] unless one of that name exists, as the SQL `CREATE TRIGGER IF NOT EXISTS` does.
     *
     * @see CREATE_TRIGGER
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun CREATE_TRIGGER_IF_NOT_EXISTS(name: String): TriggerName = TriggerName(name, isIfNotExists = true)

    /** The event of a trigger that fires when a row is inserted, which has a new row. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val INSERT: TriggerEvent
        get() = TriggerEvent("INSERT", hasNewRow = true, hasOldRow = false)

    /** The event of a trigger that fires when a row is deleted, which has an old row. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val DELETE: TriggerEvent
        get() = TriggerEvent("DELETE", hasNewRow = false, hasOldRow = true)

    /** The event of a trigger that fires when a row is updated, which has a new row and an old row. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val UPDATE: TriggerEvent
        get() = TriggerEvent("UPDATE", hasNewRow = true, hasOldRow = true)

    /**
     * The event of a trigger that fires when any of [columns] of a row is updated, as the SQL `UPDATE OF` does.
     *
     * @throws IllegalArgumentException if there are no columns
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun UPDATE_OF(vararg columns: ClauseElement<*>): TriggerEvent {
        require(columns.isNotEmpty()) { "UPDATE OF needs at least one column." }
        return TriggerEvent("UPDATE OF ${columns.joinToString(",") { it.valueName }}", hasNewRow = true, hasOldRow = true, columns.toList())
    }

    /** Makes this trigger fire before its event, so that it can stop it with `RAISE`. */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun TriggerName.BEFORE(event: TriggerEvent): TriggerTiming = TriggerTiming(this, "BEFORE", event)

    /** Makes this trigger fire after its event. */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun TriggerName.AFTER(event: TriggerEvent): TriggerTiming = TriggerTiming(this, "AFTER", event)

    /**
     * Gives this trigger the [table] whose rows fire it.
     *
     * @throws IllegalArgumentException if [table] is an FTS table, on which SQLite can't create a trigger, or a column of
     * `UPDATE_OF` isn't a column of [table]
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun <T> TriggerTiming.ON(table: Table<T>): TriggerDefinition {
        check(triggerBody == null) { "A trigger can't be created in the body of another." }
        require(table !is FtsTable<*>) { "SQLite can't create a trigger on '${table.tableName}', which is an FTS table, a virtual table." }
        for (column in event.columns) {
            require(!column.isFunction && column.table.tableName == table.tableName) {
                "The trigger '${trigger.name}' on '${table.tableName}' can only fire on an UPDATE OF its columns, but '${column.valueName}' isn't one."
            }
        }
        return TriggerDefinition(this, table).also { definingTrigger = it }
    }

    /**
     * Makes this trigger fire only for the rows where [condition] holds, as the SQL `WHEN` does. It can read the rows
     * being written with `new()` and `old()`.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun TriggerDefinition.WHEN(condition: SelectCondition): TriggerDefinition {
        check(this.condition == null) { "The trigger '${timing.trigger.name}' already has a WHEN." }
        return TriggerDefinition(timing, table, condition).also { definingTrigger = it }
    }

    /**
     * Gives this trigger its statements, which [body] writes as it writes any: INSERT, UPDATE, DELETE and `RAISE`. They
     * run when the trigger fires, not when the scope ends, and can read the rows being written with `new()` and `old()`.
     *
     * @throws IllegalArgumentException if [body] writes no statement, or another one, such as a SELECT
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public infix fun TriggerDefinition.BEGIN(body: () -> Unit) {
        val collected = TriggerBody()
        definingTrigger = this
        triggerBody = collected
        try {
            body()
        } finally {
            triggerBody = null
            definingTrigger = null
        }
        val name = timing.trigger.name
        require(collected.statements.isNotEmpty()) { "The trigger '$name' needs at least one statement." }
        for (statement in collected.statements) {
            require(Changes.writtenTable(statement) != null || statement is RaiseStatement) {
                "The trigger '$name' can only run INSERT, UPDATE, DELETE and RAISE, but not: ${statement.sqlStr}"
            }
            statement.checkComplete()
        }
        addStatement(Trigger.create(this, collected.statements, databaseConnection))
    }

    /**
     * The [column] of the new row of the trigger being defined, `NEW.column`: the row inserted, or a row as it is
     * updated to. It can only be used in the WHEN and the body of a trigger on INSERT or UPDATE.
     *
     * @throws IllegalStateException if no trigger is being defined
     * @throws IllegalArgumentException if the trigger has no new row, which SQLite would only report when it fires, or
     * [column] isn't a column of its table
     */
    @ExperimentalDSLDatabaseAPI
    public fun <E : ClauseElement<*>> new(column: E): E = rowColumn(column, NEW_ROW)

    /**
     * The [column] of the old row of the trigger being defined, `OLD.column`: the row deleted, or a row as it was before
     * it is updated. It can only be used in the WHEN and the body of a trigger on UPDATE or DELETE.
     *
     * @throws IllegalStateException if no trigger is being defined
     * @throws IllegalArgumentException if the trigger has no old row, which SQLite would only report when it fires, or
     * [column] isn't a column of its table
     */
    @ExperimentalDSLDatabaseAPI
    public fun <E : ClauseElement<*>> old(column: E): E = rowColumn(column, OLD_ROW)

    @Suppress("UNCHECKED_CAST")
    private fun <E : ClauseElement<*>> rowColumn(column: E, row: String): E {
        val definition = checkNotNull(definingTrigger) { "${row.lowercase()}() can only be used in the WHEN or the body of a trigger." }
        val event = definition.timing.event
        require(if (row == NEW_ROW) event.hasNewRow else event.hasOldRow) {
            "The trigger '${definition.timing.trigger.name}' on ${event.sql.substringBefore(' ')} has no $row row, which SQLite would only report when it fires."
        }
        require(!column.isFunction && column.table.tableName == definition.table.tableName) {
            "${row.lowercase()}() takes a column of '${definition.table.tableName}', the table of the trigger '${definition.timing.trigger.name}', but '${column.valueName}' isn't one."
        }
        return column.rebind(RowRelation(row, definition.table)) as E
    }

    /** `RAISE(ABORT, ...)`: stops the statement that fired the trigger and undoes its changes. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val ABORT: RaiseResolution
        get() = RaiseResolution("ABORT")

    /** `RAISE(FAIL, ...)`: stops the statement that fired the trigger, keeping the changes it made before. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val FAIL: RaiseResolution
        get() = RaiseResolution("FAIL")

    /** `RAISE(ROLLBACK, ...)`: stops the statement that fired the trigger and rolls back the transaction. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val ROLLBACK: RaiseResolution
        get() = RaiseResolution("ROLLBACK")

    /** `RAISE(IGNORE)`: skips the row that fired the trigger, and the rest of the trigger. */
    @ExperimentalDSLDatabaseAPI
    @KeyWordDslMaker
    public val IGNORE: RaiseIgnore
        get() = RaiseIgnore()

    /**
     * Stops the statement that fired the trigger with [message], which SQLite reports as the error, as the SQL
     * `RAISE(resolution, message)` does: `RAISE(ABORT, "age can't be negative")`. It can only be in the body of a
     * trigger, where it is usually run where the WHEN of the trigger holds.
     *
     * @throws IllegalStateException if no trigger is being defined
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun RAISE(resolution: RaiseResolution, message: String) {
        addRaise("RAISE(${resolution.sql},${sqlLiteral(message)})")
    }

    /**
     * Skips the row that fired the trigger, and the rest of the trigger, as the SQL `RAISE(IGNORE)` does.
     *
     * @throws IllegalStateException if no trigger is being defined
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun RAISE(ignore: RaiseIgnore) {
        addRaise("RAISE(IGNORE)")
    }

    private fun addRaise(raise: String) {
        check(triggerBody != null) { "RAISE can only be in the body of a trigger." }
        addStatement(RaiseStatement("SELECT $raise"))
    }

    /**
     * Drops the trigger named [name], as the SQL `DROP TRIGGER` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun DROP_TRIGGER(name: String) {
        addStatement(Trigger.drop(name, isIfExists = false, databaseConnection))
    }

    /**
     * Drops the trigger named [name] if it exists, as the SQL `DROP TRIGGER IF EXISTS` does.
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun DROP_TRIGGER_IF_EXISTS(name: String) {
        addStatement(Trigger.drop(name, isIfExists = true, databaseConnection))
    }

    // ========== VACUUM ==========

    /**
     * Rebuilds the database file, repacking it into the minimum disk space, as the SQL `VACUUM` does: the file doesn't
     * shrink when rows are deleted, as SQLite keeps the free pages to reuse them.
     *
     * VACUUM rewrites the whole database, so it takes long for a large one, and needs free disk space of up to twice
     * its size. It may change the rowids of the tables without an INTEGER PRIMARY KEY.
     *
     * Example:
     * ```kotlin
     * database {
     *     LogTable DELETE WHERE (LogTable.time LT expired)
     * }
     * database {
     *     VACUUM()
     * }
     * ```
     *
     * @throws IllegalStateException if it is called inside a transaction, where SQLite can't vacuum
     */
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun VACUUM() {
        check(!isInTransaction) { "SQLite can't VACUUM inside a transaction." }
        addStatement(Vacuum.vacuum(databaseConnection))
    }

    /**
     * Writes a vacuumed copy of the database to [file], as the SQL `VACUUM INTO file` does, leaving the database as it
     * is: a backup, consistent as of one moment, of a database that is in use.
     *
     * [file] is a path of the file system, so on Android, where the working directory can't be written, it has to be
     * absolute. It must not exist, or SQLite fails.
     *
     * `VACUUM INTO` needs SQLite 3.27.0, which Android has from API 30 on.
     *
     * Example:
     * ```kotlin
     * database {
     *     VACUUM_INTO("/data/data/com.example/backup/notes.db")
     * }
     * ```
     *
     * @throws IllegalStateException if it is called inside a transaction, where SQLite can't vacuum
     */
    @PlatformDependentSQLiteAPI
    @ExperimentalDSLDatabaseAPI
    @StatementDslMaker
    public fun VACUUM_INTO(file: String) {
        check(!isInTransaction) { "SQLite can't VACUUM inside a transaction." }
        addStatement(Vacuum.vacuumInto(file, databaseConnection))
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