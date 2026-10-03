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

package com.ctrip.sqllin.dsl.sql

import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker

/**
 * Represents the wildcard `*` in SQL statements.
 *
 * Used in DSL operations where SQL requires a wildcard or universal selector:
 * - `SELECT *`: Select all columns from a table
 * - `DELETE *`: Delete all records from a table
 *
 * Example:
 * ```kotlin
 * // SELECT * FROM PersonTable
 * val allPeople = PersonTable SELECT X
 *
 * // DELETE FROM PersonTable
 * PersonTable DELETE X
 * ```
 *
 * @author Yuang Qiao
 */
@KeyWordDslMaker
public object X

/**
 * Selects every row like [X], but reads each into [R] rather than into the table's own row type, so that only the
 * columns [R]'s properties name are selected.
 *
 * Example:
 * ```kotlin
 * // SELECT name,age FROM PersonTable
 * val people = PersonTable SELECT X<NameAndAge>()
 * ```
 *
 * @see ProjectedX
 */
@Suppress("DSL_MARKER_APPLIED_TO_WRONG_TARGET")
@KeyWordDslMaker
public fun <R> X(): ProjectedX<R> = ProjectedX()

/**
 * The selector of every row, [X], carrying the type [R] that a SELECT reads rows into. Created by `X<R>()`.
 */
public class ProjectedX<R> internal constructor()
