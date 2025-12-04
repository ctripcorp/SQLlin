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

import com.ctrip.sqllin.dsl.annotation.FunctionDslMaker
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.X

/**
 * SQLite aggregate and scalar functions for use in SELECT clauses.
 *
 * These functions can be used in WHERE, HAVING, ORDER BY, and SELECT expressions.
 * All functions return [ClauseElement] wrappers that can be compared with operators.
 *
 * @author Yuang Qiao
 */

/**
 * COUNT aggregate function - counts non-NULL values.
 *
 * Usage:
 * ```kotlin
 * SELECT(user) GROUP_BY (user.department) HAVING (count(user.id) GT 5)
 * ```
 */
@FunctionDslMaker
public fun <T> Table<T>.count(element: ClauseElement): ClauseNumber =
    ClauseNumber("count(${element.valueName})", this, true)

/**
 * COUNT(*) aggregate function - counts all rows (including NULLs).
 *
 * Usage:
 * ```kotlin
 * SELECT(user) WHERE (count(*) GT 100)
 * ```
 */
@FunctionDslMaker
public fun <T> Table<T>.count(x: X): ClauseNumber =
    ClauseNumber("count(*)", this, true)

/**
 * AVG aggregate function - returns average value.
 */
@FunctionDslMaker
public fun <T> Table<T>.avg(element: ClauseElement): ClauseNumber =
    ClauseNumber("avg(${element.valueName})", this, true)

/**
 * SUM aggregate function - returns sum of values.
 */
@FunctionDslMaker
public fun <T> Table<T>.sum(element: ClauseElement): ClauseNumber =
    ClauseNumber("sum(${element.valueName})", this, true)

/**
 * MAX aggregate function - returns maximum value.
 */
@FunctionDslMaker
public fun <T> Table<T>.max(element: ClauseElement): ClauseNumber =
    ClauseNumber("max(${element.valueName})", this, true)

/**
 * MIN aggregate function - returns minimum value.
 */
@FunctionDslMaker
public fun <T> Table<T>.min(element: ClauseElement): ClauseNumber =
    ClauseNumber("min(${element.valueName})", this, true)

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
public fun <T> Table<T>.group_concat(element: ClauseString, infix: String): ClauseString =
    ClauseString("group_concat(${element.valueName},'$infix')", this, true)

/**
 * ABS scalar function - returns absolute value.
 */
@FunctionDslMaker
public fun <T> Table<T>.abs(element: ClauseNumber): ClauseNumber =
    ClauseNumber("abs(${element.valueName})", this, true)

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
 * @return ClauseNumber representing the rounded value
 */
@FunctionDslMaker
public fun <T> Table<T>.round(element: ClauseNumber, digits: Int): ClauseNumber =
    ClauseNumber("round(${element.valueName},$digits)", this, true)

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
public fun <T> Table<T>.random(): ClauseNumber =
    ClauseNumber("random()", this, true)

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
 *
 * @param element The numeric value to get the sign of
 * @return ClauseNumber representing -1, 0, or 1
 */
@FunctionDslMaker
public fun <T> Table<T>.sign(element: ClauseNumber): ClauseNumber =
    ClauseNumber("sign(${element.valueName})", this, true)

/**
 * UPPER scalar function - converts string to uppercase.
 */
@FunctionDslMaker
public fun <T> Table<T>.upper(element: ClauseString): ClauseString =
    ClauseString("upper(${element.valueName})", this, true)

/**
 * LOWER scalar function - converts string to lowercase.
 */
@FunctionDslMaker
public fun <T> Table<T>.lower(element: ClauseString): ClauseString =
    ClauseString("lower(${element.valueName})", this, true)

/**
 * LENGTH scalar function - returns string/blob length in bytes.
 */
@FunctionDslMaker
public fun <T> Table<T>.length(element: ClauseString): ClauseNumber =
    ClauseNumber("length(${element.valueName})", this, true)

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
public fun <T> Table<T>.length(element: ClauseBlob): ClauseNumber =
    ClauseNumber("length(${element.valueName})", this, true)

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
public fun <T> Table<T>.substr(element: ClauseString, start: Int, len: Int): ClauseString =
    ClauseString("substr(${element.valueName},$start,$len)", this, true)

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
public fun <T> Table<T>.trim(element: ClauseString): ClauseString =
    ClauseString("trim(${element.valueName})", this, true)

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
public fun <T> Table<T>.ltrim(element: ClauseString): ClauseString =
    ClauseString("ltrim(${element.valueName})", this, true)

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
public fun <T> Table<T>.rtrim(element: ClauseString): ClauseString =
    ClauseString("rtrim(${element.valueName})", this, true)

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
public fun <T> Table<T>.replace(element: ClauseString, old: String, new: String): ClauseString =
    ClauseString("replace(${element.valueName},'$old','$new')", this, true)

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
public fun <T> Table<T>.instr(element: ClauseString, sub: String): ClauseNumber =
    ClauseNumber("instr(${element.valueName},'$sub')", this, true)

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
public fun <T> Table<T>.printf(format: String, element: ClauseString): ClauseString =
    ClauseString("printf('$format',${element.valueName})", this, true)