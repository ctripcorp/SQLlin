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

/**
 * Literal values, as elements of expressions.
 *
 * A Kotlin value given to an operator or a function becomes a literal by itself, as `1` in `visits + 1`, so `literal`
 * is only needed where a value stands alone, such as a value to set a column to, or a branch of `CASE`. The value is
 * written into the SQL, which SQLite lets a view, a trigger and an index have, where it allows no parameters, and a
 * string is written between single quotes with each one inside doubled, so that it stays a literal.
 *
 * A literal can't be NULL: leave a column out to set it to NULL, or omit `ELSE` in a `CASE`.
 *
 * @author Yuang Qiao
 */

/** A literal number, of the type of [value]: `literal(5)` is a `ClauseNumber<Int>`. */
@ExperimentalDSLDatabaseAPI
public fun <V : Number> literal(value: V): ClauseNumber<V> = ClauseNumber(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

/** A literal `UByte`, written as the number SQLite stores for it. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: UByte): ClauseNumber<UByte> = unsignedLiteral(value)

/** A literal `UShort`, written as the number SQLite stores for it. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: UShort): ClauseNumber<UShort> = unsignedLiteral(value)

/** A literal `UInt`, written as the number SQLite stores for it. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: UInt): ClauseNumber<UInt> = unsignedLiteral(value)

/** A literal `ULong`, written as the `Long` of the same bits, which SQLite stores for it. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: ULong): ClauseNumber<ULong> = unsignedLiteral(value)

/** A literal string. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: String): ClauseString<String> = ClauseString(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

/** A literal `Char`, which SQLite stores as a string of one character. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: Char): ClauseString<Char> = ClauseString(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

/** A literal Boolean, written as `1` or `0`, as SQLite stores it. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: Boolean): ClauseBoolean = ClauseBoolean(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

/** A literal BLOB, written as `X'...'`. */
@ExperimentalDSLDatabaseAPI
public fun literal(value: ByteArray): ClauseBlob = ClauseBlob(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

/** A literal enum entry, written as its ordinal, as SQLite stores it. */
@ExperimentalDSLDatabaseAPI
public fun <T : Enum<T>> literal(value: T): ClauseEnum<T> = ClauseEnum(expressionLiteral(value.ordinal), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)

private fun <V : Any> unsignedLiteral(value: V): ClauseNumber<V> = ClauseNumber(expressionLiteral(value), ExpressionRelation, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = false, columnTables = emptySet(), isLiteral = true)
