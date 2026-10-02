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

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeAlias
import java.io.Writer

/**
 * Parser for column constraint annotations during CREATE TABLE statement generation.
 *
 * This class processes primary key, uniqueness, and collation annotations on properties
 * to generate the appropriate SQLite column constraints in CREATE TABLE statements.
 * It was extracted from [ClauseProcessor] to improve code organization and separation of concerns.
 *
 * ### Processing Workflow
 * 1. **Parse property annotations**: [parseProperty] extracts constraint metadata and appends SQL
 * 2. **Generate metadata**: [generateCodeForPrimaryKey] creates runtime metadata and table-level constraints
 *
 * ### Supported Annotations
 *
 * #### Primary Keys
 * - **[@PrimaryKey][com.ctrip.sqllin.dsl.annotation.PrimaryKey]**: Single-column primary key with optional AUTOINCREMENT
 * - **[@CompositePrimaryKey][com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey]**: Multi-column primary key (table-level)
 *
 * #### Uniqueness Constraints
 * - **[@Unique][com.ctrip.sqllin.dsl.annotation.Unique]**: Single-column UNIQUE constraint
 * - **[@CompositeUnique][com.ctrip.sqllin.dsl.annotation.CompositeUnique]**: Multi-column UNIQUE constraint with group support
 *
 * #### Collation
 * - **[@CollateNoCase][com.ctrip.sqllin.dsl.annotation.CollateNoCase]**: Case-insensitive text comparison (for String/Char only)
 *
 * ### Example Usage
 * ```kotlin
 * // In ClauseProcessor
 * val parser = ColumnConstraintParser(resolver)
 *
 * // For each property:
 * parser.parseProperty(sqlBuilder, property, "userId", isNotNull = true)
 * // Appends: " BIGINT NOT NULL"
 *
 * // After all properties processed:
 * parser.generateCodeForPrimaryKey(writer, sqlBuilder)
 * // Generates: override val primaryKeyInfo = PrimaryKeyInfo(...)
 * // Appends: ",PRIMARY KEY(col1,col2)" for composite keys
 * ```
 *
 * ### Validation Rules
 * - Cannot use both [@PrimaryKey] and [@CompositePrimaryKey] on the same property
 * - Primary key properties must be nullable (SQLite rowid aliasing requirement)
 * - Only one [@PrimaryKey] annotation allowed per table
 * - AUTOINCREMENT requires Long type (mapped to INTEGER in SQLite)
 * - [@CollateNoCase] can only be applied to String or Char properties
 * - [@CompositePrimaryKey] properties must be non-nullable
 *
 * @param resolver KSP resolver for looking up annotation types
 *
 * @author Yuang Qiao
 * @see ClauseProcessor
 * @see com.ctrip.sqllin.dsl.annotation.PrimaryKey
 * @see com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
 * @see com.ctrip.sqllin.dsl.annotation.Unique
 * @see com.ctrip.sqllin.dsl.annotation.CompositeUnique
 * @see com.ctrip.sqllin.dsl.annotation.CollateNoCase
 */
class ColumnConstraintParser(resolver: Resolver) {

    private companion object {
        const val ANNOTATION_PRIMARY_KEY = "com.ctrip.sqllin.dsl.annotation.PrimaryKey"
        const val ANNOTATION_COMPOSITE_PRIMARY_KEY = "com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey"
        const val ANNOTATION_UNIQUE = "com.ctrip.sqllin.dsl.annotation.Unique"
        const val ANNOTATION_COMPOSITE_UNIQUE = "com.ctrip.sqllin.dsl.annotation.CompositeUnique"
        const val ANNOTATION_NO_CASE = "com.ctrip.sqllin.dsl.annotation.CollateNoCase"

        const val PROMPT_CANT_ADD_BOTH_ANNOTATION = "You can't add both @PrimaryKey and @CompositePrimaryKey to the same property."
        const val PROMPT_PRIMARY_KEY_MUST_NOT_NULL = "The primary key must be not-null."
        const val PROMPT_PRIMARY_KEY_TYPE = """The primary key's type must be Long when you set the the parameter "isAutoincrement = true" in annotation PrimaryKey."""
        const val PROMPT_PRIMARY_KEY_USE_COUNT = "You only could use PrimaryKey to annotate one property in a class."
        const val PROMPT_NO_CASE_MUST_FOR_TEXT = "You only could add annotation @CollateNoCase for a String or Char typed property."
    }

