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

package com.ctrip.sqllin.dsl.annotation

/**
 * Mark the primary key(s) for a table
 * @author Yuang Qiao
 */

/**
 * Marks a property as the primary key for a table within a class annotated with [DBRow].
 *
 * This annotation defines how a data model maps to the primary key of a database table.
 * Within a given `@DBRow` class, **only one** property can be marked with this annotation.
 * To define a primary key that consists of multiple columns, use the [CompositePrimaryKey] annotation instead.
 * Additionally, if a property in the class is marked with [PrimaryKey], the class cannot also use the [CompositePrimaryKey] annotation.
 *
 * ### Type and Nullability Rules
 * The behavior of this annotation differs based on the type of property it annotates.
 * The following rules must be followed:
 *
 * - **When annotating a `Long` property**:
 * The property **must** be declared as a nullable type (`Long?`). This triggers a special
 * SQLite mechanism, mapping the property to an `INTEGER PRIMARY KEY` column, which acts as
 * an alias for the database's internal `rowid`. This is typically used for auto-incrementing
 * keys, where the database assigns an ID upon insertion of a new object (when its ID is `null`).
 *
 * - **When annotating all other types (e.g., `String`, `Int`)**:
 * The property **must** be declared as a non-nullable type (e.g., `String`).
 * This creates a standard, user-provided primary key (such as `TEXT PRIMARY KEY`).
 * You must provide a unique, non-null value for this property upon insertion.
 *
 * @property isAutoincrement Indicates whether to append the `AUTOINCREMENT` keyword to the
 * `INTEGER PRIMARY KEY` column in the `CREATE TABLE` statement. This enables a stricter
 * auto-incrementing strategy that ensures row IDs are never reused.
 * **Important Note**: This parameter is only meaningful when annotating a property of type `Long?`.
 * Setting this to `true` on non-Long properties will result in a compile-time error.
 *
 * @see DBRow
 * @see CompositePrimaryKey
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class PrimaryKey(val isAutoincrement: Boolean = false)

/**
 * Marks a property as a part of a composite primary key for the table.
 *
 * This annotation is used to define a primary key that consists of multiple columns.
 * Unlike [PrimaryKey], you can apply this annotation to **multiple properties** within the
 * same [DBRow] class. The combination of all properties marked with [CompositePrimaryKey]
 * will form the table's composite primary key.
 *
 * ### Important Rules
 * - A class can have multiple properties annotated with [CompositePrimaryKey].
 * - If a class uses [CompositePrimaryKey] on any of its properties, it **cannot** also use
 * the [PrimaryKey] annotation on any other property. A table can only have one primary key,
 * which is either a single column or a composite of multiple columns.
 * - All properties annotated with [CompositePrimaryKey] must be of a **non-nullable** type
 * (e.g., `String`, `Int`, `Long`), as primary key columns cannot contain `NULL` values.
 *
 * @see DBRow
 * @see PrimaryKey
 *
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class CompositePrimaryKey

/**
 * A marker annotation for DSL functions that are considered advanced and require explicit opt-in.
 *
 * This library contains certain powerful APIs that are intended for special use cases and can
 * lead to unexpected behavior or data integrity issues if used improperly. This annotation
 * is used to protect such APIs and ensure they are used intentionally.
 *
 * Any function marked with [AdvancedInsertAPI] is part of this advanced feature set. To call
 * such a function, you must explicitly acknowledge its use by annotating your own calling
 * function or class with `@OptIn(AdvancedInsertAPI::class)`. This acts as a contract,
 * confirming that you understand the implications of the API.
 *
 * A primary example is an API that allows for the manual insertion of a record with a
 * specific primary key ID (e.g., `INSERT_WITH_ID`), which bypasses the database's automatic
 * ID generation. This is useful for data migration but is unsafe for regular inserts.
 *
 * @see OptIn
 * @see RequiresOptIn
 */
@RequiresOptIn(
    message = "This is a special-purpose API for inserting a record with a predefined value for its `INTEGER PRIMARY KEY` (the rowid-backed key). " +
            "It is intended for use cases like data migration or testing. " +
            "For all standard operations where the database should generate the ID, you must use the `INSERT` API instead.",
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class AdvancedInsertAPI