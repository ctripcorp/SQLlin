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
import com.ctrip.sqllin.dsl.sql.Relation

/**
 * SQLite's date and time functions, which SQLite has on every platform SQLlin supports.
 *
 * Each takes a time value and modifiers. The time value is a string, such as `"2026-10-07 12:30:00"` or `"now"`, or an
 * expression: a string, or a number, which is a Julian day number, or a Unix time with the `"unixepoch"` modifier. The
 * modifiers change the time in turn, as `"+1 day"`, `"start of month"` or `"localtime"` do.
 *
 * A function is NULL for a value or a modifier it can't read, so it can be NULL, except of `"now"` without modifiers.
 * The current time, and the `"localtime"` and `"utc"` modifiers, which depend on where the code runs, make it give
 * different results over time, so an index can't hold it.
 *
 * @author Yuang Qiao
 */

/**
 * DATE function - the date, as `YYYY-MM-DD`, of [time], a time string, as `"now"`, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.date(time: String, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("date($arguments)", traits)
}

/**
 * DATE function - the date, as `YYYY-MM-DD`, of [time], a string expression, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.date(time: ClauseString<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("date($arguments)", traits)
}

/**
 * DATE function - the date, as `YYYY-MM-DD`, of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.date(time: ClauseNumber<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("date($arguments)", traits)
}

/**
 * TIME function - the time, as `HH:MM:SS`, of [time], a time string, as `"now"`, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.time(time: String, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("time($arguments)", traits)
}

/**
 * TIME function - the time, as `HH:MM:SS`, of [time], a string expression, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.time(time: ClauseString<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("time($arguments)", traits)
}

/**
 * TIME function - the time, as `HH:MM:SS`, of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.time(time: ClauseNumber<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("time($arguments)", traits)
}

/**
 * DATETIME function - the date and time, as `YYYY-MM-DD HH:MM:SS`, of [time], a time string, as `"now"`, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.datetime(time: String, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("datetime($arguments)", traits)
}

/**
 * DATETIME function - the date and time, as `YYYY-MM-DD HH:MM:SS`, of [time], a string expression, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.datetime(time: ClauseString<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("datetime($arguments)", traits)
}

/**
 * DATETIME function - the date and time, as `YYYY-MM-DD HH:MM:SS`, of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.datetime(time: ClauseNumber<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("datetime($arguments)", traits)
}

/**
 * JULIANDAY function - the Julian day number, as a `Double`, of [time], a time string, as `"now"`, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.julianday(time: String, vararg modifiers: String): ClauseNumber<Double> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("julianday($arguments)", traits)
}

/**
 * JULIANDAY function - the Julian day number, as a `Double`, of [time], a string expression, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.julianday(time: ClauseString<*>, vararg modifiers: String): ClauseNumber<Double> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("julianday($arguments)", traits)
}

/**
 * JULIANDAY function - the Julian day number, as a `Double`, of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.julianday(time: ClauseNumber<*>, vararg modifiers: String): ClauseNumber<Double> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return numberExpression("julianday($arguments)", traits)
}

/**
 * STRFTIME function - [format] with its specifiers, as `%Y` or `%m`, replaced by the parts of [time], a time string, as `"now"`, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.strftime(format: String, time: String, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("strftime(${expressionLiteral(format)},$arguments)", traits)
}

/**
 * STRFTIME function - [format] with its specifiers, as `%Y` or `%m`, replaced by the parts of [time], a string expression, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.strftime(format: String, time: ClauseString<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("strftime(${expressionLiteral(format)},$arguments)", traits)
}

/**
 * STRFTIME function - [format] with its specifiers, as `%Y` or `%m`, replaced by the parts of [time], a number expression, a Julian day number or, with the `"unixepoch"` modifier, a Unix time, changed by [modifiers].
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> Relation<T>.strftime(format: String, time: ClauseNumber<*>, vararg modifiers: String): ClauseString<String> {
    val (arguments, traits) = timeArguments(time, modifiers)
    return stringExpression("strftime(${expressionLiteral(format)},$arguments)", traits)
}

/**
 * The SQL of the arguments of a date and time function, [time] and [modifiers], and the traits of the function.
 */
internal fun timeArguments(time: Any, modifiers: Array<out String>): Pair<String, Traits> {
    val isLocal = modifiers.any { it.trim().lowercase() in LOCAL_MODIFIERS }
    val (sql, traits) = if (time is ClauseElement<*>) {
        time.sql to strictTraits(listOf(time), isNullable = true, isDeterministic = !isLocal)
    } else {
        val isNow = (time as String).trim().equals("now", ignoreCase = true)
        expressionLiteral(time) to strictTraits(
            operands = emptyList(),
            isNullable = !isNow || modifiers.isNotEmpty(),
            isNullOnNoRows = false,
            isDeterministic = !isNow && !isLocal,
        )
    }
    val arguments = buildString {
        append(sql)
        for (modifier in modifiers) {
            append(',')
            append(expressionLiteral(modifier))
        }
    }
    return arguments to traits
}

/** The modifiers that convert between the local time and UTC, which depends on where the code runs. */
private val LOCAL_MODIFIERS = setOf("localtime", "utc")
