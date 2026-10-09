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
import com.ctrip.sqllin.dsl.annotation.KeyWordDslMaker
import com.ctrip.sqllin.dsl.annotation.StatementDslMaker

/**
 * The collations SQLite has on every platform, which decide how strings compare and sort.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
public sealed class Collation(internal val name: String)

/** Compares the bytes of strings, as SQLite does by default. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object BINARY : Collation("BINARY")

/** Compares strings ignoring the case of the ASCII letters only, so `'A'` equals `'a'`, but `'É'` doesn't equal `'é'`. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object NOCASE : Collation("NOCASE")

/** Compares strings as BINARY does, ignoring the spaces at their ends. */
@ExperimentalDSLDatabaseAPI
@KeyWordDslMaker
public object RTRIM : Collation("RTRIM")

/**
 * The COLLATE operator: this string compared and sorted by [collation], as in `(name COLLATE NOCASE) EQ "ann"`, which
 * matches `'Ann'` too, or in `ORDER_BY((name COLLATE NOCASE) to ASC)`.
 */
@ExperimentalDSLDatabaseAPI
@StatementDslMaker
public infix fun <V : Any> ClauseString<V>.COLLATE(collation: Collation): ClauseString<V> =
    derive("($sql COLLATE ${collation.name})", traits)
