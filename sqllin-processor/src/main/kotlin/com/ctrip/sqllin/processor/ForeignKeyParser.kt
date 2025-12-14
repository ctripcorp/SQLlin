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

package com.ctrip.sqllin.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration

/**
 * Parser for foreign key constraint annotations during code generation.
 *
 * This class processes [@ForeignKeyGroup][com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup],
 * [@ForeignKey][com.ctrip.sqllin.dsl.annotation.ForeignKey], and
 * [@References][com.ctrip.sqllin.dsl.annotation.References] annotations to generate
 * the appropriate SQLite FOREIGN KEY clauses in CREATE TABLE statements.
 *
 * ### Processing Workflow
 * 1. **Parse class-level annotations**: [parseGroups] extracts [@ForeignKeyGroup] metadata
 * 2. **Parse property annotations**: [parseColumnAnnotations] processes [@ForeignKey] and [@References]
 * 3. **Generate SQL**: [generateCodeForForeignKey] appends FOREIGN KEY clauses to CREATE TABLE
 *
 * ### Supported Annotation Patterns
 *
 * #### Pattern 1: Column-level with @References
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
 *     val userId: Long
 * )
 * // Generated: userId BIGINT REFERENCES User(id) ON DELETE CASCADE
 * ```
 *
 * #### Pattern 2: Table-level with @ForeignKeyGroup + @ForeignKey
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(group = 0, tableName = "Product", trigger = Trigger.ON_DELETE_CASCADE)
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "categoryId") val category: Int,
 *     @ForeignKey(group = 0, reference = "code") val productCode: String
 * )
 * // Generated: FOREIGN KEY (category,productCode) REFERENCES Product(categoryId,code) ON DELETE CASCADE
 * ```
 *
 * ### Validation Rules
 * - [@ForeignKeyGroup] groups must have unique group numbers
 * - [@ForeignKey] annotations must reference a declared [@ForeignKeyGroup]
 * - Properties with `ON_DELETE_SET_NULL` or `ON_UPDATE_SET_NULL` must be nullable
 * - [@References] foreignKeys array cannot be empty
 * - Foreign key groups must have at least one [@ForeignKey] property
 *
 * @author Yuang Qiao
 * @see com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup
 * @see com.ctrip.sqllin.dsl.annotation.ForeignKey
 * @see com.ctrip.sqllin.dsl.annotation.References
 * @see com.ctrip.sqllin.dsl.annotation.Trigger
 */
class ForeignKeyParser {

    companion object {
        const val ANNOTATION_GROUP = "com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup"
        const val ANNOTATION_REFERENCES = "com.ctrip.sqllin.dsl.annotation.References"
        const val ANNOTATION_FOREIGN_KEY = "com.ctrip.sqllin.dsl.annotation.ForeignKey"
    }

    /**
     * Map of group number to foreign key metadata.
     * Populated by [parseGroups] and consumed by [generateCodeForForeignKey].
     */
    private val groupMap = HashMap<Int, ForeignKeyEntity>()

    /**
     * Parses class-level [@ForeignKeyGroup] annotations and stores their metadata.
     *
     * This method extracts foreign key group definitions from class annotations,
     * including the referenced table name, trigger actions, and optional constraint names.
     * The parsed metadata is stored in [groupMap] for later use by [generateCodeForForeignKey].
     *
     * ### Example
     * ```kotlin
     * @ForeignKeyGroup(
     *     group = 0,
     *     tableName = "User",
     *     trigger = Trigger.ON_DELETE_CASCADE,
     *     constraintName = "fk_order_user"
     * )
     * data class Order(...)
     * ```
     *
     * ### Validation
     * - Ensures `tableName` is not blank or empty
     * - Validates that group numbers are unique (no duplicates)
     * - Converts [Trigger] enum values to SQL strings
     *
     * @param annotations Sequence of class-level annotations to process
     * @throws IllegalArgumentException if tableName is blank or group number is duplicated
     */
    fun parseGroups(annotations: Sequence<KSAnnotation>) {
        annotations.forEach { annotation ->
            if (annotation.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_GROUP) {
                var group = 0
                var tableName = ""
                var triggerEnumName = ""
                var triggerSQL = ""
                var constraintName = ""
                annotation.arguments.forEach { argument ->
                    when (argument.name?.asString()) {
                        "group" -> group = argument.value as Int
                        "tableName" -> tableName = (argument.value as String).ifBlank {
                            throw IllegalArgumentException("The parameter `tableName` in @ForeignKeyGroup can't be blank or empty.")
                        }
                        "trigger" -> {
                            val declaration = argument.value as? KSClassDeclaration
                            if (declaration != null && declaration.classKind == ClassKind.ENUM_ENTRY) {
                                triggerEnumName = declaration.simpleName.asString()
                                if (triggerEnumName != "NULL") {
                                    triggerSQL = triggerEnumName.triggerNameToSQL()
                                }
                            }
                        }
                        "constraintName" -> constraintName = argument.value as String
                    }
                }

                // Validate for duplicate groups
                if (groupMap.containsKey(group)) {
                    throw IllegalArgumentException("Duplicate foreign key group `$group` declaration found.")
                }

                groupMap[group] = ForeignKeyEntity(
                    tableName = tableName,
                    triggerEnumName = triggerEnumName,
                    triggerSQL = triggerSQL,
                    constraintName = constraintName,
                    columns = ArrayList(),
                    references = ArrayList(),
                )
            }
        }
    }

