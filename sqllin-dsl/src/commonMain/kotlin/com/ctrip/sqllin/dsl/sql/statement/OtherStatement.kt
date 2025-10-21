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

package com.ctrip.sqllin.dsl.sql.statement

import com.ctrip.sqllin.driver.DatabaseConnection

/**
 * UPDATE statement without WHERE clause.
 *
 * Represents an UPDATE operation that can either:
 * - Execute immediately (updates all rows in the table)
 * - Be refined by adding a WHERE clause to target specific rows
 *
 * This intermediate state enables the DSL to support both:
 * ```kotlin
 * UPDATE(user) SET { /* ... */ }  // Updates all rows
 * UPDATE(user) SET { /* ... */ } WHERE { /* ... */ }  // Updates filtered rows
 * ```
 *
 * @param T The entity type being updated
 *
 * @author Yuang Qiao
 */
public class UpdateStatementWithoutWhereClause<T> internal constructor(
    preSQLStr: String,
    internal val statementContainer: StatementContainer,
    internal val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
) : SingleStatement(preSQLStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * UPDATE or DELETE statement with WHERE clause applied (final form).
 *
 * Represents a complete UPDATE or DELETE operation ready for execution. The WHERE clause
 * has already been applied, so the statement targets specific rows.
 *
 * @author Yuang Qiao
 */
public class UpdateDeleteStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeUpdateDelete(sqlStr, params)
}

/**
 * INSERT statement (final form).
 *
 * Represents a complete INSERT operation with entities encoded as parameterized VALUES.
 * Executes as a single batch insert for all entities.
 *
 * @author Yuang Qiao
 */
public class InsertStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
    override val parameters: MutableList<Any?>?,
) : SingleStatement(sqlStr) {
    public override fun execute(): Unit = connection.executeInsert(sqlStr, params)
}

/**
 * CREATE statement (final form).
 *
 * Represents a complete CREATE TABLE operation. Does not support parameterized queries
 * since DDL statements use direct SQL execution.
 *
 * @author Yuang Qiao
 */
public class CreateStatement internal constructor(
    sqlStr: String,
    private val connection: DatabaseConnection,
) : SingleStatement(sqlStr) {
    override fun execute(): Unit = connection.execSQL(sqlStr, params)
    override val parameters: MutableList<Any?>? = null
}