    private val primaryKeyAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_PRIMARY_KEY)!!.asStarProjectedType()
    private val compositePrimaryKeyName = resolver.getClassDeclarationByName(ANNOTATION_COMPOSITE_PRIMARY_KEY)!!.asStarProjectedType()
    private val noCaseAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_NO_CASE)!!.asStarProjectedType()
    private val uniqueAnnotationName = resolver.getClassDeclarationByName(ANNOTATION_UNIQUE)!!.asStarProjectedType()

    // Primary key tracking for metadata generation
    private var primaryKeyName: String? = null
    private var isAutomaticIncrement = false
    var isRowId = false
        private set
    private val compositePrimaryKeys = ArrayList<String>()
    private var isContainsPrimaryKey = false

    // Track composite unique constraints: group number → list of column names
    private val compositeUniqueColumns = HashMap<Int, MutableList<String>>()

    /**
     * Parses property annotations and appends corresponding SQLite constraints to the CREATE TABLE statement.
     *
     * This method processes all constraint-related annotations on a property and generates the appropriate
     * SQLite type declaration and constraint clauses. It accumulates metadata for table-level constraints
     * (composite primary keys and composite unique constraints) which are later output by [generateCodeForPrimaryKey].
     *
     * ### Generated SQL Patterns
     *
     * #### Basic Type with NOT NULL
     * ```kotlin
     * val age: Int  // Non-nullable
     * // Generated: age INT NOT NULL
     * ```
     *
     * #### Primary Key
     * ```kotlin
     * @PrimaryKey(isAutoincrement = true)
     * val id: Long?
     * // Generated: id INTEGER PRIMARY KEY AUTOINCREMENT
     * ```
     *
     * #### Composite Primary Key
     * ```kotlin
     * @CompositePrimaryKey
     * val userId: Long
     * // Column: userId BIGINT
     * // Later appended: ,PRIMARY KEY(userId,productId)
     * ```
     *
     * #### Unique with Collation
     * ```kotlin
     * @Unique
     * @CollateNoCase
     * val username: String
     * // Generated: username TEXT COLLATE NOCASE UNIQUE
     * ```
     *
     * #### Composite Unique (Multi-Group)
     * ```kotlin
     * @CompositeUnique(group = [0, 1])
     * val email: String
     * // Column: email TEXT
     * // Later appended: ,UNIQUE(email,phone),UNIQUE(email,username)
     * ```
     *
     * ### Processing Order
     * 1. Determine SQLite type via [getSQLiteType]
     * 2. Apply PRIMARY KEY constraint if [@PrimaryKey] present
     * 3. Collect [@CompositePrimaryKey] columns for table-level constraint
     * 4. Apply NOT NULL for non-nullable, non-PK columns
     * 5. Apply COLLATE NOCASE if [@CollateNoCase] present
     * 6. Apply UNIQUE if [@Unique] present
     * 7. Collect [@CompositeUnique] groups for table-level constraints
     *
     * ### State Mutations
     * This method mutates internal state that is read by [generateCodeForPrimaryKey]:
     * - Sets [primaryKeyName] for single-column primary keys
     * - Adds to [compositePrimaryKeys] for composite primary keys
     * - Populates [compositeUniqueColumns] for composite unique constraints
     * - Updates [isAutomaticIncrement] and [isRowId] flags
     *
     * @param createSQLBuilder StringBuilder to append column definition and constraints to
     * @param property The property declaration to process
     * @param propertyName The name of the database column (may differ from property name)
     * @param isNotNull Whether the property type is non-nullable in Kotlin
     *
     * @throws IllegalArgumentException if validation fails (see class-level documentation for rules)
     *
     * @see generateCodeForPrimaryKey
     * @see getSQLiteType
     */
    @Suppress("UNCHECKED_CAST")
    fun parseProperty(
        createSQLBuilder: StringBuilder,
        property: KSPropertyDeclaration,
        propertyName: String,
        isNotNull: Boolean,
    ) {
        // Collect the information of the primary key(s).
        val annotationKSType = property.annotations.map { it.annotationType.resolve() }
        val isPrimaryKey = annotationKSType.any { it.isAssignableFrom(primaryKeyAnnotationName) }

        with(createSQLBuilder) {
            val type = getSQLiteType(property, isPrimaryKey)
            append(type)

            // Handle @PrimaryKey annotation
            if (isPrimaryKey) {
                check(!annotationKSType.any { it.isAssignableFrom(compositePrimaryKeyName) }) { PROMPT_CANT_ADD_BOTH_ANNOTATION }
                check(!isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                check(!isContainsPrimaryKey) { PROMPT_PRIMARY_KEY_USE_COUNT }
                isContainsPrimaryKey = true
                primaryKeyName = propertyName

                append(" PRIMARY KEY")

                isAutomaticIncrement = property.annotations.find {
                    it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_PRIMARY_KEY
                }?.arguments?.firstOrNull()?.value as? Boolean ?: false
                val isLong = type == " INTEGER" || type == " BIGINT"
                if (isAutomaticIncrement) {
                    check(isLong) { PROMPT_PRIMARY_KEY_TYPE }
                    append(" AUTOINCREMENT")
                }
                isRowId = isLong
            } else if (annotationKSType.any { it.isAssignableFrom(compositePrimaryKeyName) }) {
                // Handle @CompositePrimaryKey - collect for table-level constraint
                check(isNotNull) { PROMPT_PRIMARY_KEY_MUST_NOT_NULL }
                compositePrimaryKeys.add(propertyName)
            } else if (isNotNull) {
                // Add NOT NULL constraint for non-nullable, non-PK columns
                append(" NOT NULL")
            }

            // Handle @CollateNoCase annotation - must be on text columns
            if (annotationKSType.any { it.isAssignableFrom(noCaseAnnotationName) }) {
                check(type == " TEXT" || type == " CHAR(1)") { PROMPT_NO_CASE_MUST_FOR_TEXT }
                append(" COLLATE NOCASE")
            }

            // Handle @Unique annotation - single column uniqueness
            if (annotationKSType.any { it.isAssignableFrom(uniqueAnnotationName) })
                append(" UNIQUE")

            // Handle @CompositeUnique annotation - collect for table-level constraint
            val compositeUniqueAnnotation = property.annotations
                .find { it.annotationType.resolve().declaration.qualifiedName?.asString() == ANNOTATION_COMPOSITE_UNIQUE }

            compositeUniqueAnnotation?.run {
                // Extract group numbers from annotation (defaults to group 0 if not specified)
                arguments
                    .firstOrNull { it.name?.asString() == "group" }
                    .let {
                        val list = if (it == null) {
                            listOf(0)  // Default to group 0
                        } else {
                            it.value as? List<Int> ?: listOf(0)
                        }
                        // Add this property to each specified group
                        list.forEach { group ->
                            val groupList = compositeUniqueColumns[group] ?: ArrayList<String>().also { gl ->
                                compositeUniqueColumns[group] = gl
                            }
                            groupList.add(propertyName)
                        }
                    }
            }
        }
    }

    /**
     * Generates runtime primary key metadata and appends table-level constraints to CREATE TABLE statement.
     *
     * This method performs two critical tasks:
     * 1. **Writes Kotlin code** to the output file that overrides the `primaryKeyInfo` property
     * 2. **Appends SQL** to the CREATE TABLE statement for composite primary keys and composite unique constraints
     *
     * This method must be called **after** all properties have been processed by [parseProperty],
     * as it consumes the accumulated state from those calls.
     *
     * ### Generated Kotlin Code Patterns
     *
     * #### No Primary Key
     * ```kotlin
     * override val primaryKeyInfo = null
     * ```
     *
     * #### Single-Column Primary Key
     * ```kotlin
     * override val primaryKeyInfo = PrimaryKeyInfo(
     *     primaryKeyName = "id",
     *     isAutomaticIncrement = true,
     *     isRowId = true,
     *     compositePrimaryKeys = null,
     * )
     * ```
     *
     * #### Composite Primary Key
     * ```kotlin
     * override val primaryKeyInfo = PrimaryKeyInfo(
     *     primaryKeyName = null,
     *     isAutomaticIncrement = false,
     *     isRowId = false,
     *     compositePrimaryKeys = listOf(
     *         "userId",
     *         "productId",
     *     )
     * )
     * ```
     *
     * ### Appended SQL Patterns
     *
     * #### Composite Primary Key Constraint
     * ```sql
     * ,PRIMARY KEY(userId,productId)
     * ```
     *
     * #### Composite Unique Constraints (Multiple Groups)
     * ```sql
     * ,UNIQUE(email,phone)
     * ,UNIQUE(username,displayName)
     * ```
     *
     * ### State Dependencies
     * This method reads state accumulated by [parseProperty]:
     * - [primaryKeyName]: Name of single-column primary key (if any)
     * - [isAutomaticIncrement]: Whether AUTOINCREMENT is enabled
     * - [isRowId]: Whether the primary key can serve as SQLite rowid alias
     * - [compositePrimaryKeys]: List of columns in composite primary key
     * - [compositeUniqueColumns]: Map of group number to columns for UNIQUE constraints
     *
     * @param writer Writer for generating Kotlin code (primaryKeyInfo property)
     * @param createSQLBuilder StringBuilder to append table-level SQL constraints to
     *
     * @see parseProperty
     * @see com.ctrip.sqllin.dsl.sql.PrimaryKeyInfo
     */
    fun generateCodeForPrimaryKey(writer: Writer, createSQLBuilder: StringBuilder) {
        // Write the override instance for property `primaryKeyInfo`.
        with(writer) {
            if (primaryKeyName == null && compositePrimaryKeys.isEmpty()) {
                write("    override val primaryKeyInfo = null\n\n")
            } else {
                write("    override val primaryKeyInfo = PrimaryKeyInfo(\n")
                if (primaryKeyName == null) {
                    write("        primaryKeyName = null,\n")
                } else {
                    write("        primaryKeyName = \"$primaryKeyName\",\n")
                }
                write("        isAutomaticIncrement = $isAutomaticIncrement,\n")
                write("        isRowId = $isRowId,\n")
                if (compositePrimaryKeys.isEmpty()) {
                    write("        compositePrimaryKeys = null,\n")
                } else {
                    write("        compositePrimaryKeys = listOf(\n")
                    compositePrimaryKeys.forEach {
                        write("            \"$it\",\n")
                    }
                    write("        )\n")
                }
                write("    )\n\n")
            }
        }
        // Append table-level constraints to CREATE TABLE statement
        with(createSQLBuilder) {
            // Add composite primary key constraint if present
            compositePrimaryKeys.takeIf { it.isNotEmpty() }?.let {
                append(",PRIMARY KEY(")
                append(it[0])
                for (i in 1 ..< it.size) {
                    append(',')
                    append(it[i])
                }
                append(')')
            }

            // Add composite unique constraints for each group
            compositeUniqueColumns.values.forEach {
                if (it.isEmpty())
                    return@forEach
                append(",UNIQUE(")
                append(it[0])
                for (i in 1 ..< it.size) {
                    append(',')
                    append(it[i])
                }
                append(')')
            }
        }
    }

    /**
     * Determines the SQLite type declaration for a given property.
     *
     * This function resolves the Kotlin type of a property to its corresponding SQLite type
     * string, handling type aliases and enum classes. The result is used in compile-time
     * CREATE TABLE statement generation.
     *
     * ### Type Resolution Strategy
     * 1. **Type Aliases**: Resolves to the underlying type, then maps to SQLite type
     * 2. **Enum Classes**: Maps to SQLite INT type (enums are stored as ordinals)
     * 3. **Standard Types**: Direct mapping via [FullNameCache.getSQLTypeName]
     *
     * ### Primary Key Special Handling
     * When `isPrimaryKey` is true and the property is of type [Long], the function returns
     * " INTEGER" instead of " BIGINT" to enable SQLite's rowid aliasing optimization.
     *
     * ### Example Mappings
     * ```kotlin
     * // Standard type
     * val age: Int  // → " INT"
     *
     * // Type alias
     * typealias UserId = Long
     * val id: UserId  // → " BIGINT" (or " INTEGER" if primary key)
     *
     * // Enum class
     * enum class Status { ACTIVE, INACTIVE }
     * val status: Status  // → " INT"
     * ```
     *
     * @param property The KSP property declaration to analyze
     * @param isPrimaryKey Whether this property is annotated with [@PrimaryKey]
     * @return SQLite type declaration string with leading space (e.g., " INT", " TEXT")
     * @throws IllegalStateException if the property type is not supported by SQLlin
     *
     * @see FullNameCache.getSQLTypeName
     */
    private fun getSQLiteType(property: KSPropertyDeclaration, isPrimaryKey: Boolean): String {
        val declaration = property.type.resolve().declaration
        return when (declaration) {
            is KSTypeAlias -> {
                val realDeclaration = declaration.type.resolve().declaration
                FullNameCache.getSQLTypeName(realDeclaration.typeName, isPrimaryKey) ?: kotlin.run {
                    if (realDeclaration is KSClassDeclaration && realDeclaration.classKind == ClassKind.ENUM_CLASS)
                        FullNameCache.getSQLTypeName(FullNameCache.INT, isPrimaryKey)
                    else
                        null
                }
            }
            is KSClassDeclaration if declaration.classKind == ClassKind.ENUM_CLASS ->
                FullNameCache.getSQLTypeName(FullNameCache.INT, isPrimaryKey)
            else -> FullNameCache.getSQLTypeName(declaration.typeName, isPrimaryKey)
        } ?: throw IllegalStateException("Hasn't support the type '${declaration.typeName}' yet")
    }
}