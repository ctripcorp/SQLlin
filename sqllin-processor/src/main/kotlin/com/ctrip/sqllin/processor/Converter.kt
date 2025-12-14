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

import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration

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

/**
 * Extension property that resolves a property's fully qualified type name.
 *
 * This property resolves the property's type through KSP's type system and extracts
 * its fully qualified name. Used throughout the processor for type mapping and code generation.
 *
 * ### Examples
 * ```kotlin
 * // For a property: val age: Int
 * property.typeName  // Returns: "kotlin.Int"
 *
 * // For a property: val user: com.example.User
 * property.typeName  // Returns: "com.example.User"
 *
 * // For a nullable property: val name: String?
 * property.typeName  // Returns: "kotlin.String" (nullability is separate)
 * ```
 *
 * ### Type Resolution
 * This property performs:
 * 1. Resolves the property's type (`type.resolve()`)
 * 2. Gets the declaration of that type
 * 3. Extracts the fully qualified name
 *
 * ### Usage in Processor
 * - Type mapping to SQLite types in [FullNameCache.getSQLTypeName]
 * - Clause element type generation in [ClauseProcessor.getClauseElementTypeStr]
 * - Default value generation in [ClauseProcessor.getDefaultValueByType]
 * - Enum type detection in [ColumnConstraintParser.getSQLiteType]
 *
 * @return The fully qualified type name (e.g., "kotlin.Int", "kotlin.String"), or null if unavailable
 *
 * @see KSDeclaration.typeName
 * @see ColumnConstraintParser.getSQLiteType
 */
inline val KSPropertyDeclaration.typeName
    get() = type.resolve().declaration.qualifiedName?.asString()

/**
 * Extension property that retrieves a declaration's fully qualified type name.
 *
 * This is a convenience property for accessing a declaration's qualified name,
 * providing a more expressive API than calling `qualifiedName?.asString()` directly.
 *
 * ### Examples
 * ```kotlin
 * // For a class declaration of: class User
 * classDeclaration.typeName  // Returns: "com.example.User"
 *
 * // For a type alias: typealias UserId = Long
 * typeAliasDeclaration.typeName  // Returns: "com.example.UserId"
 *
 * // For an enum entry: enum class Status { ACTIVE, INACTIVE }
 * enumEntryDeclaration.typeName  // Returns: "com.example.Status.ACTIVE"
 * ```
 *
 * ### Usage in Processor
 * - Resolving underlying types in type alias handling
 * - Getting enum class names for ClauseEnum type generation
 * - Default value generation for enum types
 *
 * @return The fully qualified name of the declaration, or null if unavailable
 *
 * @see KSPropertyDeclaration.typeName
 */
inline val KSDeclaration.typeName
    get() = qualifiedName?.asString()