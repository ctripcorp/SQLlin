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
 * Base interface for SQL statements that can be executed against the database.
 *
 * Implementations include:
 * - [SingleStatement]: Individual SQL operations (INSERT, UPDATE, DELETE, CREATE, SELECT)
 * - [TransactionStatementsGroup]: Multiple statements wrapped in a transaction
 *
 * Statements are collected during DSL building and executed when the [com.ctrip.sqllin.dsl.DatabaseScope]
 * exits.
 *
 * @author Yuang Qiao
 */
public sealed interface ExecutableStatement {
    /**
     * Executes this statement against the database.
     *
     * For single statements, this runs the SQL directly.
     * For statement groups, this executes all contained statements in order.
     */
    public fun execute()
}