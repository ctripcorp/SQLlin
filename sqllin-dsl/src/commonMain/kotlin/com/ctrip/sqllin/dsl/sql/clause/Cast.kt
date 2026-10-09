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

@file:Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.FunctionDslMaker
import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker

/**
 * The types an expression can be converted to with `CAST`, named as SQLlin names the type of a column of that Kotlin
 * type when it creates a table: `INT` for `Int`, `BIGINT` for `Long`, `TEXT` for `String` and so on. SQLite converts by
 * the type's affinity, so all integer types convert alike, as do `FLOAT` and `DOUBLE`.
 *
 * There is no `BOOLEAN`, as SQLite's `CAST(5 AS BOOLEAN)` is `5`, not a Boolean, nor `NUMERIC`, whose result is an
 * integer or a real number depending on the value.
 *
 * @param V The Kotlin type of the values the expression is converted to
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public sealed class SQLType<V : Any>(internal val name: String)

/**
 * A number type, which `CAST` converts an expression to: an integer type truncates a real number, as `CAST(3.7 AS INT)`
 * is `3`, and reads the leading digits of a string, as `CAST('12abc' AS INT)` is `12`. It doesn't check the range of the
 * Kotlin type, so a value that doesn't fit is read back wrong.
 */
@ExperimentalDSLDatabaseAPI
public sealed class NumberType<V : Any>(name: String) : SQLType<V>(name)

/** `Byte`, the type of a `Byte` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object TINYINT : NumberType<Byte>("TINYINT")

/** `Short`, the type of a `Short` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object SMALLINT : NumberType<Short>("SMALLINT")

/** `Int`, the type of an `Int` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object INT : NumberType<Int>("INT")

/** `Long`, the type of a `Long` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object BIGINT : NumberType<Long>("BIGINT")

/** `Float`, the type of a `Float` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object FLOAT : NumberType<Float>("FLOAT")

/** `Double`, the type of a `Double` column. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object DOUBLE : NumberType<Double>("DOUBLE")

/** `String`, the type of a `String` column: a number converts to its decimal digits, a BLOB to its bytes as text. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object TEXT : SQLType<String>("TEXT")

/** `ByteArray`, the type of a `ByteArray` column: a string converts to its bytes. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object BLOB : SQLType<ByteArray>("BLOB")

/**
 * An expression and the type `CAST` converts it to, as `AS` pairs them in `CAST(price AS INT)`.
 */
@ExperimentalDSLDatabaseAPI
public class Cast<out T : SQLType<*>> internal constructor(internal val element: ClauseElement<*>, internal val type: T)

/**
 * Pairs this expression with the [type] that `CAST` converts it to: `CAST(price AS INT)`.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T : SQLType<*>> ClauseElement<*>.AS(type: T): Cast<T> = Cast(this, type)

/**
 * Converts an expression to a number type, SQL's `CAST(x AS type)`, as in `CAST(price AS INT)`, an `Int`. It is NULL
 * where the expression is.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <V : Any> CAST(cast: Cast<NumberType<V>>): ClauseNumber<V> {
    val element = cast.element
    return ClauseNumber("CAST(${element.sql} AS ${cast.type.name})", element.table, isFunction = true, isNullable = element.isNullable, isAggregate = element.isAggregate, isNullOnNoRows = element.isNullOnNoRows, columnTables = element.columnTables, tables = element.tables, isDeterministic = element.isDeterministic)
}

/**
 * Converts an expression to a string, SQL's `CAST(x AS TEXT)`, as in `CAST(price AS TEXT)`. It is NULL where the
 * expression is.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun CAST(cast: Cast<TEXT>): ClauseString<String> {
    val element = cast.element
    return ClauseString("CAST(${element.sql} AS TEXT)", element.table, isFunction = true, isNullable = element.isNullable, isAggregate = element.isAggregate, isNullOnNoRows = element.isNullOnNoRows, columnTables = element.columnTables, tables = element.tables, isDeterministic = element.isDeterministic)
}

/**
 * Converts an expression to a BLOB, SQL's `CAST(x AS BLOB)`. It is NULL where the expression is.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun CAST(cast: Cast<BLOB>): ClauseBlob {
    val element = cast.element
    return ClauseBlob("CAST(${element.sql} AS BLOB)", element.table, isFunction = true, isNullable = element.isNullable, isAggregate = element.isAggregate, isNullOnNoRows = element.isNullOnNoRows, columnTables = element.columnTables, tables = element.tables, isDeterministic = element.isDeterministic)
}
