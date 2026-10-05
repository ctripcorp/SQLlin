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
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.clause.*
import com.ctrip.sqllin.dsl.sql.compiler.appendDBColumnName
import com.ctrip.sqllin.dsl.sql.statement.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder

/**
 * SELECT operation builder.
 *
 * Constructs SELECT statements by combining table information with clauses (WHERE, ORDER BY,
 * LIMIT, GROUP BY, JOIN) or result columns. Creates the appropriate statement type based on which clauses are
 * initially provided, enforcing compile-time clause ordering through the statement hierarchy.
 *
 * @author Yuang Qiao
 */
internal object Select : Operation {

    override val sqlStr: String
        get() = "SELECT "

    /**
     * Builds a SELECT statement with WHERE clause.
     *
     * @return Statement that can be followed by GROUP BY, ORDER BY, or LIMIT
     */
    fun <R> select(
        table: Table<*>,
        clause: WhereClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): WhereSelectStatement<R> {
        checkProjection(table, deserializer)
        return WhereSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container, clause.selectCondition.parameters, null)
    }

    /**
     * Builds a SELECT statement with ORDER BY clause.
     *
     * @return Statement that can be followed by LIMIT
     */
    fun <R> select(
        table: Table<*>,
        clause: OrderByClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): OrderBySelectStatement<R> {
        checkProjection(table, deserializer)
        return OrderBySelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container, null, null)
    }

    /**
     * Builds a SELECT statement with LIMIT clause.
     *
     * @return Statement that can be followed by OFFSET
     */
    fun <R> select(
        table: Table<*>,
        clause: LimitClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): LimitSelectStatement<R> {
        checkProjection(table, deserializer)
        return LimitSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container, null, null)
    }

    /**
     * Builds a SELECT statement with GROUP BY clause.
     *
     * @return Statement that can be followed by HAVING or ORDER BY
     */
    fun <R> select(
        table: Table<*>,
        clause: GroupByClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): GroupBySelectStatement<R> {
        checkProjection(table, deserializer)
        return GroupBySelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container, null, null)
    }

    /**
     * Builds a SELECT statement with NATURAL JOIN clause.
     *
     * Natural joins automatically match columns with the same name in both tables.
     *
     * @return Statement that can be followed by WHERE, GROUP BY, ORDER BY, or LIMIT
     */
    fun <R> select(
        table: Table<*>,
        clause: NaturalJoinClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ) : JoinSelectStatement<R> =
        JoinSelectStatement(buildSQL(table, clause, isDistinct, deserializer), deserializer, connection, container, null, null)

    /**
     * Builds a SELECT statement with JOIN clause (requires ON or USING).
     *
     * Returns an intermediate state that must be completed with either an ON or USING clause.
     *
     * @return Incomplete JOIN statement requiring condition
     */
    fun <R> select(
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

    /**
     * Checks that [deserializer] can read rows of [table], when it reads them into a type other than the table's
     * own row type.
     *
     * The columns a SELECT reads are the element names of [deserializer]'s descriptor, so a projection type has to
     * fit the table. Every property must name a column, have that column's type, and be nullable when the column is,
     * as a NULL read into a non-null property would quietly become `0` or `""`. A mismatch fails here, while the
     * statement is built, rather than in SQLite or not at all.
     *
     * @throws IllegalArgumentException if the projection type doesn't fit the table
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun checkProjection(table: Table<*>, deserializer: DeserializationStrategy<*>) {
        val columns = table.kSerializer().descriptor
        val projection = deserializer.descriptor
        if (projection == columns)
            return
        for (index in 0 ..< projection.elementsCount)
            checkColumnProperty(table, columns, projection, index)
    }

    /**
     * Checks that property [index] of [projection] can be read from the column of [table] it names, as described by
     * [checkProjection].
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun checkColumnProperty(table: Table<*>, columns: SerialDescriptor, projection: SerialDescriptor, index: Int) {
        val projectionName = projection.serialName
        val name = projection.getElementName(index)
        val columnIndex = columns.getElementIndex(name)
        require(columnIndex != CompositeDecoder.UNKNOWN_NAME) {
            "Can't select '$projectionName' from table '${table.tableName}': its property '$name' isn't a column of that table."
        }
        val column = columns.getElementDescriptor(columnIndex)
        val property = projection.getElementDescriptor(index)
        val columnType = column.serialName.removeSuffix("?")
        val propertyType = property.serialName.removeSuffix("?")
        require(propertyType == columnType) {
            "Can't select '$projectionName' from table '${table.tableName}': its property '$name' is a $propertyType, but the column holds a $columnType."
        }
        require(property.isNullable || !column.isNullable) {
            "Can't select '$projectionName' from table '${table.tableName}': column '$name' is nullable, so property '$name' has to be nullable too."
        }
    }

    /**
     * Builds a SELECT statement with result columns: expressions selected into properties of the result type with
     * `AS`, while every other property is read from its column.
     *
     * Generates SQL in the format: `SELECT column, expression AS property, ... FROM table`, in the order of the result
     * type's properties.
     *
     * @return Statement that can be followed by WHERE, GROUP BY, ORDER BY, or LIMIT
     */
    fun <R> select(
        table: Table<*>,
        resultColumns: Iterable<ResultColumn<R>>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): ResultColumnSelectStatement<R> {
        val expressions = checkResultColumns(table, resultColumns, deserializer)
        val projection = deserializer.descriptor
        val sql = buildString {
            append(sqlStr)
            if (isDistinct)
                append("DISTINCT ")
            for (index in 0 ..< projection.elementsCount) {
                if (index > 0)
                    append(',')
                val name = projection.getElementName(index)
                expressions[name]?.let {
                    append(it.valueName)
                    append(" AS ")
                }
                append(name)
            }
            append(" FROM ")
            append(table.tableName)
        }
        return ResultColumnSelectStatement(sql, deserializer, connection, container, null, ungroupedError(table, expressions, deserializer))
    }

    /**
     * Checks that [resultColumns] fit the result type of [deserializer], and returns their expressions by the names of
     * the properties they are selected into.
     *
     * Every result column has to name a property that is serialized under its own name, which is how the result is
     * read, and no property can be given two expressions. A property given an expression has to be nullable when the
     * expression can be NULL in a row, or in a group of GROUP BY. Every other property is read from its column, and
     * is checked as [checkProjection] does. Whether a property has to be nullable because the query isn't grouped is
     * left to [ungroupedError].
     *
     * @throws IllegalArgumentException if the result columns don't fit the result type
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun <R> checkResultColumns(
        table: Table<*>,
        resultColumns: Iterable<ResultColumn<R>>,
        deserializer: DeserializationStrategy<R>,
    ): Map<String, ClauseElement<*>> {
        val projection = deserializer.descriptor
        val projectionName = projection.serialName
        val expressions = HashMap<String, ClauseElement<*>>()
        for (resultColumn in resultColumns) {
            val name = resultColumn.propertyName
            val index = projection.getElementIndex(name)
            require(index != CompositeDecoder.UNKNOWN_NAME) {
                "Can't select '$projectionName' from table '${table.tableName}': it doesn't serialize its property '$name' under that name. The property may be @Transient or computed, or renamed with @SerialName, which a property given an expression with AS can't be."
            }
            require(name !in expressions) {
                "Can't select '$projectionName' from table '${table.tableName}': its property '$name' is given more than one expression."
            }
            val element = resultColumn.element
            require(element.table.tableName == table.tableName) {
                "Can't select '$projectionName' from table '${table.tableName}': the expression '${element.valueName}' of property '$name' belongs to table '${element.table.tableName}'."
            }
            require(!element.isNullable || projection.getElementDescriptor(index).isNullable) {
                "Can't select '$projectionName' from table '${table.tableName}': '${element.valueName}' can be NULL, so property '$name' has to be nullable."
            }
            expressions[name] = element
        }
        require(expressions.isNotEmpty()) {
            "Can't select '$projectionName' from table '${table.tableName}' with no result columns. To select columns only, use X<$projectionName>()."
        }
        val columns = table.kSerializer().descriptor
        for (index in 0 ..< projection.elementsCount)
            if (projection.getElementName(index) !in expressions)
                checkColumnProperty(table, columns, projection, index)
        return expressions
    }

    /**
     * Returns the error to report if the statement isn't grouped with GROUP BY, or null if it can do without.
     *
     * A query that selects an aggregate function is an aggregate query, and without GROUP BY, SQLite returns one row
     * for it even when no rows match. In that row, every column and every aggregate function except `count` is NULL,
     * so the properties that hold them have to be nullable, unless the query is grouped: then each group has rows.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun ungroupedError(
        table: Table<*>,
        expressions: Map<String, ClauseElement<*>>,
        deserializer: DeserializationStrategy<*>,
    ): String? {
        if (expressions.values.none { it.isAggregate })
            return null
        val projection = deserializer.descriptor
        val nonNullProperties = (0 ..< projection.elementsCount).filter { index ->
            !projection.getElementDescriptor(index).isNullable &&
                expressions[projection.getElementName(index)]?.isNullOnNoRows != false
        }.map { projection.getElementName(it) }
        if (nonNullProperties.isEmpty())
            return null
        val properties = nonNullProperties.joinToString { "'$it'" }
        return "Can't select '${projection.serialName}' from table '${table.tableName}' without GROUP BY: an aggregate query that isn't grouped returns one row even when no rows match, in which $properties would be NULL. Make them nullable, or append GROUP BY."
    }

    private fun <T> buildSQL(
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

    /**
     * Builds a simple SELECT statement without any clauses.
     *
     * Generates SQL in the format: `SELECT columns FROM table`
     *
     * @return Final SELECT statement ready for execution
     */
    fun <R> select(
        table: Table<*>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): FinalSelectStatement<R> {
        checkProjection(table, deserializer)
        val sql = buildString {
            append(sqlStr)
            if (isDistinct)
                append("DISTINCT ")
            appendDBColumnName(deserializer.descriptor)
            append(" FROM ")
            append(table.tableName)
        }
        return FinalSelectStatement(sql, deserializer, connection, container, null, null, isSimple = true)
    }

    /**
     * Builds a compound SELECT statement: [left] and [right] combined with [operator], which is ` UNION `,
     * ` UNION ALL `, ` INTERSECT ` or ` EXCEPT `.
     *
     * SQLite evaluates a compound from left to right, and only allows ORDER BY and LIMIT after its last member, where
     * they apply to the whole compound. So a member is written as a subquery, `SELECT * FROM (...)`, when it would
     * otherwise mean something else: when it ends with ORDER BY or LIMIT, which then apply to it alone, and when it
     * is itself a compound on the right, as in `a UNION (b UNION ALL c)`, which then is combined as a whole. A
     * compound on the left needs no subquery, as SQLite evaluates it first anyway.
     *
     * @return Statement that can be followed by another compound operator, ORDER BY or LIMIT
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun <R> compound(
        left: SelectStatement<R>,
        operator: String,
        right: SelectStatement<R>,
        container: StatementContainer,
    ): CompoundSelectStatement<R> {
        val sql = buildString {
            appendMember(left, isRight = false)
            append(operator)
            appendMember(right, isRight = true)
        }
        val parameters = ArrayList<Any?>().apply {
            left.parameters?.let { addAll(it) }
            right.parameters?.let { addAll(it) }
        }
        return CompoundSelectStatement(sql, left.deserializer, left.connection, container, parameters.ifEmpty { null })
    }

    @OptIn(ExperimentalDSLDatabaseAPI::class)
    private fun StringBuilder.appendMember(member: SelectStatement<*>, isRight: Boolean) {
        val isSubquery = when (member) {
            is CompoundSelectStatement -> isRight
            is OrderBySelectStatement, is LimitSelectStatement -> true
            is FinalSelectStatement -> !member.isSimple
            else -> false
        }
        if (isSubquery) {
            append("SELECT * FROM (")
            append(member.sqlStr)
            append(')')
        } else {
            append(member.sqlStr)
        }
    }
}
