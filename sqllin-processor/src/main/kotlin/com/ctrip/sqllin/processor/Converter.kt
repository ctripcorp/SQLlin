package com.ctrip.sqllin.processor

/**
 * Converts a [Trigger][com.ctrip.sqllin.dsl.annotation.Trigger] enum name to its SQL representation.
 *
 * This function transforms the Kotlin enum constant name (using underscore separators)
 * into the corresponding SQL syntax (using space separators).
 *
 * ### Examples
 * ```kotlin
 * "ON_DELETE_CASCADE".triggerNameToSQL()  // Returns: "ON DELETE CASCADE"
 * "ON_UPDATE_SET_NULL".triggerNameToSQL() // Returns: "ON UPDATE SET NULL"
 * "ON_DELETE_RESTRICT".triggerNameToSQL() // Returns: "ON DELETE RESTRICT"
 * ```
 *
 * ### Usage
 * This function is used internally by [ForeignKeyParser] during CREATE TABLE statement
 * generation to convert [Trigger][com.ctrip.sqllin.dsl.annotation.Trigger] enum values
 * into valid SQLite syntax.
 *
 * @receiver The trigger enum name (e.g., "ON_DELETE_CASCADE")
 * @return The SQL representation with underscores replaced by spaces (e.g., "ON DELETE CASCADE")
 */
fun String.triggerNameToSQL(): String = replace('_', ' ')