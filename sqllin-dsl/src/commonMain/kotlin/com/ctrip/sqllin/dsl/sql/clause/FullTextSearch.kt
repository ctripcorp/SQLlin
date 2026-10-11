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
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker
import com.ctrip.sqllin.dsl.sql.FtsTable
import com.ctrip.sqllin.dsl.sql.Relation

/**
 * Full-text search of FTS3 and FTS4 tables: the MATCH operator, and the functions that describe a match.
 *
 * The query is FTS's own language: terms, which all have to be found; `OR` between terms; phrases in double quotes;
 * prefixes such as `kot*`; `column:term` for one column; and `NEAR` between terms close to each other. Queries made of
 * these work the same on every platform. Beyond them, FTS has two syntaxes: Android's SQLite has the standard one,
 * where `-term` leaves out the rows with the term, while the SQLite of the JVM driver and of Apple's platforms has the
 * enhanced one, which adds `AND`, `NOT` and parentheses, and reads `-term` as the term itself. On Linux and Windows,
 * the syntax is that of the SQLite the app links.
 *
 * @author Yuang Qiao
 */

/**
 * Finds the rows of this FTS table that match [query], in any column: `table MATCH ?`.
 *
 * Example:
 * ```kotlin
 * ArticleTable SELECT WHERE(ArticleTable MATCH "kotlin coroutines")
 * ```
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <T> FtsTable<T>.MATCH(query: String): SelectCondition =
    SelectCondition("$tableName MATCH ?", mutableListOf(query))

/**
 * Finds the rows whose value in this column of an FTS table matches [query]: `table.column MATCH ?`.
 *
 * @throws IllegalArgumentException if this column isn't one of an FTS table
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun ClauseString<*>.MATCH(query: String): SelectCondition {
    require(!isFunction && table is FtsTable<*>) { "MATCH searches a column of an FTS table, which '$valueName' isn't." }
    return condition("${table.tableName}.$valueName MATCH ?", mutableListOf(query))
}

/**
 * The text around the terms a full-text query matched, with them marked: `snippet(table, start, end, ellipsis, column,
 * tokens)`. Only meaningful in a query with MATCH.
 *
 * @param start The text before each matched term
 * @param end The text after each matched term
 * @param ellipsis The text where the snippet leaves out text
 * @param column The index of the column to take the snippet from, or -1 for the one that matches best
 * @param tokens The number of terms in the snippet, at most 64; a negative number is its upper bound, taken from the
 * column that matches best
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> FtsTable<T>.snippet(
    start: String = "<b>",
    end: String = "</b>",
    ellipsis: String = "<b>...</b>",
    column: Int = -1,
    tokens: Int = -15,
): ClauseString<String> = ftsFunction(
    "snippet($tableName,${sqlString(start)},${sqlString(end)},${sqlString(ellipsis)},$column,$tokens)",
)

/**
 * Where the terms a full-text query matched are, as a text of numbers, four per match: the column, the term of the
 * query, and the byte offset and size of the match. Only meaningful in a query with MATCH.
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> FtsTable<T>.offsets(): ClauseString<String> = ftsFunction("offsets($tableName)")

/**
 * The statistics of a full-text match, as a BLOB of 32-bit unsigned integers in the machine's byte order, which a
 * ranking of the matches is computed from, as FTS3 and FTS4 have no ranking of their own. Only meaningful in a query
 * with MATCH.
 *
 * @param format The statistics to include, as the letters SQLite's documentation of `matchinfo` lists; the default,
 * "pcx", gives the number of phrases and columns, and how often each phrase occurs in each column
 */
@ExperimentalDSLDatabaseAPI
@FunctionDslMaker
public fun <T> FtsTable<T>.matchinfo(format: String = "pcx"): ClauseBlob =
    ClauseBlob("matchinfo($tableName,${sqlString(format)})", this, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = true, columnTables = setOf(tableName))

private fun Relation<*>.ftsFunction(valueName: String): ClauseString<String> =
    ClauseString(valueName, this, isFunction = true, isNullable = false, isAggregate = false, isNullOnNoRows = true, columnTables = setOf(tableName))

private fun sqlString(string: String): String = "'${string.replace("'", "''")}'"
