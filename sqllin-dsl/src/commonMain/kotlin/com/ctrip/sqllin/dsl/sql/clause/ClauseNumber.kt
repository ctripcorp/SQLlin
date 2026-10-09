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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.Relation
import kotlin.jvm.JvmName

/**
 * Wrapper for numeric column/function references in SQL clauses.
 *
 * Provides comparison and set operators for numeric values (Byte, Short, Int, Long, Float, Double).
 * All value-based comparisons use parameterized binding (?) to prevent SQL injection and ensure
 * proper type handling across platforms.
 *
 * Available operators:
 * - `lt`: Less than (<) - parameterized
 * - `lte`: Less than or equal (<=) - parameterized
 * - `eq`: Equals (=) - parameterized or IS NULL
 * - `neq`: Not equals (!=) - parameterized or IS NOT NULL
 * - `gt`: Greater than (>) - parameterized
 * - `gte`: Greater than or equal (>=) - parameterized
 * - `inIterable`: IN (?, ?, ...) - all values parameterized
 * - `between`: BETWEEN ? AND ? - both boundaries parameterized
 *
 * @param V The type of the element's values: `Int` for an `Int` column, `Long` for `count(*)`, `Double` for `avg(...)`
 *
 * @author Yuang Qiao
 */
public class ClauseNumber<V : Any> internal constructor(
    valueName: String,
    table: Relation<*>,
    isFunction: Boolean,
    isNullable: Boolean,
    isAggregate: Boolean,
    isNullOnNoRows: Boolean,
    columnTables: Set<String>? = null,
    tables: Set<String> = emptySet(),
    isDeterministic: Boolean = true,
    isLiteral: Boolean = false,
) : ClauseElement<V>(valueName, table, isFunction, isNullable, isAggregate, isNullOnNoRows, columnTables, tables, isDeterministic, isLiteral) {

    /**
     * Creates the element of a column, as the code generated for a table does.
     *
     * @param isNullable Whether the column is nullable
     */
    public constructor(valueName: String, table: Relation<*>, isNullable: Boolean) :
        this(valueName, table, isFunction = false, isNullable = isNullable, isAggregate = false, isNullOnNoRows = true)

    override fun toAggregate(valueName: String, table: Relation<*>): ClauseNumber<V> =
        ClauseNumber(valueName, table, isFunction = true, isNullable = isNullable, isAggregate = true, isNullOnNoRows = true, columnTables = columnTables, tables = tables, isDeterministic = isDeterministic)

    override fun derive(valueName: String, traits: Traits): ClauseNumber<V> =
        ClauseNumber(valueName, table, isFunction = true, isNullable = traits.isNullable, isAggregate = traits.isAggregate, isNullOnNoRows = traits.isNullOnNoRows, columnTables = traits.columnTables, tables = traits.tables, isDeterministic = traits.isDeterministic)

    override fun literalOf(value: V): ClauseNumber<V> =
        ClauseNumber(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

    /**
     * Less than (<) comparison using parameterized binding.
     *
     * Generates: `column < ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun lt(number: Number): SelectCondition = appendNumber("<?", number)

    /** Less than (<) - compare against another column/function */
    internal infix fun lt(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber("<", clauseNumber)

    /**
     * Less than or equal (<=) comparison using parameterized binding.
     *
     * Generates: `column <= ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun lte(number: Number): SelectCondition = appendNumber("<=?", number)

    /** Less than or equal (<=) - compare against another column/function */
    internal infix fun lte(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber("<=", clauseNumber)

    /**
     * Equals (=) comparison using parameterized binding, or IS NULL for null values.
     *
     * Generates: `column = ?` or `column IS NULL`
     *
     * @param number The value to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound parameter
     */
    internal infix fun eq(number: Number?): SelectCondition = appendNullableNumber("=", " IS NULL", number)

    /** Equals (=) - compare against another column/function */
    internal infix fun eq(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber("=", clauseNumber)

    /**
     * Not equals (!=) comparison using parameterized binding, or IS NOT NULL for null values.
     *
     * Generates: `column != ?` or `column IS NOT NULL`
     *
     * @param number The value to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound parameter
     */
    internal infix fun neq(number: Number?): SelectCondition = appendNullableNumber("!=", " IS NOT NULL", number)

    /** Not equals (!=) - compare against another column/function */
    internal infix fun neq(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber("!=", clauseNumber)

    /**
     * Greater than (>) comparison using parameterized binding.
     *
     * Generates: `column > ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun gt(number: Number): SelectCondition = appendNumber(">?", number)

    /** Greater than (>) - compare against another column/function */
    internal infix fun gt(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber(">", clauseNumber)

    /**
     * Greater than or equal (>=) comparison using parameterized binding.
     *
     * Generates: `column >= ?`
     *
     * @param number The value to compare against
     * @return SelectCondition with placeholder and bound parameter
     */
    internal infix fun gte(number: Number): SelectCondition = appendNumber(">=?", number)

    /** Greater than or equal (>=) - compare against another column/function */
    internal infix fun gte(clauseNumber: ClauseNumber<*>): SelectCondition = appendClauseNumber(">=", clauseNumber)

    /**
     * IN operator - checks if value is in the given set.
     *
     * Uses parameterized binding for all values to prevent SQL injection.
     * Generates: `column IN (?, ?, ?, ...)`
     *
     * @param numbers Non-empty iterable of numbers to check against
     * @return SelectCondition with placeholders and bound parameters
     * @throws IllegalArgumentException if numbers is empty
     */
    internal infix fun inIterable(numbers: Iterable<Number>): SelectCondition {
        val parameters = numbers.toMutableList<Any?>()
        require(parameters.isNotEmpty()) { "Param 'numbers' must not be empty!!!" }
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" IN (")

            append('?')
            repeat(parameters.size - 1) {
                append(",?")
            }
            append(')')
        }
        return condition(sql, parameters)
    }

    /**
     * BETWEEN operator - checks if value is within a range (inclusive).
     *
     * Uses parameterized binding for both range boundaries.
     * Generates: `column BETWEEN ? AND ?`
     *
     * @param range The inclusive range to check (start..end)
     * @return SelectCondition with placeholders and bound parameters
     */
    internal infix fun between(range: LongRange): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(" BETWEEN ? AND ?")
        }
        return condition(sql, mutableListOf(range.first, range.last))
    }

    private fun appendNumber(symbol: String, number: Number): SelectCondition {
        val sql = buildString {
            if (!isFunction) {
                append(table.tableName)
                append('.')
            }
            append(valueName)
            append(symbol)
        }
        return condition(sql, mutableListOf(number))
    }

    private fun appendNullableNumber(notNullSymbol: String, nullSymbol: String, number: Number?): SelectCondition {
        val builder = StringBuilder()
        if (!isFunction) {
            builder.append(table.tableName)
            builder.append('.')
        }
        builder.append(valueName)
        val parameters = if (number == null) {
            builder.append(nullSymbol)
            null
        } else {
            builder.append(notNullSymbol)
            builder.append('?')
            mutableListOf<Any?>(number)
        }
        return condition(builder.toString(), parameters)
    }

    private fun appendClauseNumber(symbol: String, clauseNumber: ClauseNumber<*>): SelectCondition {
        val sql = buildString {
            appendSQL(this)
            append(symbol)
            clauseNumber.appendSQL(this)
        }
        return condition(sql, null, clauseNumber)
    }

    // ========== Arithmetic ==========
    //
    // An operator of two numbers of the same type gives a number of that type, as `visits + 1` of an `Int` column is an
    // `Int`; CAST converts a number to another type, to compute with numbers of different types. SQLite computes as
    // Kotlin does, except that it turns an integer that overflows into a real number, rather than wrapping it around,
    // and that it gives NULL for a division by zero, rather than throwing.

    /**
     * Addition: `(this + other)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun plus(other: ClauseNumber<V>): ClauseNumber<V> = arithmetic("+", other)

    /**
     * Addition of a value: `(this + value)`, as in `visits + 1`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun plus(value: V): ClauseNumber<V> = arithmetic("+", literalOf(value))

    /**
     * Subtraction: `(this - other)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun minus(other: ClauseNumber<V>): ClauseNumber<V> = arithmetic("-", other)

    /**
     * Subtraction of a value: `(this - value)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun minus(value: V): ClauseNumber<V> = arithmetic("-", literalOf(value))

    /**
     * Multiplication: `(this * other)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun times(other: ClauseNumber<V>): ClauseNumber<V> = arithmetic("*", other)

    /**
     * Multiplication by a value: `(this * value)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun times(value: V): ClauseNumber<V> = arithmetic("*", literalOf(value))

    /**
     * Division: `(this / other)`, which divides integers as Kotlin does, `7 / 2` being `3`. SQLite gives NULL for a
     * division by zero, so the result can be NULL.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun div(other: ClauseNumber<V>): ClauseNumber<V> = arithmetic("/", other, isNullable = true)

    /**
     * Division by a value: `(this / value)`, which can only be NULL where this element can, unless [value] is zero.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun div(value: V): ClauseNumber<V> {
        val divisor = literalOf(value)
        return arithmetic("/", divisor, isNullable = isNullable || divisor.valueName.trim('(', ')').toDouble() == 0.0)
    }

    /**
     * Negation: `(-this)`.
     */
    @ExperimentalDSLDatabaseAPI
    public operator fun unaryMinus(): ClauseNumber<V> = derive("(-$sql)", strictTraits(listOf(this)))

    /**
     * The remainder of the division of integers, `(this % other)`, which, as in Kotlin, has the sign of this element.
     * The remainder operators are only given to integer types, as SQLite converts real numbers to integers for it.
     */
    internal fun remainder(other: ClauseElement<*>): ClauseNumber<V> {
        val isNonZero = other.isLiteral && other.valueName.trim('(', ')').toDouble() != 0.0
        return arithmetic("%", other, isNullable = isNullable || !isNonZero || other.isNullable)
    }

    /**
     * A bitwise operator, `(this operator other)`, which is NULL where either is.
     */
    internal fun bitwise(operator: String, other: ClauseElement<*>): ClauseNumber<V> = arithmetic(operator, other)

    /**
     * The bitwise complement, `(~this)`.
     */
    internal fun complement(): ClauseNumber<V> = derive("(~$sql)", strictTraits(listOf(this)))

    private fun arithmetic(operator: String, other: ClauseElement<*>, isNullable: Boolean = this.isNullable || other.isNullable): ClauseNumber<V> =
        derive("($sql $operator ${other.sql})", strictTraits(listOf(this, other), isNullable))

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseNumber<*>)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}

// The remainder of integers: SQLite converts real numbers to integers for `%`, so `5.5 % 2` is `1.0` there, but `1.5` in
// Kotlin, and the operator is only given to integer types.

/** The remainder of the division of `Byte`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remByte")
public operator fun ClauseNumber<Byte>.rem(other: ClauseNumber<Byte>): ClauseNumber<Byte> = remainder(other)

/** The remainder of the division by a `Byte`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remByteValue")
public operator fun ClauseNumber<Byte>.rem(value: Byte): ClauseNumber<Byte> = remainder(literalOf(value))

/** The remainder of the division of `Short`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remShort")
public operator fun ClauseNumber<Short>.rem(other: ClauseNumber<Short>): ClauseNumber<Short> = remainder(other)

/** The remainder of the division by a `Short`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remShortValue")
public operator fun ClauseNumber<Short>.rem(value: Short): ClauseNumber<Short> = remainder(literalOf(value))

/** The remainder of the division of `Int`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remInt")
public operator fun ClauseNumber<Int>.rem(other: ClauseNumber<Int>): ClauseNumber<Int> = remainder(other)

/** The remainder of the division by a `Int`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remIntValue")
public operator fun ClauseNumber<Int>.rem(value: Int): ClauseNumber<Int> = remainder(literalOf(value))

/** The remainder of the division of `Long`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remLong")
public operator fun ClauseNumber<Long>.rem(other: ClauseNumber<Long>): ClauseNumber<Long> = remainder(other)

/** The remainder of the division by a `Long`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remLongValue")
public operator fun ClauseNumber<Long>.rem(value: Long): ClauseNumber<Long> = remainder(literalOf(value))

/** The remainder of the division of `UByte`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUByte")
public operator fun ClauseNumber<UByte>.rem(other: ClauseNumber<UByte>): ClauseNumber<UByte> = remainder(other)

/** The remainder of the division by a `UByte`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUByteValue")
public operator fun ClauseNumber<UByte>.rem(value: UByte): ClauseNumber<UByte> = remainder(literalOf(value))

/** The remainder of the division of `UShort`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUShort")
public operator fun ClauseNumber<UShort>.rem(other: ClauseNumber<UShort>): ClauseNumber<UShort> = remainder(other)

/** The remainder of the division by a `UShort`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUShortValue")
public operator fun ClauseNumber<UShort>.rem(value: UShort): ClauseNumber<UShort> = remainder(literalOf(value))

/** The remainder of the division of `UInt`s: `(this % other)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUInt")
public operator fun ClauseNumber<UInt>.rem(other: ClauseNumber<UInt>): ClauseNumber<UInt> = remainder(other)

/** The remainder of the division by a `UInt`: `(this % value)`, NULL for a division by zero. */
@ExperimentalDSLDatabaseAPI
@JvmName("remUIntValue")
public operator fun ClauseNumber<UInt>.rem(value: UInt): ClauseNumber<UInt> = remainder(literalOf(value))

// Bitwise operators, named as Kotlin names them, of `Int` and `Long`, as SQLite computes them with 64-bit integers.

/** Bitwise AND of `Int`s: `(this & other)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("andInt")
public infix fun ClauseNumber<Int>.and(other: ClauseNumber<Int>): ClauseNumber<Int> = bitwise("&", other)

/** Bitwise AND with a `Int`: `(this & value)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("andIntValue")
public infix fun ClauseNumber<Int>.and(value: Int): ClauseNumber<Int> = bitwise("&", literalOf(value))

/** Bitwise OR of `Int`s: `(this | other)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("orInt")
public infix fun ClauseNumber<Int>.or(other: ClauseNumber<Int>): ClauseNumber<Int> = bitwise("|", other)

/** Bitwise OR with a `Int`: `(this | value)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("orIntValue")
public infix fun ClauseNumber<Int>.or(value: Int): ClauseNumber<Int> = bitwise("|", literalOf(value))

/** Shift left of a `Int` by [bits]: `(this << bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shlInt")
public infix fun ClauseNumber<Int>.shl(bits: ClauseNumber<Int>): ClauseNumber<Int> = bitwise("<<", bits)

/** Shift left of a `Int` by the value [bits]: `(this << bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shlIntValue")
public infix fun ClauseNumber<Int>.shl(bits: Int): ClauseNumber<Int> = bitwise("<<", literal(bits))

/** Shift right, keeping the sign, of a `Int` by [bits]: `(this >> bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shrInt")
public infix fun ClauseNumber<Int>.shr(bits: ClauseNumber<Int>): ClauseNumber<Int> = bitwise(">>", bits)

/** Shift right, keeping the sign, of a `Int` by the value [bits]: `(this >> bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shrIntValue")
public infix fun ClauseNumber<Int>.shr(bits: Int): ClauseNumber<Int> = bitwise(">>", literal(bits))

/** The bitwise complement of a `Int`: `(~this)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("invInt")
public fun ClauseNumber<Int>.inv(): ClauseNumber<Int> = complement()

/** Bitwise AND of `Long`s: `(this & other)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("andLong")
public infix fun ClauseNumber<Long>.and(other: ClauseNumber<Long>): ClauseNumber<Long> = bitwise("&", other)

/** Bitwise AND with a `Long`: `(this & value)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("andLongValue")
public infix fun ClauseNumber<Long>.and(value: Long): ClauseNumber<Long> = bitwise("&", literalOf(value))

/** Bitwise OR of `Long`s: `(this | other)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("orLong")
public infix fun ClauseNumber<Long>.or(other: ClauseNumber<Long>): ClauseNumber<Long> = bitwise("|", other)

/** Bitwise OR with a `Long`: `(this | value)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("orLongValue")
public infix fun ClauseNumber<Long>.or(value: Long): ClauseNumber<Long> = bitwise("|", literalOf(value))

/** Shift left of a `Long` by [bits]: `(this << bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shlLong")
public infix fun ClauseNumber<Long>.shl(bits: ClauseNumber<Int>): ClauseNumber<Long> = bitwise("<<", bits)

/** Shift left of a `Long` by the value [bits]: `(this << bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shlLongValue")
public infix fun ClauseNumber<Long>.shl(bits: Int): ClauseNumber<Long> = bitwise("<<", literal(bits))

/** Shift right, keeping the sign, of a `Long` by [bits]: `(this >> bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shrLong")
public infix fun ClauseNumber<Long>.shr(bits: ClauseNumber<Int>): ClauseNumber<Long> = bitwise(">>", bits)

/** Shift right, keeping the sign, of a `Long` by the value [bits]: `(this >> bits)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("shrLongValue")
public infix fun ClauseNumber<Long>.shr(bits: Int): ClauseNumber<Long> = bitwise(">>", literal(bits))

/** The bitwise complement of a `Long`: `(~this)`. */
@ExperimentalDSLDatabaseAPI
@JvmName("invLong")
public fun ClauseNumber<Long>.inv(): ClauseNumber<Long> = complement()
