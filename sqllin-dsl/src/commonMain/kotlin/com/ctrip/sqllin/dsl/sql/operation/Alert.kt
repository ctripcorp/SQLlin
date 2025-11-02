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
import com.ctrip.sqllin.dsl.sql.clause.ClauseElement
import com.ctrip.sqllin.dsl.sql.statement.SingleStatement
import com.ctrip.sqllin.dsl.sql.statement.TableStructureStatement

/**
 * ALERT (ALTER) operation for modifying database table structures.
 *
 * Note: This is named "Alert" but generates SQL ALTER TABLE statements. The naming follows
 * the existing codebase convention.
 *
 * Supports common table modification operations:
 * - **ADD COLUMN**: Add a new column to an existing table
 * - **RENAME TABLE**: Rename a table to a new name
 * - **RENAME COLUMN**: Rename a column within a table
 * - **DROP COLUMN**: Remove a column from a table
 *
 * Usage examples:
 * ```kotlin
 * database {
 *     // Add a new column
 *     PersonTable ALERT_ADD_COLUMN email
 *
 *     // Rename table
 *     PersonTable ALERT_RENAME_TABLE_TO NewPersonTable
 *     // or from old name
 *     "old_person" ALERT_RENAME_TABLE_TO NewPersonTable
 *
 *     // Rename column
 *     PersonTable.RENAME_COLUMN(oldName, newName)
 *     // or with ClauseElement
 *     PersonTable.RENAME_COLUMN(PersonTable.age, PersonTable.yearsOld)
 *
 *     // Drop column
 *     PersonTable DROP_COLUMN PersonTable.email
 * }
 * ```
 *
 * @see com.ctrip.sqllin.dsl.DatabaseScope.ALERT_ADD_COLUMN
 * @see com.ctrip.sqllin.dsl.DatabaseScope.ALERT_RENAME_TABLE_TO
 * @see com.ctrip.sqllin.dsl.DatabaseScope.RENAME_COLUMN
 * @see com.ctrip.sqllin.dsl.DatabaseScope.DROP_COLUMN
 * @author Yuang Qiao
 */
internal object Alert : Operation {

    override val sqlStr: String
        get() = "ALERT TABLE "

    private const val ADD_COLUMN = " ADD COLUMN "
    private const val RENAME_TABLE = " RENAME TO "
    private const val RENAME_COLUMN = " RENAME COLUMN "
    private const val DROP_COLUMN = " DROP COLUMN "

    /**
     * Creates an ALTER TABLE ADD COLUMN statement.
     *
     * Adds a new column to an existing table.
     *
     * @param table The table to modify
     * @param newColumn The column definition to add
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the ADD COLUMN operation
     */
    fun addColumn(table: Table<*>, newColumn: ClauseElement, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(ADD_COLUMN)
            append(newColumn.valueName)
            val propertyDescriptor = table.kSerializer().descriptor
            val index = propertyDescriptor.getElementIndex(newColumn.valueName)
            val descriptor = propertyDescriptor.getElementDescriptor(index)
            append(FullNameCache.getSerialNameBySerialName(descriptor, newColumn.valueName, table))
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Creates an ALTER TABLE RENAME TO statement.
     *
     * Renames an existing table to a new name.
     *
     * @param oldName The current name of the table to rename
     * @param newTable The new table definition containing the target name
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the RENAME TABLE operation
     */
    fun renameTable(oldName: String, newTable: Table<*>, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(oldName)
            append(RENAME_TABLE)
            append(newTable.tableName)
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Creates an ALTER TABLE RENAME COLUMN statement.
     *
     * Renames an existing column within a table.
     *
     * @param table The table containing the column to rename
     * @param oldName The current name of the column
     * @param newColumn The new column definition containing the target name
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the RENAME COLUMN operation
     */
    fun renameColumn(table: Table<*>, oldName: String, newColumn: ClauseElement, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(RENAME_COLUMN)
            append(oldName)
            append(" TO ")
            append(newColumn.valueName)
        }
        return TableStructureStatement(sql, connection)
    }

    /**
     * Creates an ALTER TABLE DROP COLUMN statement.
     *
     * Removes a column from an existing table.
     *
     * @param table The table containing the column to drop
     * @param column The column to remove
     * @param connection The database connection for executing the statement
     * @return A [TableStructureStatement] representing the DROP COLUMN operation
     */
    fun dropColumn(table: Table<*>, column: ClauseElement, connection: DatabaseConnection): SingleStatement {
        val sql = buildString {
            append(sqlStr)
            append(table.tableName)
            append(DROP_COLUMN)
            append(column.valueName)
        }
        return TableStructureStatement(sql, connection)
    }
}