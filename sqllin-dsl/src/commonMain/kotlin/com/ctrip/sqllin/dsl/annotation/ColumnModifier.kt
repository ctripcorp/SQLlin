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
 * Modifiers for columns in a table
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
 * Marks a text column to use case-insensitive collation in SQLite.
 *
 * This annotation adds the `COLLATE NOCASE` clause to the column definition in the
 * `CREATE TABLE` statement, making string comparisons case-insensitive for this column.
 * This is particularly useful for columns that store user input where case should not
 * affect equality or sorting (e.g., email addresses, usernames).
 *
 * ### Type Restrictions
 * - Can **only** be applied to properties of type `String` or `Char` (and their nullable variants)
 * - Attempting to use this annotation on non-text types will result in a compile-time error
 *
 * ### Example
 * ```kotlin
 * @Serializable
 * @DBRow
 * data class User(
 *     @PrimaryKey val id: Long?,
 *     @CollateNoCase val email: String,  // Case-insensitive email matching
 *     val name: String
 * )
 * // Generated SQL: CREATE TABLE User(id INTEGER PRIMARY KEY, email TEXT COLLATE NOCASE, name TEXT)
 * ```
 *
 * ### SQLite Behavior
 * With `COLLATE NOCASE`:
 * - `'ABC' = 'abc'` evaluates to true
 * - `ORDER BY` clauses sort case-insensitively
 * - Indexes on the column are case-insensitive
 *
 * @see DBRow
 * @see Unique
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class CollateNoCase

/**
 * Marks a column as unique, enforcing a UNIQUE constraint in the database.
 *
 * This annotation adds the `UNIQUE` keyword to the column definition in the
 * `CREATE TABLE` statement, ensuring that no two rows can have the same value
 * in this column (except for NULL values, which can appear multiple times).
 *
 * ### Single vs. Composite Unique Constraints
 * - Use [Unique] when a **single column** must have unique values
 * - Use [CompositeUnique] when **multiple columns together** must form a unique combination
 *
 * ### Example
 * ```kotlin
 * @Serializable
 * @DBRow
 * data class User(
 *     @PrimaryKey val id: Long?,
 *     @Unique val email: String,     // Each email must be unique
 *     @Unique val username: String,  // Each username must be unique
 *     val age: Int
 * )
 * // Generated SQL: CREATE TABLE User(id INTEGER PRIMARY KEY, email TEXT UNIQUE, username TEXT UNIQUE, age INT)
 * ```
 *
 * ### Nullability Considerations
 * - Multiple NULL values are allowed in a UNIQUE column (NULL is not equal to NULL in SQL)
 * - To prevent NULL values, combine with a non-nullable type: `val email: String`
 *
 * @see DBRow
 * @see CompositeUnique
 * @see CollateNoCase
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class Unique

/**
 * Marks a property as part of one or more composite UNIQUE constraints.
 *
 * This annotation allows you to define UNIQUE constraints that span multiple columns.
 * Unlike [Unique], which enforces uniqueness on a single column, [CompositeUnique]
 * ensures that the **combination** of values across multiple columns is unique.
 *
 * ### Grouping
 * Properties can belong to multiple unique constraint groups by specifying different
 * group numbers. Properties with the same group number(s) will be combined into a
 * single composite UNIQUE constraint.
 *
 * ### Example: Single Composite Constraint
 * ```kotlin
 * @Serializable
 * @DBRow
 * data class Enrollment(
 *     @PrimaryKey val id: Long?,
 *     @CompositeUnique(0) val studentId: Int,
 *     @CompositeUnique(0) val courseId: Int,
 *     val enrollmentDate: String
 * )
 * // Generated SQL: CREATE TABLE Enrollment(
 * //   id INTEGER PRIMARY KEY,
 * //   studentId INT,
 * //   courseId INT,
 * //   enrollmentDate TEXT,
 * //   UNIQUE(studentId,courseId)
 * // )
 * // A student cannot enroll in the same course twice
 * ```
 *
 * ### Example: Multiple Composite Constraints
 * ```kotlin
 * @Serializable
 * @DBRow
 * data class Event(
 *     @PrimaryKey val id: Long?,
 *     @CompositeUnique(0, 1) val userId: Int,     // Part of groups 0 and 1
 *     @CompositeUnique(0) val eventType: String,  // Part of group 0
 *     @CompositeUnique(1) val timestamp: Long     // Part of group 1
 * )
 * // Generated SQL: CREATE TABLE Event(
 * //   id INTEGER PRIMARY KEY,
 * //   userId INT,
 * //   eventType TEXT,
 * //   timestamp BIGINT,
 * //   UNIQUE(userId,eventType),
 * //   UNIQUE(userId,timestamp)
 * // )
 * ```
 *
 * ### Default Behavior
 * - If no group is specified: `@CompositeUnique()`, defaults to group `0`
 * - All properties with group `0` (explicit or default) form a single composite constraint
 *
 * @property group One or more group numbers (0-based integers) identifying which
 * composite UNIQUE constraint(s) this property belongs to. Properties sharing
 * the same group number are combined into a single `UNIQUE(col1, col2, ...)` clause.
 *
 * @see DBRow
 * @see Unique
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class CompositeUnique(vararg val group: Int = [0])