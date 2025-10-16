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

/**
 * Container for managing and modifying SQL statements.
 *
 * Used by statement builders (e.g., UPDATE, JOIN) to replace or update the last statement
 * in a collection when DSL operations refine or extend it. For example, when an UPDATE
 * statement adds a WHERE clause, it replaces the initial UPDATE statement.
 *
 * Implementations:
 * - [DatabaseExecuteEngine]: Executes standalone statements
 * - [TransactionStatementsGroup]: Groups statements within a transaction
 * - [UnionSelectStatementGroup]: Accumulates SELECT statements for UNION operations
 *
 * @author Yuang Qiao
 */
internal fun interface StatementContainer {

    /**
     * Replaces the most recently added statement with a modified version.
     *
     * Used when DSL operations progressively build up a statement (e.g., adding WHERE to UPDATE).
     */
    infix fun changeLastStatement(statement: SingleStatement)
}