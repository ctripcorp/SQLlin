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

package com.ctrip.sqllin.dsl.sql.operation

import com.ctrip.sqllin.driver.DatabaseConnection
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * SQLite PRAGMA command operations for database configuration.
 *
 * This object provides methods to generate PRAGMA SQL statements that configure
 * various SQLite database settings. PRAGMA statements are special SQLite commands
 * that query or modify database operation parameters.
 *
 * ### Available PRAGMAs
 * - **foreign_keys**: Enable or disable foreign key constraint enforcement
 *
 * ### Usage
 * This object is used internally by [DatabaseScope][com.ctrip.sqllin.dsl.DatabaseScope]
 * to generate PRAGMA statements when calling functions like `PRAGMA_FOREIGN_KEYS`.
 *
 * @author Yuang Qiao
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
internal object PRAGMA : Operation {

    override val sqlStr: String
        get() = "PRAGMA "

    /**
     * Generates a PRAGMA statement to enable or disable foreign key constraint enforcement.
     *
     * SQLite disables foreign key constraints by default for backward compatibility.
     * This method creates a statement that enables or disables foreign key enforcement
     * for the current database connection.
     *
     * ### Important Notes
     * - This setting is **per-connection** and must be set each time a database is opened
     * - The setting **cannot be changed** inside a transaction
     * - When enabled, all INSERT, UPDATE, and DELETE operations will enforce foreign key constraints
     * - When disabled, foreign key constraints are part of the schema but not enforced
     *
     * ### Generated SQL
     * ```sql
     * PRAGMA foreign_keys=1;  -- Enable foreign keys
     * PRAGMA foreign_keys=0;  -- Disable foreign keys
     * ```
     *
     * ### Example Usage
     * ```kotlin
     * database {
     *     PRAGMA_FOREIGN_KEYS(true)  // Enable enforcement
     *     CREATE(OrderTable)  // Create table with foreign keys
     *
     *     // Now foreign key constraints will be enforced
     *     OrderTable INSERT Order(userId = 999)  // Fails if user 999 doesn't exist
     * }
     * ```
     *
     * @param enable `true` to enable foreign key enforcement, `false` to disable
     * @param connection The database connection to execute the statement on
     * @return A [SingleStatement] that executes the PRAGMA command
     *
     * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
     * @see com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup
     * @see com.ctrip.sqllin.dsl.annotation.References
     */
    fun foreignKeys(enable: Boolean, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append("foreign_keys=")
            append(if (enable) "1;" else "0;")
        }
        return TableStructureStatement(sql, connection)
    }
}