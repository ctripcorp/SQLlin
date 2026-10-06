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

package com.ctrip.sqllin.dsl.sql.compiler

/**
 * Returns [sql] with each `?` placeholder replaced by the SQL literal of the corresponding parameter.
 *
 * SQLite doesn't let a view or a trigger take parameters, so a statement that becomes part of one has to carry its
 * values in its SQL. A `?` inside a string literal or a quoted identifier isn't a placeholder, so it is skipped, as in
 * the SQL of `replace(name, "?", "")`.
 *
 * @throws IllegalArgumentException if the placeholders and [parameters] don't match in number, or a parameter has a
 * type no literal is written for
 */
internal fun inlineParameters(sql: String, parameters: List<Any?>?): String {
    if (parameters.isNullOrEmpty())
        return sql
    val iterator = parameters.iterator()
    val builder = StringBuilder(sql.length + parameters.size * 8)
    var quote: Char? = null
    var index = 0
    while (index < sql.length) {
        val char = sql[index]
        when {
            quote != null -> {
                builder.append(char)
                if (char == quote) {
                    // A doubled quote is an escaped one, inside the literal or identifier
                    if (index + 1 < sql.length && sql[index + 1] == quote) {
                        builder.append(quote)
                        index++
                    } else {
                        quote = null
                    }
                }
            }
            char == '\'' || char == '"' || char == '`' -> {
                quote = char
                builder.append(char)
            }
            char == '?' -> {
                require(iterator.hasNext()) { "The SQL has more placeholders than the ${parameters.size} parameters: $sql" }
                builder.append(sqlLiteral(iterator.next()))
            }
            else -> builder.append(char)
        }
        index++
    }
    require(!iterator.hasNext()) { "The SQL has fewer placeholders than the ${parameters.size} parameters: $sql" }
    return builder.toString()
}

/**
 * Returns [value] as a SQL literal: a string between single quotes, with each one inside doubled; a BLOB as `X'...'`;
 * a Boolean as 1 or 0, as SQLite stores it.
 */
internal fun sqlLiteral(value: Any?): String = when (value) {
    null -> "NULL"
    is String -> "'${value.replace("'", "''")}'"
    is Char -> sqlLiteral(value.toString())
    is Boolean -> if (value) "1" else "0"
    is Double -> doubleLiteral(value)
    is Float -> doubleLiteral(value.toDouble())
    is Byte, is Short, is Int, is Long -> value.toString()
    is ByteArray -> value.joinToString(separator = "", prefix = "X'", postfix = "'") { HEX_DIGITS[(it.toInt() shr 4) and 0xF].toString() + HEX_DIGITS[it.toInt() and 0xF] }
    else -> throw IllegalArgumentException("Can't write a ${value::class.simpleName} as a SQL literal.")
}

private const val HEX_DIGITS = "0123456789ABCDEF"

/**
 * SQLite has no literal for NaN, which it stores as NULL, nor for infinity, which a number too large to represent is.
 */
private fun doubleLiteral(value: Double): String = when {
    value.isNaN() -> "NULL"
    value == Double.POSITIVE_INFINITY -> "9e999"
    value == Double.NEGATIVE_INFINITY -> "-9e999"
    else -> value.toString()
}
