package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.driver.DatabaseConfiguration
import com.ctrip.sqllin.driver.DatabasePath
import com.ctrip.sqllin.driver.openDatabase
import com.ctrip.sqllin.dsl.sql.*
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.operation.Delete
import com.ctrip.sqllin.dsl.sql.operation.Insert
import com.ctrip.sqllin.dsl.sql.operation.Update
import com.ctrip.sqllin.dsl.sql.operation.Select
import com.ctrip.sqllin.dsl.sql.statement.*
import com.ctrip.sqllin.dsl.sql.statement.DatabaseExecuteEngine
import com.ctrip.sqllin.dsl.sql.statement.TransactionStatementsGroup
import kotlinx.serialization.KSerializer
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.serializer
import kotlin.jvm.Volatile

/**
 * Database object
 * @author yaqiao
 */

@Suppress("UNCHECKED_CAST")
public class Database(
    configuration: DatabaseConfiguration,
) {

    public constructor(
        name: String,
        path: DatabasePath,
        version: Int,
    ) : this(
        DatabaseConfiguration(
            name = name,
            path = path,
            version = version,
        )
    )

    private val databaseConnection = openDatabase(configuration)

    /**
     * Close the database connection.
     */
    public fun close(): Unit = databaseConnection.close()

    /**
     * Start a scope with this database object that used for execute SQL.
     */
    public operator fun <T> invoke(block: Database.() -> T): T {
        val result = block()
        executeAllStatement()
        return result
    }

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
        transactionStatementsGroup = TransactionStatementsGroup(databaseConnection)
        executeEngine.addStatement(transactionStatementsGroup!!)
        return true
    }

    public fun endTransaction() {
        transactionStatementsGroup = null
    }

    public inline fun <T> transaction(block: Database.() -> T): T {
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

    private val executeEngine = DatabaseExecuteEngine()

    private fun addStatement(statement: SingleStatement) {
        if (isInTransaction)
            transactionStatementsGroup!!.addStatement(statement)
        else
            executeEngine.addStatement(statement)
    }

    private fun <T : DBEntity<T>> addSelectStatement(statement: SelectStatement<T>) {
        if (unionSelectStatementGroupStack.isNotEmpty)
            (unionSelectStatementGroupStack.top as UnionSelectStatementGroup<T>).addSelectStatement(statement)
        else
            addStatement(statement)
    }

    private fun executeAllStatement() = executeEngine.executeAllStatement()

    /**
     * Insert.
     */

    public infix fun <T : DBEntity<T>> Table<T>.INSERT(entities: Iterable<T>) {
        val statement = Insert.insert(this, databaseConnection, entities)
        addStatement(statement)
    }

    public infix fun <T : DBEntity<T>> Table<T>.INSERT(entity: T): Unit =
        INSERT(listOf(entity))

    /**
     * Update.
     */

    public infix fun <T : DBEntity<T>> Table<T>.UPDATE(clause: SetClause<T>): UpdateStatementWithoutWhereClause<T> =
        transactionStatementsGroup?.let {
            val statement = Update.update(this, databaseConnection, it, clause)
            it addStatement statement
            statement
        } ?: Update.update(this, databaseConnection, executeEngine, clause).also {
            executeEngine addStatement it
        }

    /**
     * Delete.
     */

    public infix fun Table<*>.DELETE(x: X) {
        val statement = Delete.deleteAllEntity(this, databaseConnection)
        addStatement(statement)
    }

    public infix fun <T : DBEntity<T>> Table<T>.DELETE(clause: WhereClause<T>) {
        val statement = Delete.delete(this, databaseConnection, clause)
        addStatement(statement)
    }

    /**
     * Select.
     */

    /**
     * Select with no any clause.
     */
    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT(x: X): FinalSelectStatement<T> =
        select(getKSerializer(), false)

    public inline infix fun <reified T :DBEntity<T>> Table<T>.SELECT_DISTINCT(x: X): FinalSelectStatement<T> =
        select(getKSerializer(), true)

    public fun <T : DBEntity<T>> Table<T>.select(serializer: KSerializer<T>, isDistinct: Boolean): FinalSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'WHERE' clause.
     */
    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(getKSerializer(), clause, false)

    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT_DISTINCT(clause: WhereClause<T>): WhereSelectStatement<T> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>> Table<T>.select(serializer: KSerializer<T>, clause: WhereClause<T>, isDistinct: Boolean): WhereSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'ORDER BY' clause.
     */
    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(getKSerializer(), clause, false)

    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT_DISTINCT(clause: OrderByClause<T>): OrderBySelectStatement<T> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>> Table<T>.select(serializer: KSerializer<T>, clause: OrderByClause<T>, isDistinct: Boolean): OrderBySelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'LIMIT' clause.
     */
    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(getKSerializer(), clause, false)

    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT_DISTINCT(clause: LimitClause<T>): LimitSelectStatement<T> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>> Table<T>.select(serializer: KSerializer<T>, clause: LimitClause<T>, isDistinct: Boolean): LimitSelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    /**
     * Receive the 'GROUP BY' clause.
     */
    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(getKSerializer(), clause, false)

    public inline infix fun <reified T : DBEntity<T>> Table<T>.SELECT_DISTINCT(clause: GroupByClause<T>): GroupBySelectStatement<T> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>> Table<T>.select(serializer: KSerializer<T>, clause: GroupByClause<T>, isDistinct: Boolean): GroupBySelectStatement<T> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }

    public inline fun <reified T : DBEntity<T>> getKSerializer(): KSerializer<T> = EmptySerializersModule().serializer()

    /**
     * The 'UNION' clause of Select.
     */

    private val unionSelectStatementGroupStack by lazy { Stack<UnionSelectStatementGroup<*>>() }

    private fun getSelectStatementGroup(): StatementContainer = unionSelectStatementGroupStack.top ?: transactionStatementsGroup ?: executeEngine

    public inline fun <T : DBEntity<T>> Table<T>.UNION(block: Table<T>.(Table<T>) -> Unit): FinalSelectStatement<T> {
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

    public inline fun <T : DBEntity<T>> Table<T>.UNION_ALL(block: Table<T>.(Table<T>) -> Unit): FinalSelectStatement<T> {
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

    public fun <T : DBEntity<T>> beginUnion() {
        unionSelectStatementGroupStack.push(UnionSelectStatementGroup<T>())
    }

    public fun <T : DBEntity<T>> createUnionSelectStatement(isUnionAll: Boolean): FinalSelectStatement<T> {
        check(unionSelectStatementGroupStack.isNotEmpty) { "Please invoke the 'beginUnion' before you invoke this function!!!" }
        return (unionSelectStatementGroupStack.top as UnionSelectStatementGroup<T>).unionStatements(isUnionAll)
    }

    public fun <T : DBEntity<T>> endUnion(selectStatement: SelectStatement<T>?) {
        unionSelectStatementGroupStack.pop()
        selectStatement?.let { addSelectStatement(it) }
    }

    /**
     * Receive the 'JOIN' clause.
     */

    public inline infix fun <T : DBEntity<T>, reified R : DBEntity<R>> Table<T>.SELECT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, false)

    public inline infix fun <T : DBEntity<T>, reified R : DBEntity<R>> Table<T>.SELECT_DISTINCT(clause: JoinClause<R>): JoinStatementWithoutCondition<R> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>, R : DBEntity<R>> Table<T>.select(serializer: KSerializer<R>, clause: JoinClause<R>, isDistinct: Boolean): JoinStatementWithoutCondition<R> {
        val container = getSelectStatementGroup()
        return Select.select(this, clause, isDistinct, serializer, databaseConnection, container, ::addSelectStatement)
    }

    /**
     * Receive the natural join clause(includes 'NATURAL LEFT OUTER JOIN' and 'NATURAL INNER JOIN').
     */

    public inline infix fun <T : DBEntity<T>, reified R : DBEntity<R>> Table<T>.SELECT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, false)

    public inline infix fun <T : DBEntity<T>, reified R : DBEntity<R>> Table<T>.SELECT_DISTINCT(clause: NaturalJoinClause<R>): JoinSelectStatement<R> =
        select(getKSerializer(), clause, true)

    public fun <T : DBEntity<T>, R : DBEntity<R>> Table<T>.select(serializer: KSerializer<R>, clause: NaturalJoinClause<R>, isDistinct: Boolean): JoinSelectStatement<R> {
        val container = getSelectStatementGroup()
        val statement = Select.select(this, clause, isDistinct, serializer, databaseConnection, container)
        addSelectStatement(statement)
        return statement
    }
}