    /**
     * Processes property-level foreign key annotations and generates SQL constraints.
     *
     * This method handles both [@References] (column-level) and [@ForeignKey] (table-level)
     * annotations on properties. For @References, it directly appends the REFERENCES clause
     * to the column definition. For @ForeignKey, it accumulates metadata in [groupMap] for
     * later processing by [generateCodeForForeignKey].
     *
     * ### @References Processing
     * Generates inline column-level foreign key constraint:
     * ```kotlin
     * @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
     * val userId: Long
     * // Generated: userId BIGINT REFERENCES User(id) ON DELETE CASCADE
     * ```
     *
     * ### @ForeignKey Processing
     * Accumulates metadata for table-level constraint generation:
     * ```kotlin
     * @ForeignKey(group = 0, reference = "id")
     * val userId: Long
     * // Later generates: FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE
     * ```
     *
     * ### Validation
     * - Ensures `tableName` is not blank
     * - Validates that `foreignKeys` array is not empty
     * - Checks that properties with SET_NULL triggers are nullable
     * - Verifies that referenced [@ForeignKeyGroup] exists
     *
     * @param createSQLBuilder StringBuilder to append SQL fragments to (for @References only)
     * @param annotations Sequence of property annotations to process
     * @param propertyName The name of the property being processed
     * @param isNotNull Whether the property is non-nullable
     * @throws IllegalArgumentException if validation fails or referenced group doesn't exist
     */
    @Suppress("UNCHECKED_CAST")
    fun parseColumnAnnotations(
        createSQLBuilder: StringBuilder,
        annotations: Sequence<KSAnnotation>,
        propertyName: String,
        isNotNull: Boolean,
    ) {
        annotations.forEach { annotation ->
            when (annotation.annotationType.resolve().declaration.qualifiedName?.asString()) {
                ANNOTATION_REFERENCES -> {
                    var tableName = ""
                    var triggerEnumName: String
                    var triggerSQL = ""
                    var constraintName = ""
                    var foreignKeys = emptyList<String>()
                    annotation.arguments.forEach { argument ->
                        when (argument.name?.asString()) {
                            "tableName" -> tableName = (argument.value as String).ifBlank {
                                throw IllegalArgumentException("The parameter `tableName` can't be blank or empty.")
                            }
                            "trigger" -> {
                                val declaration = argument.value as? KSClassDeclaration
                                if (declaration != null && declaration.classKind == ClassKind.ENUM_ENTRY) {
                                    triggerEnumName = declaration.simpleName.asString()
                                    if ((triggerEnumName == "ON_DELETE_SET_NULL" || triggerEnumName == "ON_UPDATE_SET_NULL") && isNotNull) {
                                        throw IllegalArgumentException("Can't use trigger `ON_DELETE_SET_NULL` or `ON_UPDATE_SET_NULL` on a non-null property.")
                                    }
                                    if (triggerEnumName != "NULL") {
                                        triggerSQL = triggerEnumName.triggerNameToSQL()
                                    }
                                }
                            }
                            "constraintName" -> constraintName = argument.value as String
                            "foreignKeys" -> {
                                foreignKeys = (argument.value as? List<String>)?.filter { it.isNotBlank() }
                                    ?: throw IllegalArgumentException("The parameter `foreignKeys` can't be null.")
                                if (foreignKeys.isEmpty()) {
                                    throw IllegalArgumentException("The parameter `foreignKeys` can't be empty or contain only blank values.")
                                }
                            }
                        }
                    }
                    with(createSQLBuilder) {
                        if (constraintName.isNotEmpty()) {
                            append(" CONSTRAINT ")
                            append(constraintName)
                        }
                        append(" REFERENCES ")
                        append(tableName)
                        append('(')
                        append(foreignKeys.first())
                        for (i in 1 ..< foreignKeys.size) {
                            append(',')
                            append(foreignKeys[i])
                        }
                        append(')')
                        if (triggerSQL.isNotEmpty()) {
                            append(' ')
                            append(triggerSQL)
                        }
                    }
                }
                ANNOTATION_FOREIGN_KEY -> {
                    var group = 0
                    var reference = ""
                    annotation.arguments.forEach { argument ->
                        when (argument.name?.asString()) {
                            "group" -> group = argument.value as Int
                            "reference" -> reference = (argument.value as String).ifBlank {
                                throw IllegalArgumentException("The `reference` can't be blank.")
                            }
                        }
                    }
                    val entity = groupMap[group] ?: throw IllegalArgumentException("Foreign key group `$group` hasn't been declared with @ForeignKeyGroup annotation.")
                    with(entity) {
                        if ((triggerEnumName == "ON_DELETE_SET_NULL" || triggerEnumName == "ON_UPDATE_SET_NULL") && isNotNull) {
                            throw IllegalArgumentException("Can't use trigger `ON_DELETE_SET_NULL` or `ON_UPDATE_SET_NULL` on a non-null property in foreign key group `$group`.")
                        }
                        columns.add(propertyName)
                        references.add(reference)
                    }
                }
            }
        }
    }

