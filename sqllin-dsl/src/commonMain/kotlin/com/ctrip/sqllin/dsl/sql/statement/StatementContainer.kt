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
 * Used by statement builders to replace a statement when DSL operations refine or extend it. For example, when an
 * UPDATE statement adds a WHERE clause, it replaces the initial UPDATE statement, wherever it is among the statements,
 * rather than the last one, which another statement built since may be. A SELECT that becomes part of an
 * `INSERT INTO ... SELECT` is removed, as it no longer runs on its own.
 *
 * Implementations:
 * - [DatabaseExecuteEngine]: Executes standalone statements
 * - [TransactionStatementsGroup]: Groups statements within a transaction
 *
 * @author Yuang Qiao
 */
internal interface StatementContainer {

    /**
     * Removes [statement] if this container holds it, wherever it is.
     */
    infix fun removeStatement(statement: SingleStatement)

    /**
     * Replaces [statement] with [newStatement], where [statement] is, as when a clause completes it.
     *
     * @throws IllegalStateException if this container doesn't hold [statement]: a clause was already appended to it,
     * which replaced it, or it became part of another statement, as a subquery does
     */
    fun replaceStatement(statement: SingleStatement, newStatement: SingleStatement)
}