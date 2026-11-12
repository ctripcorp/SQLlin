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
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.sql.Table
import com.ctrip.sqllin.dsl.sql.View
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * DROP operation for removing database tables.
 *
 * Generates SQL DROP TABLE statements to remove tables from the database.
 * This is a destructive operation that permanently deletes the table and all its data.
 *
 * Usage:
 * ```kotlin
 * database {
 *     DROP(PersonTable)
 *     // or
 *     PersonTable.DROP()
 * }
 * ```
 *
 * @see com.ctrip.sqllin.dsl.DatabaseScope.DROP
 * @author Yuang Qiao
 */
internal object Drop : Operation {

    override val sqlStr: String
        get() = "DROP TABLE "

    /**
     * Creates a DROP TABLE statement for the specified table.
     *
     * @param table The table to drop
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the DROP TABLE operation
     */
    fun drop(table: Table<*>, connection: DatabaseConnection, isIfExists: Boolean = false): SingleStatement =
        dropTable(table.tableName, connection, isIfExists)

    /**
     * Builds a DROP TABLE statement for the table named [tableName], which needs no [Table].
     */
    fun dropTable(tableName: String, connection: DatabaseConnection, isIfExists: Boolean = false): SingleStatement {
        require(tableName.isNotBlank()) { "The name of a table can't be blank." }
        val sql = buildString {
            append(sqlStr)
            if (isIfExists)
                append(IF_EXISTS)
            append(tableName)
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Creates a DROP VIEW statement for the specified view.
     *
     * @param view The view to drop
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the DROP VIEW operation
     */
    @OptIn(ExperimentalDSLDatabaseAPI::class)
    fun dropView(view: View<*>, connection: DatabaseConnection, isIfExists: Boolean = false): SingleStatement =
        dropView(view.tableName, connection, isIfExists)

    /**
     * Builds a DROP VIEW statement for the view named [viewName], which needs no [View].
     */
    fun dropView(viewName: String, connection: DatabaseConnection, isIfExists: Boolean = false): SingleStatement {
        require(viewName.isNotBlank()) { "The name of a view can't be blank." }
        return TableStructureStatement("DROP VIEW ${if (isIfExists) IF_EXISTS else ""}$viewName", connection)
    }

    /**
     * Builds a DROP INDEX statement for the index named [indexName].
     */
    fun dropIndex(indexName: String, connection: DatabaseConnection, isIfExists: Boolean = false): SingleStatement =
        TableStructureStatement("DROP INDEX ${if (isIfExists) IF_EXISTS else ""}$indexName", connection)

    private const val IF_EXISTS = "IF EXISTS "
}
