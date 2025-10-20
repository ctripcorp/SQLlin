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

import com.ctrip.sqllin.dsl.sql.Table

/**
 * Base class for elements used in SQL clauses.
 *
 * Represents a reference to a database column or function that can be used in clause expressions.
 * Clause elements maintain their source table and whether they represent a function call.
 *
 * Subclasses provide type-specific wrappers:
 * - [ClauseBoolean]: Boolean column/function references with comparison operators
 * - [ClauseNumber]: Numeric column/function references with arithmetic and comparison operators
 * - [ClauseString]: String column/function references with text comparison operators
 * - [ClauseBlob]: BLOB (ByteArray) column/function references with comparison operators
 *
 * Used in:
 * - WHERE/HAVING conditions
 * - ORDER BY expressions
 * - GROUP BY columns
 * - SET assignments
 * - JOIN USING clauses
 *
 * @property valueName The column name or function expression
 * @property table The table this element belongs to
 * @property isFunction Whether this represents a function call (e.g., COUNT, SUM)
 *
 * @author Yuang Qiao
 */
public sealed class ClauseElement(
    internal val valueName: String,
    internal val table: Table<*>,
    internal val isFunction: Boolean,
)