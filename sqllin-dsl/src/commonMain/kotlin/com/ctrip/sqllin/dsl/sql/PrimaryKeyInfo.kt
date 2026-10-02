/*
 * Copyright (C) 2025 Ctrip.com.
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

/**
 * Metadata describing a table's primary key configuration.
 *
 * This class captures information extracted from [@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey]
 * and [@CompositePrimaryKey][com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey] annotations
 * during code generation. It enables the DSL to properly handle INSERT and UPDATE operations
 * with respect to primary key constraints.
 *
 * **Single Primary Key:**
 * When a table has a single primary key column (marked with `@PrimaryKey`):
 * - [primaryKeyName] contains the column name
 * - [compositePrimaryKeys] is `null`
 * - [isGeneratedByDatabase] is `true` if the key is a `Long?`, whose value the database assigns. A
 *   non-null `Long` key is also an `INTEGER PRIMARY KEY` (a rowid alias) but is supplied by the caller,
 *   so it is `false` there, as it is for a key of any other type
 * - [isAutomaticIncrement] is `true` if `@PrimaryKey(autoIncrement = true)` was specified
 *
 * **Composite Primary Key:**
 * When a table has multiple primary key columns (marked with `@CompositePrimaryKey`):
 * - [primaryKeyName] is `null`
 * - [compositePrimaryKeys] contains the list of column names forming the composite key
 * - [isGeneratedByDatabase] is `false` (the caller supplies every column of a composite key)
 * - [isAutomaticIncrement] is `false` (composite keys cannot auto-increment)
 *
 * **No Primary Key:**
 * When a table has no primary key annotations, [Table.primaryKeyInfo] is `null`.
 *
 * @property primaryKeyName The name of the single primary key column, or `null` for composite keys
 * @property isAutomaticIncrement Whether the primary key uses SQLite's AUTOINCREMENT keyword
 * @property isGeneratedByDatabase Whether the database assigns the primary key's value, in which case
 * a plain INSERT leaves the column out
 * @property compositePrimaryKeys List of column names forming a composite primary key, or `null` for single keys
 *
 * @author Yuang Qiao
 */
public class PrimaryKeyInfo(
    internal val primaryKeyName: String?,
    internal val isAutomaticIncrement: Boolean,
    internal val isGeneratedByDatabase: Boolean,
    internal val compositePrimaryKeys: List<String>?,
)