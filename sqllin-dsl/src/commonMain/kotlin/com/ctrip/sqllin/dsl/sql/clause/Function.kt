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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.FunctionDslMaker
import com.ctrip.sqllin.dsl.sql.Relation
import com.ctrip.sqllin.dsl.sql.X
import kotlin.jvm.JvmName

/**
 * SQLite aggregate and scalar functions for use in SELECT clauses.
 *
 * These functions can be used in WHERE, HAVING, ORDER BY, and SELECT expressions.
 * All functions return [ClauseElement] wrappers that can be compared with operators,
 * and selected into a property of a result type with [AS].
 *
 * Each function's result has the type of the values SQLite returns for it: `count` and `length` give a `Long`,
 * `avg` and `round` a `Double`, `sum` a `Long` or a `Double` as its input holds integers or reals, and `max`, `min`
 * and `abs` the type of their input. Whether the result can be NULL follows SQLite too: an aggregate function other
 * than `count` is NULL for a group whose values are all NULL, and, without GROUP BY, when no rows match.
 *
 * @author Yuang Qiao
 */

/** An aggregate function of [element] with values of type [V]: NULL when all its values are, or no rows match. */
private fun <V : Any> Relation<*>.numberAggregate(valueName: String, element: ClauseElement<*>): ClauseNumber<V> =
    ClauseNumber(valueName, this, isFunction = true, isNullable = element.isNullable, isAggregate = true, isNullOnNoRows = true, columnTables = element.columnTables)

/** A scalar function of [element] with values of type [V]: NULL when [element] is. */
private fun <V : Any> Relation<*>.numberFunction(valueName: String, element: ClauseElement<*>): ClauseNumber<V> =
    ClauseNumber(valueName, this, isFunction = true, isNullable = element.isNullable, isAggregate = element.isAggregate, isNullOnNoRows = element.isNullOnNoRows, columnTables = element.columnTables)

/** A scalar function of [element] with `String` values: NULL when [element] is. */
private fun Relation<*>.stringFunction(valueName: String, element: ClauseElement<*>): ClauseString<String> =
    ClauseString(valueName, this, isFunction = true, isNullable = element.isNullable, isAggregate = element.isAggregate, isNullOnNoRows = element.isNullOnNoRows, columnTables = element.columnTables)

/**
 * Writes [string] as a SQL string literal. The only character SQLite escapes in one is `'`, by doubling it, so this
 * keeps any string a literal: a `'` in it can't end the literal and turn the rest into SQL.
 */
private fun sqlString(string: String): String = "'${string.replace("'", "''")}'"

/**
 * COUNT aggregate function - counts non-NULL values.
 *
 * Usage:
 * ```kotlin
 * SELECT(user) GROUP_BY (user.department) HAVING (count(user.id) GT 5)
 * ```
 */
@FunctionDslMaker
public fun <T> Relation<T>.count(element: ClauseElement<*>): ClauseNumber<Long> =
    ClauseNumber("count(${element.sql})", this, isFunction = true, isNullable = false, isAggregate = true, isNullOnNoRows = false, columnTables = element.columnTables)

/**
 * COUNT(*) aggregate function - counts all rows (including NULLs).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) WHERE (count(*) GT 100)
 * ```
 */
@FunctionDslMaker
public fun <T> Relation<T>.count(x: X): ClauseNumber<Long> =
    ClauseNumber("count(*)", this, isFunction = true, isNullable = false, isAggregate = true, isNullOnNoRows = false)

/**
 * AVG aggregate function - returns average value, as a `Double`.
 */
@FunctionDslMaker
public fun <T> Relation<T>.avg(element: ClauseElement<*>): ClauseNumber<Double> =
    numberAggregate("avg(${element.sql})", element)

/**
 * SUM aggregate function - returns sum of values.
 *
 * The sum of integers is a `Long`, and the sum of reals a `Double`, so there is one overload per column type, and
 * one for a Boolean column, whose sum counts the `true` values. There is none for `ULong`, as SQLite stores values
 * above `Long.MAX_VALUE` as negative numbers, which would make the sum wrong.
 */
