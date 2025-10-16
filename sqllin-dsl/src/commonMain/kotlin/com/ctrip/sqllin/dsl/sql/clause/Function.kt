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
 * SELECT(user) WHERE (count(X) GT 100)
 * ```
 */
@FunctionDslMaker
public fun <T> Table<T>.count(x: X): ClauseNumber =
    ClauseNumber("count(*)", this, true)

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
 * ABS scalar function - returns absolute value.
 */
@FunctionDslMaker
public fun <T> Table<T>.abs(number: ClauseElement): ClauseNumber =
    ClauseNumber("abs(${number.valueName})", this, true)

/**
 * UPPER scalar function - converts string to uppercase.
 */
@FunctionDslMaker
public fun <T> Table<T>.upper(element: ClauseElement): ClauseString =
    ClauseString("upper(${element.valueName})", this, true)

/**
 * LOWER scalar function - converts string to lowercase.
 */
@FunctionDslMaker
public fun <T> Table<T>.lower(element: ClauseElement): ClauseString =
    ClauseString("lower(${element.valueName})", this, true)

/**
 * LENGTH scalar function - returns string/blob length in bytes.
 */
@FunctionDslMaker
public fun <T> Table<T>.length(element: ClauseElement): ClauseNumber =
    ClauseNumber("length(${element.valueName})", this, true)