    /**
     * Generates table-level FOREIGN KEY clauses and appends them to the CREATE TABLE statement.
     *
     * This method processes all foreign key groups accumulated by [parseColumnAnnotations]
     * and generates the corresponding FOREIGN KEY constraints at the table level. Each group
     * is converted into a SQL clause of the form:
     * ```sql
     * FOREIGN KEY (col1, col2) REFERENCES ParentTable(ref1, ref2) ON DELETE CASCADE
     * ```
     *
     * ### Example Output
     * For a class with two foreign key groups:
     * ```kotlin
     * @ForeignKeyGroup(group = 0, tableName = "User", trigger = Trigger.ON_DELETE_CASCADE)
     * @ForeignKeyGroup(group = 1, tableName = "Product", trigger = Trigger.ON_DELETE_RESTRICT)
     * data class OrderItem(
     *     @ForeignKey(group = 0, reference = "id") val userId: Long,
     *     @ForeignKey(group = 1, reference = "id") val productId: Long
     * )
     * ```
     * Generates:
     * ```sql
     * ,FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE
     * ,FOREIGN KEY (productId) REFERENCES Product(id) ON DELETE RESTRICT
     * ```
     *
     * ### Validation
     * - Ensures each group has at least one [@ForeignKey] property
     * - Verifies that the number of columns matches the number of references
     *
     * @param createSQLBuilder StringBuilder containing the CREATE TABLE statement to append to
     * @throws IllegalArgumentException if a foreign key group is declared but has no properties
     */
    fun generateCodeForForeignKey(createSQLBuilder: StringBuilder) {
        if (groupMap.isEmpty())
            return
        with(createSQLBuilder) {
            groupMap.forEach { (groupNum, entity) ->
                // Validate entity has columns
                if (entity.columns.isEmpty()) {
                    throw IllegalArgumentException("Foreign key group `$groupNum` was declared but no columns reference it with @ForeignKey annotation.")
                }

                // Validate columns and references match
                if (entity.columns.size != entity.references.size) {
                    throw IllegalArgumentException("Internal error: columns and references size mismatch in foreign key group `$groupNum`.")
                }

                if (entity.constraintName.isNotEmpty()) {
                    append(",CONSTRAINT ")
                    append(entity.constraintName)
                    append(' ')
                } else {
                    append(',')
                }
                append("FOREIGN KEY (")

                append(entity.columns.first())
                for (i in 1 ..< entity.columns.size) {
                    append(',')
                    append(entity.columns[i])
                }

                append(") REFERENCES ")
                append(entity.tableName)
                append('(')
                append(entity.references.first())
                for (i in 1 ..< entity.references.size) {
                    append(',')
                    append(entity.references[i])
                }
                append(')')

                if (entity.triggerSQL.isNotEmpty()) {
                    append(' ')
                    append(entity.triggerSQL)
                }
            }
        }
    }

    /**
     * Internal data class representing a single foreign key constraint group.
     *
     * This class stores metadata for a foreign key constraint, including the
     * referenced table, trigger actions, and the mapping between local columns
     * and referenced columns.
     *
     * @property tableName The name of the parent table being referenced
     * @property triggerEnumName The enum name of the trigger (e.g., "ON_DELETE_CASCADE")
     * @property triggerSQL The SQL representation of the trigger (e.g., "ON DELETE CASCADE")
     * @property constraintName Optional name for the constraint
     * @property columns List of local column names participating in this foreign key
     * @property references List of referenced column names in the parent table (parallel to [columns])
     */
    private class ForeignKeyEntity(
        val tableName: String,
        val triggerEnumName: String,
        val triggerSQL: String,
        val constraintName: String,
        val columns: MutableList<String>,
        val references: MutableList<String>,
    )
}