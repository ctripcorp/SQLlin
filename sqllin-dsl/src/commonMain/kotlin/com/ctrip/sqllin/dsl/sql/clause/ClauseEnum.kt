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

package com.ctrip.sqllin.dsl.sql.clause

import com.ctrip.sqllin.dsl.sql.Table

/**
 * Wrapper for enum column references in SQL clauses.
 *
 * Enables type-safe enum comparisons in WHERE, HAVING, and other conditional clauses.
 * Enums are stored as integers (ordinal values) in SQLite and automatically converted
 * during serialization/deserialization.
 *
 * Available operators:
 * - `lt`: Less than (<) - compares ordinal values
 * - `lte`: Less than or equal to (<=) - compares ordinal values
 * - `eq`: Equals (=) - handles null with IS NULL
 * - `neq`: Not equals (!=) - handles null with IS NOT NULL
 * - `gt`: Greater than (>) - compares ordinal values
 * - `gte`: Greater than or equal to (>=) - compares ordinal values
 *
 * Example usage:
 * ```kotlin
 * enum class UserStatus { ACTIVE, INACTIVE, BANNED }
 *
 * @Serializable
 * @DBRow
 * data class User(val id: Long?, val name: String, val status: UserStatus)
 *
 * database {
 *     // Query with enum comparison
 *     UserTable SELECT WHERE(UserTable.status EQ UserStatus.ACTIVE)
 *
 *     // Compare against another enum column
 *     UserTable SELECT WHERE(UserTable.status EQ UserTable.previousStatus)
 *
 *     // Greater than comparison (ordinal-based)
 *     UserTable SELECT WHERE(UserTable.status GT UserStatus.ACTIVE)
 * }
 * ```
 *
 * @param T The enum type this clause operates on
 * @property valueName The column name
 * @property table The table this element belongs to
 *
 * @author Yuang Qiao
 */
