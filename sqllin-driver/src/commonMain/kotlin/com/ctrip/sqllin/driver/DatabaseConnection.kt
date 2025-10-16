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

package com.ctrip.sqllin.driver

/**
 * Platform-agnostic interface for SQLite database connections.
 *
 * Provides a common API for executing SQL statements and managing transactions
 * across all supported platforms.
 *
 * @author Yuang Qiao
 */
public interface DatabaseConnection {

    /**
     * Executes a SQL statement that doesn't return data.
     *
     * @param sql The SQL statement to execute
     * @param bindParams Optional parameters to bind to the statement
     */
    public fun execSQL(sql: String, bindParams: Array<out Any?>? = null)

    /**
     * Executes an INSERT statement.
     *
     * @param sql The INSERT statement
     * @param bindParams Optional parameters to bind to the statement
     */
    public fun executeInsert(sql: String, bindParams: Array<out Any?>? = null)

    /**
     * Executes an UPDATE or DELETE statement.
     *
     * @param sql The UPDATE or DELETE statement
     * @param bindParams Optional parameters to bind to the statement
     */
    public fun executeUpdateDelete(sql: String, bindParams: Array<out Any?>? = null)

    /**
     * Executes a SELECT query and returns a cursor.
     *
     * @param sql The SELECT statement
     * @param bindParams Optional string parameters to bind to the query
     * @return A cursor for iterating over query results
     */
    public fun query(sql: String, bindParams: Array<out String?>? = null): CommonCursor

    /**
     * Begins a database transaction.
     */
    public fun beginTransaction()

    /**
     * Marks the current transaction as successful.
     *
     * Must be called before [endTransaction] to commit changes.
     */
    public fun setTransactionSuccessful()

    /**
     * Ends the current transaction, committing if marked successful.
     */
    public fun endTransaction()

    /**
     * Closes the database connection and releases resources.
     */
    public fun close()

    /**
     * Whether this connection is closed.
     */
    public val isClosed: Boolean
}