@FunctionDslMaker
@JvmName("sumOfByte")
public fun <T> Relation<T>.sum(element: ClauseNumber<Byte>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `Short` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfShort")
public fun <T> Relation<T>.sum(element: ClauseNumber<Short>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of an `Int` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfInt")
public fun <T> Relation<T>.sum(element: ClauseNumber<Int>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `Long` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfLong")
public fun <T> Relation<T>.sum(element: ClauseNumber<Long>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `UByte` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfUByte")
public fun <T> Relation<T>.sum(element: ClauseNumber<UByte>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `UShort` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfUShort")
public fun <T> Relation<T>.sum(element: ClauseNumber<UShort>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `UInt` column - returns a `Long`. */
@FunctionDslMaker
@JvmName("sumOfUInt")
public fun <T> Relation<T>.sum(element: ClauseNumber<UInt>): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `Float` column - returns a `Double`. */
@FunctionDslMaker
@JvmName("sumOfFloat")
public fun <T> Relation<T>.sum(element: ClauseNumber<Float>): ClauseNumber<Double> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a `Double` column - returns a `Double`. */
@FunctionDslMaker
@JvmName("sumOfDouble")
public fun <T> Relation<T>.sum(element: ClauseNumber<Double>): ClauseNumber<Double> =
    numberAggregate("sum(${element.sql})", element)

/** SUM aggregate function of a Boolean column - returns the number of `true` values, as a `Long`. */
@FunctionDslMaker
public fun <T> Relation<T>.sum(element: ClauseBoolean): ClauseNumber<Long> =
    numberAggregate("sum(${element.sql})", element)

/**
 * MAX aggregate function - returns maximum value, of the same type as [element]: a `max` of an `Int` column is an
 * `Int`, and of a String column a String.
 */
@Suppress("UNCHECKED_CAST")
@FunctionDslMaker
public fun <T, E : ClauseElement<*>> Relation<T>.max(element: E): E =
    element.toAggregate("max(${element.sql})", this) as E

/**
 * MIN aggregate function - returns minimum value, of the same type as [element]: a `min` of an `Int` column is an
 * `Int`, and of a String column a String.
 */
@Suppress("UNCHECKED_CAST")
@FunctionDslMaker
public fun <T, E : ClauseElement<*>> Relation<T>.min(element: E): E =
    element.toAggregate("min(${element.sql})", this) as E

/**
 * GROUP_CONCAT aggregate function - concatenates all non-NULL values in a group with a separator.
 *
 * Returns a string which is the concatenation of all non-NULL values of the specified column.
 * If there are no non-NULL values, the result is NULL.
 *
 * Example:
 * ```kotlin
 * // Concatenate all user names with comma separator
 * SELECT(group_concat(User::name, ","))
 * ```
 *
 * @param element The string column to concatenate
 * @param infix The separator string to use between values
 * @return ClauseString representing the concatenated result
 */
@FunctionDslMaker
public fun <T> Relation<T>.group_concat(element: ClauseString<*>, infix: String): ClauseString<String> =
    ClauseString("group_concat(${element.sql},${sqlString(infix)})", this, isFunction = true, isNullable = element.isNullable, isAggregate = true, isNullOnNoRows = true, columnTables = element.columnTables)

/**
 * The argument of an aggregate function that takes only the distinct values of [element], as SQL's `DISTINCT` does
 * in `count(DISTINCT author)`. [DISTINCT] makes it, and only the aggregate functions it changes take it: `count`,
 * `avg`, `sum` and `group_concat`.
 *
 * @author Yuang Qiao
 */
public class Distinct<out E : ClauseElement<*>> internal constructor(internal val element: E) {

    internal val sql: String
        get() = "DISTINCT ${element.sql}"
}

/**
 * Makes an aggregate function take only the distinct values of [element], as SQL's `DISTINCT` does:
 * ```kotlin
 * BookTable SELECT (count(DISTINCT(BookTable.author)) AS BookStats::authors)
 * // SELECT count(DISTINCT book.author) AS authors FROM book
 * ```
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <E : ClauseElement<*>> DISTINCT(element: E): Distinct<E> = Distinct(element)

/**
 * COUNT aggregate function of the distinct values - counts the distinct non-NULL values of an element, as
 * `count(DISTINCT author)` counts the authors.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.count(distinct: Distinct<*>): ClauseNumber<Long> =
    ClauseNumber("count(${distinct.sql})", this, isFunction = true, isNullable = false, isAggregate = true, isNullOnNoRows = false, columnTables = distinct.element.columnTables)

/**
 * AVG aggregate function of the distinct values - returns the average of the distinct values, as a `Double`.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.avg(distinct: Distinct<*>): ClauseNumber<Double> =
    numberAggregate("avg(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a `Byte` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctByte")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Byte>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a `Short` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctShort")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Short>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of an `Int` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctInt")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Int>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a `Long` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctLong")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Long>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of an `UByte` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctUByte")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<UByte>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of an `UShort` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctUShort")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<UShort>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of an `UInt` column - returns a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctUInt")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<UInt>>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a `Float` column - returns a `Double`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctFloat")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Float>>): ClauseNumber<Double> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a `Double` column - returns a `Double`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctDouble")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseNumber<Double>>): ClauseNumber<Double> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/** SUM aggregate function of the distinct values of a Boolean column - returns 1 if any is `true`, as a `Long`. */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
@JvmName("sumOfDistinctBoolean")
public fun <T> Relation<T>.sum(distinct: Distinct<ClauseBoolean>): ClauseNumber<Long> =
    numberAggregate("sum(${distinct.sql})", distinct.element)

/**
 * GROUP_CONCAT aggregate function of the distinct values - concatenates the distinct non-NULL values, separated by
 * commas. SQLite allows no other separator here, as an aggregate function with DISTINCT takes only one argument.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.group_concat(distinct: Distinct<ClauseString<*>>): ClauseString<String> =
    ClauseString("group_concat(${distinct.sql})", this, isFunction = true, isNullable = distinct.element.isNullable, isAggregate = true, isNullOnNoRows = true, columnTables = distinct.element.columnTables)

/**
 * ABS scalar function - returns absolute value, of the same type as [element].
 */
@FunctionDslMaker
public fun <T, V : Any> Relation<T>.abs(element: ClauseNumber<V>): ClauseNumber<V> =
    numberFunction("abs(${element.sql})", element)

/**
 * ROUND scalar function - rounds a number to a specified number of decimal places.
 *
 * Rounds the numeric value to the specified number of digits after the decimal point.
 * If digits is negative, rounding occurs to the left of the decimal point.
 *
 * Example:
 * ```kotlin
 * // Round price to 2 decimal places
 * SELECT WHERE (round(Product::price, 2) EQ 19.99)
 * ```
 *
 * @param element The numeric value to round
 * @param digits The number of decimal places to round to
 * @return ClauseNumber representing the rounded value, a `Double` even for an integer column
 */
@FunctionDslMaker
public fun <T> Relation<T>.round(element: ClauseNumber<*>, digits: Int): ClauseNumber<Double> =
    numberFunction("round(${element.sql},$digits)", element)

/**
 * RANDOM scalar function - returns a pseudo-random integer.
 *
 * Returns a pseudo-random integer between -9223372036854775808 and +9223372036854775807.
 *
 * Example:
 * ```kotlin
 * // Select random records
 * SELECT ORDER_BY(random()) LIMIT 10
 * ```
 *
 * @return ClauseNumber representing the random integer
 */
@FunctionDslMaker
public fun <T> Relation<T>.random(): ClauseNumber<Long> =
    ClauseNumber("random()", this, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false)

/**
 * SIGN scalar function - returns the sign of a number.
 *
 * Returns -1, 0, or +1 if the argument is negative, zero, or positive respectively.
 * If the argument is NULL, then NULL is returned.
 *
 * Example:
 * ```kotlin
 * // Get the sign of balance
 * SELECT WHERE (sign(Account::balance) EQ 1)
 * ```
 * ***It is based on SQLite 3.51.1, disabled it temporarily***
 *
 * @param element The numeric value to get the sign of
 * @return ClauseNumber representing -1, 0, or 1
 */
/* @FunctionDslMaker
 public fun <T> Relation<T>.sign(element: ClauseNumber<*>): ClauseNumber<Long> =
    numberFunction("sign(${element.sql})", element) */

/**
 * UPPER scalar function - converts string to uppercase.
 */
@FunctionDslMaker
public fun <T> Relation<T>.upper(element: ClauseString<*>): ClauseString<String> =
    stringFunction("upper(${element.sql})", element)

/**
 * LOWER scalar function - converts string to lowercase.
 */
@FunctionDslMaker
public fun <T> Relation<T>.lower(element: ClauseString<*>): ClauseString<String> =
    stringFunction("lower(${element.sql})", element)

/**
 * LENGTH scalar function - returns string/blob length in bytes.
 */
@FunctionDslMaker
public fun <T> Relation<T>.length(element: ClauseString<*>): ClauseNumber<Long> =
    numberFunction("length(${element.sql})", element)

/**
 * LENGTH scalar function - returns the length of a BLOB in bytes.
 *
 * For BLOBs, returns the number of bytes in the blob.
 *
 * Example:
 * ```kotlin
 * // Get the size of an image blob
 * SELECT WHERE (length(Image::data) GT 1024)
 * ```
 *
 * @param element The BLOB column to measure
 * @return ClauseNumber representing the length in bytes
 */
@FunctionDslMaker
public fun <T> Relation<T>.length(element: ClauseBlob): ClauseNumber<Long> =
    numberFunction("length(${element.sql})", element)

/**
 * SUBSTR scalar function - extracts a substring from a string.
 *
 * Returns a substring starting at position `start` with length `len`.
 * In SQLite, the first character has index 1 (not 0).
 *
 * Example:
 * ```kotlin
 * // Extract first 5 characters
 * SELECT WHERE (substr(User::name, 1, 5) EQ "Alice")
 * ```
 *
 * @param element The string to extract from
 * @param start The starting position (1-indexed)
 * @param len The length of the substring to extract
 * @return ClauseString representing the extracted substring
 */
@FunctionDslMaker
public fun <T> Relation<T>.substr(element: ClauseString<*>, start: Int, len: Int): ClauseString<String> =
    stringFunction("substr(${element.sql},$start,$len)", element)

/**
 * TRIM scalar function - removes leading and trailing whitespace from a string.
 *
 * Removes spaces from both ends of the string.
 *
 * Example:
 * ```kotlin
 * // Remove whitespace from names
 * SELECT(trim(User::name))
 * ```
 *
 * @param element The string to trim
 * @return ClauseString with whitespace removed from both ends
 */
@FunctionDslMaker
public fun <T> Relation<T>.trim(element: ClauseString<*>): ClauseString<String> =
    stringFunction("trim(${element.sql})", element)

/**
 * LTRIM scalar function - removes leading (left) whitespace from a string.
 *
 * Removes spaces from the beginning of the string only.
 *
 * Example:
 * ```kotlin
 * // Remove leading whitespace
 * SELECT(ltrim(User::name))
 * ```
 *
 * @param element The string to trim
 * @return ClauseString with leading whitespace removed
 */
@FunctionDslMaker
public fun <T> Relation<T>.ltrim(element: ClauseString<*>): ClauseString<String> =
    stringFunction("ltrim(${element.sql})", element)

/**
 * RTRIM scalar function - removes trailing (right) whitespace from a string.
 *
 * Removes spaces from the end of the string only.
 *
 * Example:
 * ```kotlin
 * // Remove trailing whitespace
 * SELECT(rtrim(User::name))
 * ```
 *
 * @param element The string to trim
 * @return ClauseString with trailing whitespace removed
 */
@FunctionDslMaker
public fun <T> Relation<T>.rtrim(element: ClauseString<*>): ClauseString<String> =
    stringFunction("rtrim(${element.sql})", element)

/**
 * REPLACE scalar function - replaces all occurrences of a substring with another string.
 *
 * Returns a copy of the string with all occurrences of `old` replaced by `new`.
 *
 * Example:
 * ```kotlin
 * // Replace dots with dashes in email
 * SELECT WHERE (replace(User::email, ".", "-") LIKE "%gmail-com")
 * ```
 *
 * @param element The string to perform replacement on
 * @param old The substring to find and replace
 * @param new The replacement string
 * @return ClauseString with replacements applied
 */
@FunctionDslMaker
public fun <T> Relation<T>.replace(element: ClauseString<*>, old: String, new: String): ClauseString<String> =
    stringFunction("replace(${element.sql},${sqlString(old)},${sqlString(new)})", element)

/**
 * INSTR scalar function - finds the first occurrence of a substring.
 *
 * Returns the 1-indexed position of the first occurrence of `sub` in the string.
 * Returns 0 if the substring is not found.
 *
 * Example:
 * ```kotlin
 * // Find position of '@' in email
 * SELECT WHERE (instr(User::email, "@") GT 0)
 * ```
 *
 * @param element The string to search in
 * @param sub The substring to find
 * @return ClauseNumber representing the position (1-indexed) or 0 if not found
 */
@FunctionDslMaker
public fun <T> Relation<T>.instr(element: ClauseString<*>, sub: String): ClauseNumber<Long> =
    numberFunction("instr(${element.sql},${sqlString(sub)})", element)

/**
 * PRINTF scalar function - formats a string according to a format specification.
 *
 * Works similar to the standard C printf() function. The format string can contain
 * format specifiers like %s (string), %d (integer), %f (float), etc.
 *
 * Example:
 * ```kotlin
 * // Format price with currency
 * SELECT(printf("$%.2f", Product::price))
 * ```
 *
 * @param format The format string with format specifiers
 * @param element The value to format
 * @return ClauseString with the formatted result
 */
@FunctionDslMaker
public fun <T> Relation<T>.printf(format: String, element: ClauseString<*>): ClauseString<String> =
    stringFunction("printf(${sqlString(format)},${element.sql})", element)