public class ClauseEnum<T : Enum<T>>(
    valueName: String,
    table: Table<*>,
) : ClauseElement(valueName, table, false) {

    /**
     * Less than (<) comparison using the enum's ordinal value.
     *
     * Generates: `column < ?` with the enum's ordinal as parameter
     *
     * @param entry The enum entry to compare against
     * @return SelectCondition with placeholder and bound ordinal parameter
     */
    internal infix fun lt(entry: T): SelectCondition = appendEnum("<?", entry)

    /**
     * Less than (<) comparison against another enum column.
     *
     * Generates: `column1 < column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun lt(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum("<", clauseEnum)

    /**
     * Less than or equal (<=) comparison using the enum's ordinal value.
     *
     * Generates: `column <= ?` with the enum's ordinal as parameter
     *
     * @param entry The enum entry to compare against
     * @return SelectCondition with placeholder and bound ordinal parameter
     */
    internal infix fun lte(entry: T): SelectCondition = appendEnum("<=?", entry)

    /**
     * Less than or equal (<=) comparison against another enum column.
     *
     * Generates: `column1 <= column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun lte(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum("<=", clauseEnum)

    /**
     * Equals (=) comparison using the enum's ordinal value, or IS NULL for null values.
     *
     * Generates: `column = ?` or `column IS NULL`
     *
     * @param entry The enum entry to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound ordinal parameter
     */
    internal infix fun eq(entry: T?): SelectCondition = appendNullableEnum("=", " IS NULL", entry)

    /**
     * Equals (=) comparison against another enum column.
     *
     * Generates: `column1 = column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun eq(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum("=", clauseEnum)

    /**
     * Not equals (!=) comparison using the enum's ordinal value, or IS NOT NULL for null values.
     *
     * Generates: `column != ?` or `column IS NOT NULL`
     *
     * @param entry The enum entry to compare against, or null
     * @return SelectCondition with placeholder (if non-null) and bound ordinal parameter
     */
    internal infix fun neq(entry: T?): SelectCondition = appendNullableEnum("!=", " IS NOT NULL", entry)

    /**
     * Not equals (!=) comparison against another enum column.
     *
     * Generates: `column1 != column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun neq(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum("!=", clauseEnum)

    /**
     * Greater than (>) comparison using the enum's ordinal value.
     *
     * Generates: `column > ?` with the enum's ordinal as parameter
     *
     * @param entry The enum entry to compare against
     * @return SelectCondition with placeholder and bound ordinal parameter
     */
    internal infix fun gt(entry: T): SelectCondition = appendEnum(">?", entry)

    /**
     * Greater than (>) comparison against another enum column.
     *
     * Generates: `column1 > column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun gt(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum(">", clauseEnum)

    /**
     * Greater than or equal (>=) comparison using the enum's ordinal value.
     *
     * Generates: `column >= ?` with the enum's ordinal as parameter
     *
     * @param entry The enum entry to compare against
     * @return SelectCondition with placeholder and bound ordinal parameter
     */
    internal infix fun gte(entry: T): SelectCondition = appendEnum(">=?", entry)

    /**
     * Greater than or equal (>=) comparison against another enum column.
     *
     * Generates: `column1 >= column2`
     *
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition comparing two enum columns
     */
    internal infix fun gte(clauseEnum: ClauseEnum<T>): SelectCondition = appendClauseEnum(">=", clauseEnum)

    /**
     * Builds a comparison condition with an enum value using parameterized binding.
     *
     * Generates SQL: `table.column<symbol>` with the enum's ordinal as a parameter.
     *
     * @param symbol The comparison operator with placeholder (e.g., "<?", ">=?")
     * @param entry The enum entry whose ordinal will be bound as a parameter
     * @return SelectCondition with SQL and ordinal parameter
     */
    private fun appendEnum(symbol: String, entry: T): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(symbol)
        }
        return SelectCondition(sql, mutableListOf(entry.ordinal))
    }

    /**
     * Builds a comparison condition for nullable enum values.
     *
     * For non-null values, generates: `table.column<notNullSymbol>?` with ordinal as parameter.
     * For null values, generates: `table.column<nullSymbol>` (e.g., " IS NULL").
     *
     * @param notNullSymbol The comparison operator for non-null values (e.g., "=", "!=")
     * @param nullSymbol The SQL fragment for null comparison (e.g., " IS NULL", " IS NOT NULL")
     * @param entry The enum entry to compare against, or null
     * @return SelectCondition with appropriate SQL and optional ordinal parameter
     */
    private fun appendNullableEnum(notNullSymbol: String, nullSymbol: String, entry: T?): SelectCondition {
        val builder = StringBuilder()
        builder.append(table.tableName)
        builder.append('.')
        builder.append(valueName)
        val parameters = if (entry == null){
            builder.append(nullSymbol)
            null
        } else {
            builder.append(notNullSymbol)
            builder.append('?')
            mutableListOf<Any?>(entry.ordinal)
        }
        return SelectCondition(builder.toString(), parameters)
    }

    /**
     * Builds a comparison condition between two enum columns.
     *
     * Generates SQL: `table1.column1<symbol>table2.column2` with no parameters.
     * Both columns are referenced directly in the SQL without binding.
     *
     * @param symbol The comparison operator (e.g., "<", "=", ">=")
     * @param clauseEnum The enum column to compare against
     * @return SelectCondition with SQL comparing two columns
     */
    private fun appendClauseEnum(symbol: String, clauseEnum: ClauseEnum<T>): SelectCondition {
        val sql = buildString {
            append(table.tableName)
            append('.')
            append(valueName)
            append(symbol)
            append(clauseEnum.table.tableName)
            append('.')
            append(clauseEnum.valueName)
        }
        return SelectCondition(sql, null)
    }

    override fun hashCode(): Int = valueName.hashCode() + table.tableName.hashCode()
    override fun equals(other: Any?): Boolean = (other as? ClauseEnum<*>)?.let {
        it.valueName == valueName && it.table.tableName == table.tableName
    } ?: false
}