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
import com.ctrip.sqllin.dsl.sql.Table
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
    fun drop(table: Table<*>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
        }
        return TableStructureStatement(sql, connection)
    }
}