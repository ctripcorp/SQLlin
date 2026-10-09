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
import com.ctrip.sqllin.dsl.sql.From
import com.ctrip.sqllin.dsl.sql.Relation
import com.ctrip.sqllin.dsl.sql.clause.elementTables
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
        from: From,
        clause: WhereClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): WhereSelectStatement<R> {
        checkProjection(from, deserializer)
        return WhereSelectStatement(buildSQL(from, clause, isDistinct, deserializer), deserializer, connection, container, parametersOf(from.parameters, clause.selectCondition.parameters), null, from.tables + clause.selectCondition.tables)
    }

    /**
     * Builds a SELECT statement with ORDER BY clause.
     *
     * @return Statement that can be followed by LIMIT
     */
    fun <R> select(
        from: From,
        clause: OrderByClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): OrderBySelectStatement<R> {
        checkProjection(from, deserializer)
        return OrderBySelectStatement(buildSQL(from, clause, isDistinct, deserializer), deserializer, connection, container, parametersOf(from.parameters), null, from.tables + clause.elementTables)
    }

    /**
     * Builds a SELECT statement with LIMIT clause.
     *
     * @return Statement that can be followed by OFFSET
     */
    fun <R> select(
        from: From,
        clause: LimitClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): LimitSelectStatement<R> {
        checkProjection(from, deserializer)
        return LimitSelectStatement(buildSQL(from, clause, isDistinct, deserializer), deserializer, connection, container, parametersOf(from.parameters), null, from.tables)
    }

    /**
     * Builds a SELECT statement with GROUP BY clause.
     *
     * @return Statement that can be followed by HAVING or ORDER BY
     */
    fun <R> select(
        from: From,
        clause: GroupByClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): GroupBySelectStatement<R> {
        checkProjection(from, deserializer)
        return GroupBySelectStatement(buildSQL(from, clause, isDistinct, deserializer), deserializer, connection, container, parametersOf(from.parameters), null, from.tables + clause.elementTables)
    }

    /**
     * Builds a SELECT statement with NATURAL JOIN clause.
     *
     * Natural joins automatically match columns with the same name in both tables.
     *
     * @return Statement that can be followed by WHERE, GROUP BY, ORDER BY, or LIMIT
     */
    fun <R> select(
        table: Relation<*>,
        clause: NaturalJoinClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ) : JoinSelectStatement<R> =
        JoinSelectStatement(buildSQL(From.of(table), clause, isDistinct, deserializer), deserializer, connection, container, parametersOf(table.fromParameters, clause.parameters), null, table.readTables + clause.tables)

    /**
     * Builds a SELECT statement with JOIN clause (requires ON or USING).
     *
     * Returns an intermediate state that must be completed with either an ON or USING clause.
     *
     * @return Incomplete JOIN statement requiring condition
     */
    fun <R> select(
        table: Relation<*>,
        clause: JoinClause<R>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
        addSelectStatement: (SelectStatement<R>) -> Unit
    ) : JoinStatementWithoutCondition<R> =
        JoinStatementWithoutCondition(
            buildSQL(From.of(table), clause, isDistinct, deserializer),
            deserializer,
            connection,
            container,
            parametersOf(table.fromParameters, clause.parameters),
            table.readTables + clause.tables,
            addSelectStatement,
        )

    /**
     * Checks that [deserializer] can read rows of [from], when it reads them into a type other than the row type of a
     * single table.
     *
     * The columns a SELECT reads are the element names of [deserializer]'s descriptor, so a projection type has to
     * fit what the SELECT reads. Every property must name a column, of a single relation unless a join merges them,
     * have that column's type, and be nullable when the column can be NULL, as it is nullable or an outer join can
     * leave its relation without a matching row, as a NULL read into a non-null property would quietly become `0` or
     * `""`. A mismatch fails here, while the statement is built, rather than in SQLite or not at all.
     *
     * @throws IllegalArgumentException if the projection type doesn't fit what the SELECT reads
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun checkProjection(from: From, deserializer: DeserializationStrategy<*>) {
        val projection = deserializer.descriptor
        if (!from.isJoin && projection == from.members.single().columns)
            return
        for (index in 0 ..< projection.elementsCount)
            checkColumnProperty(from, projection, index)
    }

    /**
     * Checks that property [index] of [projection] can be read from the column of [from] it names, as described by
     * [checkProjection], and returns that column.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun checkColumnProperty(from: From, projection: SerialDescriptor, index: Int): From.Column {
        val projectionName = projection.serialName
        val name = projection.getElementName(index)
        val column = from.find(name, projectionName)
        val property = projection.getElementDescriptor(index)
        val columnType = column.descriptor.serialName.removeSuffix("?")
        val propertyType = property.serialName.removeSuffix("?")
        require(propertyType == columnType) {
            "Can't select '$projectionName' from ${from.description}: its property '$name' is a $propertyType, but the column holds a $columnType."
        }
        require(property.isNullable || !column.isNullable) {
            if (column.descriptor.isNullable)
                "Can't select '$projectionName' from ${from.description}: column '$name' is nullable, so property '$name' has to be nullable too."
            else
                "Can't select '$projectionName' from ${from.description}: column '$name' of '${column.relation.tableName}' is NULL where the outer join finds no matching row, so property '$name' has to be nullable."
        }
        return column
    }

    /**
     * Appends the columns that the properties of [projection] name, as [from] writes them: with the names of their
     * relations in a join, which [checkProjection] has checked.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun StringBuilder.appendColumns(from: From, projection: SerialDescriptor) {
        if (!from.isJoin) {
            appendDBColumnName(projection)
            return
        }
        for (index in 0 ..< projection.elementsCount) {
            if (index > 0)
                append(',')
            append(from.find(projection.getElementName(index), projection.serialName).sql)
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
    @OptIn(ExperimentalSerializationApi::class)
    fun <R> select(
        from: From,
        resultColumns: Iterable<ResultColumn<R>>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): ResultColumnSelectStatement<R> {
        val expressions = checkResultColumns(from, resultColumns, deserializer)
        val projection = deserializer.descriptor
        val sql = buildString {
            append(sqlStr)
            if (isDistinct)
                append("DISTINCT ")
            for (index in 0 ..< projection.elementsCount) {
                if (index > 0)
                    append(',')
                val name = projection.getElementName(index)
                val expression = expressions[name]
                if (expression != null) {
                    expression.appendSQL(this)
                    append(" AS ")
                    append(name)
                } else {
                    append(if (from.isJoin) from.find(name, projection.serialName).sql else name)
                }
            }
            append(" FROM ")
            append(from.sql)
        }
        val tables = from.tables + expressions.values.flatMap { it.tables }
        return ResultColumnSelectStatement(sql, deserializer, connection, container, parametersOf(from.parameters), ungroupedError(from, expressions, deserializer), tables)
    }

    /**
     * Checks that [resultColumns] fit the result type of [deserializer], and returns their expressions by the names of
     * the properties they are selected into.
     *
     * Every result column has to name a property that is serialized under its own name, which is how the result is
     * read, and no property can be given two expressions. An expression can only read the columns of what the SELECT
     * reads, and a property given one has to be nullable when the expression can be NULL in a row, or in a group of
     * GROUP BY, which an outer join makes any expression but `count` of a relation it can leave without a matching row.
     * Every other property is read from its column, and is checked as [checkProjection] does. Whether a property has to
     * be nullable because the query isn't grouped is left to [ungroupedError].
     *
     * @throws IllegalArgumentException if the result columns don't fit the result type
     */
    @OptIn(ExperimentalSerializationApi::class)
    private fun <R> checkResultColumns(
        from: From,
        resultColumns: Iterable<ResultColumn<R>>,
        deserializer: DeserializationStrategy<R>,
    ): Map<String, ClauseElement<*>> {
        val projection = deserializer.descriptor
        val projectionName = projection.serialName
        val relations = from.members.mapTo(HashSet()) { it.relation.tableName }
        val expressions = HashMap<String, ClauseElement<*>>()
        for (resultColumn in resultColumns) {
            val name = resultColumn.propertyName
            val index = projection.getElementIndex(name)
            require(index != CompositeDecoder.UNKNOWN_NAME) {
                "Can't select '$projectionName' from ${from.description}: it doesn't serialize its property '$name' under that name. The property may be @Transient or computed, or renamed with @SerialName, which a property given an expression with AS can't be."
            }
            require(name !in expressions) {
                "Can't select '$projectionName' from ${from.description}: its property '$name' is given more than one expression."
            }
            val element = resultColumn.element
            val otherTable = (element.columnTables - relations).firstOrNull()
            require(otherTable == null) {
                "Can't select '$projectionName' from ${from.description}: the expression '${element.valueName}' of property '$name' belongs to table '$otherTable'."
            }
            val isNullable = projection.getElementDescriptor(index).isNullable
            require(!element.isNullable || isNullable) {
                "Can't select '$projectionName' from ${from.description}: '${element.valueName}' can be NULL, so property '$name' has to be nullable."
            }
            require(isNullable || !element.isNullOnNoRows || element.columnTables.none { it in from.nullableTables }) {
                "Can't select '$projectionName' from ${from.description}: '${element.valueName}' is NULL where the outer join finds no matching row, so property '$name' has to be nullable."
            }
            expressions[name] = element
        }
        require(expressions.isNotEmpty()) {
            "Can't select '$projectionName' from ${from.description} with no result columns. To select columns only, use X<$projectionName>()."
        }
        for (index in 0 ..< projection.elementsCount)
            if (projection.getElementName(index) !in expressions)
                checkColumnProperty(from, projection, index)
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
        from: From,
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
        return "Can't select '${projection.serialName}' from ${from.description} without GROUP BY: an aggregate query that isn't grouped returns one row even when no rows match, in which $properties would be NULL. Make them nullable, or append GROUP BY."
    }

    private fun <T> buildSQL(
        from: From,
        clause: SelectClause<T>,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<T>,
    ): String = buildString {
        append(sqlStr)
        if (isDistinct)
            append("DISTINCT ")
        appendColumns(from, deserializer.descriptor)
        append(" FROM ")
        append(from.sql)
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
        from: From,
        isDistinct: Boolean,
        deserializer: DeserializationStrategy<R>,
        connection: DatabaseConnection,
        container: StatementContainer,
    ): FinalSelectStatement<R> {
        checkProjection(from, deserializer)
        val sql = buildString {
            append(sqlStr)
            if (isDistinct)
                append("DISTINCT ")
            appendColumns(from, deserializer.descriptor)
            append(" FROM ")
            append(from.sql)
        }
        return FinalSelectStatement(sql, deserializer, connection, container, parametersOf(from.parameters), null, from.tables, isSimple = true)
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
        return CompoundSelectStatement(sql, left.deserializer, left.connection, container, parameters.ifEmpty { null }, left.tables + right.tables)
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

/**
 * Returns the parameters of a statement made of parts with [parameters], in their order: a new list, so that a list of a
 * part, such as a derived table's, is never changed, or null for none.
 */
internal fun parametersOf(vararg parameters: List<Any?>?): MutableList<Any?>? =
    parameters.flatMapTo(ArrayList()) { it.orEmpty() }.ifEmpty { null }
