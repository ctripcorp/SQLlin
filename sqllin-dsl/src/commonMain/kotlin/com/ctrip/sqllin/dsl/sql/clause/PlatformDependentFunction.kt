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
import com.ctrip.sqllin.dsl.annotation.PlatformDependentSQLiteAPI
import com.ctrip.sqllin.dsl.sql.Relation

/**
 * The functions that need a newer SQLite, or a compile-time option of SQLite, than some platforms SQLlin supports have.
 * Each is marked with [PlatformDependentSQLiteAPI], and its documentation says what it needs.
 *
 * The math functions need SQLite 3.35.0 compiled with `SQLITE_ENABLE_MATH_FUNCTIONS`, which Android's system SQLite
 * never has, at any API level. The JVM driver and Apple's platforms have them, and on Linux and Windows, it depends on
 * the SQLite the app links.
 *
 * @author Yuang Qiao
 */

/**
 * SIGN function - -1, 0 or 1 as [element] is negative, zero or positive, as a `Long`. It needs SQLite 3.35.0, which
 * Android has from API 34 on.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.sign(element: ClauseNumber<*>): ClauseNumber<Long> =
    numberExpression("sign(${element.sql})", strictTraits(listOf(element)))

/**
 * UNIXEPOCH function - the Unix time, in seconds, of [time], a time string, as `"now"`, changed by [modifiers], as a `Long`. It needs SQLite
 * 3.38.0, which Android has from API 34 on.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.unixepoch(time: String, vararg modifiers: String): ClauseNumber<Long> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("unixepoch($arguments)", traits)
}

/**
 * UNIXEPOCH function - the Unix time, in seconds, of [time], a string expression, changed by [modifiers], as a `Long`. It needs SQLite
 * 3.38.0, which Android has from API 34 on.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.unixepoch(time: ClauseString<*>, vararg modifiers: String): ClauseNumber<Long> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("unixepoch($arguments)", traits)
}

/**
 * UNIXEPOCH function - the Unix time, in seconds, of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers], as a `Long`. It needs SQLite
 * 3.38.0, which Android has from API 34 on.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.unixepoch(time: ClauseNumber<*>, vararg modifiers: String): ClauseNumber<Long> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("unixepoch($arguments)", traits)
}

/**
 * CEIL math function - the least integer not less than [element], of its type: an integer stays an integer. It needs the math functions, which
 * Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T, V : Any> Relation<T>.ceil(element: ClauseNumber<V>): ClauseNumber<V> =
    element.derive("ceil(${element.sql})", strictTraits(listOf(element)))

/**
 * FLOOR math function - the greatest integer not greater than [element], of its type: an integer stays an integer. It needs the math functions, which
 * Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T, V : Any> Relation<T>.floor(element: ClauseNumber<V>): ClauseNumber<V> =
    element.derive("floor(${element.sql})", strictTraits(listOf(element)))

/**
 * TRUNC math function - [element] without its fractional part, of its type: an integer stays an integer. It needs the math functions, which
 * Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T, V : Any> Relation<T>.trunc(element: ClauseNumber<V>): ClauseNumber<V> =
    element.derive("trunc(${element.sql})", strictTraits(listOf(element)))

/**
 * SQRT math function - the square root of [element], as a `Double`, NULL for a value outside its domain, so it can be NULL. It needs
 * the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.sqrt(element: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("sqrt(${element.sql})", strictTraits(listOf(element), isNullable = true))

/**
 * EXP math function - e raised to [element], as a `Double`, NULL for a value outside its domain, so it can be NULL. It needs
 * the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.exp(element: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("exp(${element.sql})", strictTraits(listOf(element), isNullable = true))

/**
 * LN math function - the natural logarithm of [element], as a `Double`, NULL for a value outside its domain, so it can be NULL. It needs
 * the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.ln(element: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("ln(${element.sql})", strictTraits(listOf(element), isNullable = true))

/**
 * LOG10 math function - the base-10 logarithm of [element], as a `Double`, NULL for a value outside its domain, so it can be NULL. It needs
 * the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.log10(element: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("log10(${element.sql})", strictTraits(listOf(element), isNullable = true))

/**
 * LOG2 math function - the base-2 logarithm of [element], as a `Double`, NULL for a value outside its domain, so it can be NULL. It needs
 * the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.log2(element: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("log2(${element.sql})", strictTraits(listOf(element), isNullable = true))

/**
 * POW math function - [base] raised to [exponent], as a `Double`, which can be NULL. It needs the math functions, which
 * Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.pow(base: ClauseNumber<*>, exponent: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("pow(${base.sql},${exponent.sql})", strictTraits(listOf(base, exponent), isNullable = true))

/**
 * POW math function - [base] raised to the value [exponent], as a `Double`, which can be NULL. It needs the math
 * functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.pow(base: ClauseNumber<*>, exponent: Double): ClauseNumber<Double> =
    numberExpression("pow(${base.sql},${expressionLiteral(exponent)})", strictTraits(listOf(base), isNullable = true))

/**
 * MOD math function - the remainder of [dividend] divided by [divisor], as a `Double`, for real numbers too, unlike
 * `%`. It is NULL for a division by zero, so it can be NULL. It needs the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.mod(dividend: ClauseNumber<*>, divisor: ClauseNumber<*>): ClauseNumber<Double> =
    numberExpression("mod(${dividend.sql},${divisor.sql})", strictTraits(listOf(dividend, divisor), isNullable = true))

/**
 * MOD math function - the remainder of [dividend] divided by the value [divisor], as a `Double`, NULL for a division by
 * zero. It needs the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.mod(dividend: ClauseNumber<*>, divisor: Double): ClauseNumber<Double> =
    numberExpression("mod(${dividend.sql},${expressionLiteral(divisor)})", strictTraits(listOf(dividend), isNullable = true))

/**
 * PI math function - pi, as a `Double`. It needs the math functions, which Android never has.
 */
@PlatformDependentSQLiteAPI
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.pi(): ClauseNumber<Double> =
    numberExpression("pi()", strictTraits(emptyList(), isNullable = false, isNullOnNoRows = false))
