/*
 * Copyright (C) 2026 Ctrip.com.
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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.Relation
import com.ctrip.sqllin.dsl.sql.compiler.storedValueOf
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy

/**
 * Wrapper for the column references of a type that SQLite doesn't store as it is, whose serializer converts its values
 * to those of a column: a number, a string, a Boolean or a `ByteArray`.
 *
 * A property gets this element when its serializer isn't the one SQLlin stores its type with by default: a type with a
 * built-in serializer of kotlinx.serialization, as `Uuid`, `Instant` or `Duration`; a value class; a type whose class
 * declares its serializer, as `@Serializable(with = ...)` does; or a property, or a type use, annotated with
 * `@Serializable(with = ...)`, of any type, an enum or a `String` included. The serializer that writes the property is
 * the converter:
 * ```kotlin
 * object EpochMillisSerializer : KSerializer<Instant> {
 *     override val descriptor = PrimitiveSerialDescriptor("EpochMillis", PrimitiveKind.LONG)
 *     override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeLong(value.toEpochMilliseconds())
 *     override fun deserialize(decoder: Decoder) = Instant.fromEpochMilliseconds(decoder.decodeLong())
 * }
 *
 * @Serializable
 * @DBRow
 * data class Event(
 *     @PrimaryKey val id: Uuid,
 *     @Serializable(with = EpochMillisSerializer::class) val startsAt: Instant,
 * )
 *
 * EventTable SELECT WHERE(EventTable.startsAt GTE since) ORDER_BY (EventTable.startsAt to ASC)
 * ```
 *
 * The column has the type that the serializer stores, `BIGINT` for `startsAt`, and `TEXT` for `id`, as the built-in
 * serializer of `Uuid` writes a string. A value given to a condition, or assigned in `SET {}`, is converted by the same
 * serializer, so that it is compared with, or stored as, what the column holds.
 *
 * A comparison other than an equality, as `LT` or `BETWEEN`, and ORDER BY compare the stored values, which are in the
 * order of the Kotlin values only if the serializer keeps it: the built-in serializer of `kotlin.time.Instant` writes
 * an ISO-8601 string, whose fraction of a second, if any, makes `2024-01-01T00:00:00.5Z` come before
 * `2024-01-01T00:00:00Z`; store an instant as the number of its milliseconds, as `EpochMillisSerializer` does, to compare
 * and order it.
 *
 * @param V The type of the element's values, as the property's type, not counting NULL
 * @property serializer The serializer that converts the values, the property's
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public class ClauseConverted<V : Any> internal constructor(
    valueName: String,
    table: Relation<*>,
    isFunction: Boolean,
    isNullable: Boolean,
    isAggregate: Boolean,
    isNullOnNoRows: Boolean,
    internal val serializer: SerializationStrategy<V>,
    columnTables: Set<String>? = null,
    tables: Set<String> = emptySet(),
    isDeterministic: Boolean = true,
    isLiteral: Boolean = false,
) : ClauseElement<V>(valueName, table, isFunction, isNullable, isAggregate, isNullOnNoRows, columnTables, tables, isDeterministic, isLiteral) {

    /**
     * The column [valueName] of [table], whose values [serializer] converts, which has to be the serializer that
     * kotlinx.serialization writes the column's property with, so that a condition compares the values the column
     * holds.
     *
     * @throws IllegalArgumentException if [serializer] encodes another type, or as another kind of value, than the
     * serializer of the property, as when sqllin-processor took another serializer than kotlinx.serialization did
     */
    @OptIn(ExperimentalSerializationApi::class)
    public constructor(valueName: String, table: Relation<*>, isNullable: Boolean, serializer: SerializationStrategy<V>) :
        this(valueName, table, isFunction = false, isNullable = isNullable, isAggregate = false, isNullOnNoRows = true, serializer = serializer) {
        val row = table.kSerializer().descriptor
        val property = row.getElementDescriptor(row.getElementIndex(valueName))
        val converter = serializer.descriptor
        require(property.serialName.removeSuffix("?") == converter.serialName.removeSuffix("?") && property.kind == converter.kind) {
            "The column '$valueName' of '${table.tableName}' is written by the serializer of '${property.serialName.removeSuffix("?")}', " +
                "but its conditions would convert values with that of '${converter.serialName.removeSuffix("?")}', so that they wouldn't compare what it holds. " +
                "Declare the serializer of the property with @Serializable(with = ...) on the property."
        }
    }

    override fun toAggregate(valueName: String, table: Relation<*>): ClauseConverted<V> =
        ClauseConverted(valueName, table, isFunction = true, isNullable = isNullable, isAggregate = true, isNullOnNoRows = true, serializer = serializer, columnTables = columnTables, tables = tables, isDeterministic = isDeterministic)

    override fun derive(valueName: String, traits: Traits): ClauseConverted<V> =
        ClauseConverted(valueName, table, isFunction = true, isNullable = traits.isNullable, isAggregate = traits.isAggregate, isNullOnNoRows = traits.isNullOnNoRows, serializer = serializer, columnTables = traits.columnTables, tables = traits.tables, isDeterministic = traits.isDeterministic)

    override fun literalOf(value: V): ClauseConverted<V> = convertedLiteral(value, serializer)

    override fun rebind(relation: Relation<*>): ClauseConverted<V> =
        ClauseConverted(valueName, relation, isFunction = false, isNullable = isNullable, isAggregate = false, isNullOnNoRows = true, serializer = serializer)

    /**
     * Returns [value] as the column stores it, which a condition compares the column with.
     */
    internal fun stored(value: V): Any? = storedValueOf(serializer, value)

    internal infix fun eq(value: V?): SelectCondition = appendNullable("=", " IS NULL", value)

    internal infix fun eq(other: ClauseConverted<V>): SelectCondition = appendElement("=", other)

    internal infix fun neq(value: V?): SelectCondition = appendNullable("!=", " IS NOT NULL", value)

    internal infix fun neq(other: ClauseConverted<V>): SelectCondition = appendElement("!=", other)

    internal infix fun lt(value: V): SelectCondition = appendValue("<?", value)

    internal infix fun lt(other: ClauseConverted<V>): SelectCondition = appendElement("<", other)

    internal infix fun lte(value: V): SelectCondition = appendValue("<=?", value)

    internal infix fun lte(other: ClauseConverted<V>): SelectCondition = appendElement("<=", other)

    internal infix fun gt(value: V): SelectCondition = appendValue(">?", value)

    internal infix fun gt(other: ClauseConverted<V>): SelectCondition = appendElement(">", other)

    internal infix fun gte(value: V): SelectCondition = appendValue(">=?", value)

    internal infix fun gte(other: ClauseConverted<V>): SelectCondition = appendElement(">=", other)

    internal infix fun inIterable(values: Iterable<V>): SelectCondition {
        val parameters = values.mapTo(ArrayList<Any?>()) { stored(it) }
        require(parameters.isNotEmpty()) { "Param 'values' must not be empty!!!" }
        val sql = buildString {
            appendSQL(this)
            append(" IN (?")
            repeat(parameters.size - 1) {
                append(",?")
            }
            append(')')
        }
        return condition(sql, parameters)
    }

    internal infix fun between(range: Pair<V, V>): SelectCondition =
        condition("$sql BETWEEN ? AND ?", mutableListOf(stored(range.first), stored(range.second)))

    /**
     * The condition of this element and [value] converted: `IS NULL` or `IS NOT NULL` for null, or a value that the
     * serializer stores as NULL.
     */
    private fun appendNullable(notNullSymbol: String, nullSymbol: String, value: V?): SelectCondition {
        val stored = value?.let { stored(it) }
        return if (stored == null)
            condition("$sql$nullSymbol", null)
        else
            condition("$sql$notNullSymbol?", mutableListOf(stored))
    }

    private fun appendValue(symbol: String, value: V): SelectCondition =
        condition("$sql$symbol", mutableListOf(stored(value)))

    private fun appendElement(symbol: String, other: ClauseConverted<V>): SelectCondition =
        condition("$sql $symbol ${other.sql}", null, other)

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseConverted<*>)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}

/**
 * A literal value of a type that [serializer] converts, written as the value it stores, as the literal of a column of
 * that type in `CASE`, or a value to set it to with `SET(listOf(literal(now, EpochMillisSerializer) AS Event::startsAt))`.
 * Give it the serializer of the column, so that it is stored, and compared, as the column's values are.
 */
@ExperimentalDSLDatabaseAPI
public fun <V : Any> literal(value: V, serializer: SerializationStrategy<V>): ClauseConverted<V> = convertedLiteral(value, serializer)

@OptIn(ExperimentalDSLDatabaseAPI::class)
private fun <V : Any> convertedLiteral(value: V, serializer: SerializationStrategy<V>): ClauseConverted<V> {
    val stored = storedValueOf(serializer, value)
    val literal = if (stored == null) "NULL" else expressionLiteral(stored)
    return ClauseConverted(literal, ExpressionRelation, isFunction = true, isNullable = stored == null, isAggregate = false, isNullOnNoRows = stored == null, serializer = serializer, columnTables = emptySet(), isLiteral = true)
}
