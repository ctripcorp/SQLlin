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

/**
 * Defines a table-level foreign key constraint that references another table.
 *
 * This annotation is applied at the **class level** and works together with [@ForeignKey]
 * annotations on individual properties to create multi-column foreign key constraints.
 * Use this when you need to reference multiple columns in a parent table.
 *
 * ### When to Use
 * - **Single-column foreign key**: Use [@References] on the property instead
 * - **Multi-column foreign key**: Use @ForeignKeyGroup at class level + [@ForeignKey] on each property
 *
 * ### How It Works
 * 1. Add @ForeignKeyGroup annotation(s) to your class, each with a unique group number
 * 2. Mark properties with [@ForeignKey], specifying which group they belong to
 * 3. Properties in the same group form a composite foreign key constraint
 *
 * ### Example: Single Foreign Key
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(
 *     group = 0,
 *     tableName = "User",
 *     trigger = Trigger.ON_DELETE_CASCADE
 * )
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "id")
 *     val userId: Long,
 *     val orderDate: String
 * )
 * // Generated SQL: CREATE TABLE Order(
 * //   id INTEGER PRIMARY KEY,
 * //   userId BIGINT,
 * //   orderDate TEXT,
 * //   FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE
 * // )
 * ```
 *
 * ### Example: Composite Foreign Key
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(
 *     group = 0,
 *     tableName = "Product",
 *     trigger = Trigger.ON_DELETE_CASCADE,
 *     constraintName = "fk_product"
 * )
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "categoryId")
 *     val productCategory: Int,
 *     @ForeignKey(group = 0, reference = "productCode")
 *     val productCode: String,
 *     val quantity: Int
 * )
 * // Generated SQL: CREATE TABLE OrderItem(
 * //   id INTEGER PRIMARY KEY,
 * //   productCategory INT,
 * //   productCode TEXT,
 * //   quantity INT,
 * //   CONSTRAINT fk_product FOREIGN KEY (productCategory,productCode)
 * //     REFERENCES Product(categoryId,productCode) ON DELETE CASCADE
 * // )
 * ```
 *
 * ### Multiple Foreign Keys
 * This annotation is repeatable, so you can define multiple foreign key constraints
 * by using different group numbers:
 *
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(group = 0, tableName = "User", trigger = Trigger.ON_DELETE_CASCADE)
 * @ForeignKeyGroup(group = 1, tableName = "Product", trigger = Trigger.ON_DELETE_RESTRICT)
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "id") val userId: Long,
 *     @ForeignKey(group = 1, reference = "id") val productId: Long,
 *     val quantity: Int
 * )
 * ```
 *
 * ### Important Notes
 * - **Enable foreign keys**: Use `PRAGMA_FOREIGN_KEYS(true)` before creating tables, as SQLite
 *   disables foreign key enforcement by default
 * - **Order matters**: The order of [@ForeignKey] properties must match the order of referenced
 *   columns in the parent table
 * - **Non-null constraint**: Properties marked with [@ForeignKey] and triggers like SET NULL must
 *   be nullable, otherwise a compilation error will occur
 *
 * @property group A unique integer identifier for this foreign key group (must be unique within the class)
 * @property tableName The name of the parent table being referenced (cannot be blank)
 * @property trigger The action to take when the referenced row is deleted or updated
 * @property constraintName Optional name for the constraint (appears in error messages and schema introspection)
 *
 * @see ForeignKey
 * @see References
 * @see Trigger
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class ForeignKeyGroup(
    val group: Int,
    val tableName: String,
    val trigger: Trigger = Trigger.NULL,
    val constraintName: String = "",
)

/**
 * Defines a column-level foreign key constraint that references one or more columns in another table.
 *
 * This annotation is applied directly to a property and creates an inline foreign key constraint
 * for that column. Use this for simple, single-property foreign keys. For composite foreign keys
 * involving multiple columns, use [@ForeignKeyGroup] and [@ForeignKey] instead.
 *
 * ### When to Use
 * - **Single-column foreign key**: Use @References on the property (recommended for simplicity)
 * - **Multi-column foreign key**: Use [@ForeignKeyGroup] at class level + [@ForeignKey] on each property
 *
 * ### Example: Simple Foreign Key
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @References(
 *         tableName = "User",
 *         foreignKeys = ["id"],
 *         trigger = Trigger.ON_DELETE_CASCADE
 *     )
 *     val userId: Long,
 *     val orderDate: String
 * )
 * // Generated SQL: CREATE TABLE Order(
 * //   id INTEGER PRIMARY KEY,
 * //   userId BIGINT REFERENCES User(id) ON DELETE CASCADE,
 * //   orderDate TEXT
 * // )
 * ```
 *
 * ### Example: Multi-Column Reference
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @References(
 *         tableName = "Product",
 *         foreignKeys = ["categoryId", "productCode"],
 *         trigger = Trigger.ON_DELETE_RESTRICT,
 *         constraintName = "fk_product"
 *     )
 *     val productCompositeKey: String,  // This single column references multiple columns
 *     val quantity: Int
 * )
 * // Generated SQL: CREATE TABLE OrderItem(
 * //   id INTEGER PRIMARY KEY,
 * //   productCompositeKey TEXT CONSTRAINT fk_product
 * //     REFERENCES Product(categoryId,productCode) ON DELETE RESTRICT,
 * //   quantity INT
 * // )
 * ```
 *
 * ### Example: Named Constraint
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Comment(
 *     @PrimaryKey val id: Long?,
 *     @References(
 *         tableName = "User",
 *         foreignKeys = ["id"],
 *         trigger = Trigger.ON_DELETE_SET_NULL,
 *         constraintName = "fk_comment_author"
 *     )
 *     val authorId: Long?,  // Must be nullable when using ON_DELETE_SET_NULL
 *     val content: String
 * )
 * // Generated SQL: CREATE TABLE Comment(
 * //   id INTEGER PRIMARY KEY,
 * //   authorId BIGINT CONSTRAINT fk_comment_author
 * //     REFERENCES User(id) ON DELETE SET NULL,
 * //   content TEXT
 * // )
 * ```
 *
 * ### Repeatable Usage
 * This annotation is repeatable, allowing you to apply multiple @References to the same property.
 * This is useful when a single column needs to reference different tables based on context:
 *
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class AuditLog(
 *     @PrimaryKey val id: Long?,
 *     @References(tableName = "User", foreignKeys = ["id"], constraintName = "fk_created_by")
 *     @References(tableName = "Admin", foreignKeys = ["id"], constraintName = "fk_approved_by")
 *     val performedBy: Long,  // Can reference either User or Admin table
 *     val action: String
 * )
 * // Generated SQL: CREATE TABLE AuditLog(
 * //   id INTEGER PRIMARY KEY,
 * //   performedBy BIGINT
 * //     CONSTRAINT fk_created_by REFERENCES User(id)
 * //     CONSTRAINT fk_approved_by REFERENCES Admin(id),
 * //   action TEXT
 * // )
 * ```
 *
 * ### Important Notes
 * - **Enable foreign keys**: Use `PRAGMA_FOREIGN_KEYS(true)` before creating tables, as SQLite
 *   disables foreign key enforcement by default
 * - **Nullability with SET NULL triggers**: If using `Trigger.ON_DELETE_SET_NULL` or
 *   `Trigger.ON_UPDATE_SET_NULL`, the annotated property must be nullable (e.g., `Long?`)
 * - **Referenced columns must exist**: The columns specified in `foreignKeys` must exist in
 *   the referenced table
 *
 * @property tableName The name of the parent table being referenced (cannot be blank or empty)
 * @property foreignKeys Array of column names in the parent table to reference (cannot be empty)
 * @property trigger The action to take when the referenced row is deleted or updated (defaults to no action)
 * @property constraintName Optional name for the constraint (useful for error messages and debugging)
 *
 * @see ForeignKeyGroup
 * @see ForeignKey
 * @see Trigger
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class References(
    val tableName: String,
    val trigger: Trigger = Trigger.NULL,
    val constraintName: String = "",
    vararg val foreignKeys: String,
)

/**
 * Marks a property as part of a table-level foreign key constraint defined by [@ForeignKeyGroup].
 *
 * This annotation is used in conjunction with [@ForeignKeyGroup] to create foreign key constraints
 * that span one or more columns. Each property annotated with @ForeignKey must specify which
 * foreign key group it belongs to and which column in the parent table it references.
 *
 * ### When to Use
 * - **Single-column foreign key**: Use [@References] on the property instead (simpler)
 * - **Multi-column foreign key**: Use @ForeignKeyGroup at class level + @ForeignKey on each property
 *
 * ### How It Works
 * 1. Define one or more [@ForeignKeyGroup] annotations at the class level
 * 2. Mark each participating property with @ForeignKey, specifying:
 *    - `group`: Which [@ForeignKeyGroup] this property belongs to
 *    - `reference`: The column name in the parent table that this property references
 *
 * ### Example: Single Foreign Key (via Group)
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(
 *     group = 0,
 *     tableName = "User",
 *     trigger = Trigger.ON_DELETE_CASCADE
 * )
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "id")
 *     val userId: Long,
 *     val orderDate: String
 * )
 * // Generated SQL: CREATE TABLE Order(
 * //   id INTEGER PRIMARY KEY,
 * //   userId BIGINT,
 * //   orderDate TEXT,
 * //   FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE
 * // )
 * ```
 *
 * ### Example: Composite Foreign Key
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(
 *     group = 0,
 *     tableName = "Product",
 *     trigger = Trigger.ON_DELETE_CASCADE
 * )
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "categoryId")
 *     val productCategory: Int,
 *     @ForeignKey(group = 0, reference = "productCode")
 *     val productCode: String,
 *     val quantity: Int
 * )
 * // Generated SQL: CREATE TABLE OrderItem(
 * //   id INTEGER PRIMARY KEY,
 * //   productCategory INT,
 * //   productCode TEXT,
 * //   quantity INT,
 * //   FOREIGN KEY (productCategory,productCode)
 * //     REFERENCES Product(categoryId,productCode) ON DELETE CASCADE
 * // )
 * ```
 *
 * ### Example: Multiple Foreign Keys
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(group = 0, tableName = "User", trigger = Trigger.ON_DELETE_CASCADE)
 * @ForeignKeyGroup(group = 1, tableName = "Product", trigger = Trigger.ON_DELETE_RESTRICT)
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "id") val userId: Long,
 *     @ForeignKey(group = 1, reference = "id") val productId: Long,
 *     val quantity: Int
 * )
 * // Generated SQL: CREATE TABLE OrderItem(
 * //   id INTEGER PRIMARY KEY,
 * //   userId BIGINT,
 * //   productId BIGINT,
 * //   quantity INT,
 * //   FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE,
 * //   FOREIGN KEY (productId) REFERENCES Product(id) ON DELETE RESTRICT
 * // )
 * ```
 *
 * ### Repeatable Usage
 * This annotation is repeatable, allowing a single property to participate in multiple
 * foreign key constraints. This is useful for composite keys that reference different tables:
 *
 * ```kotlin
 * @DBRow
 * @Serializable
 * @ForeignKeyGroup(group = 0, tableName = "Department", trigger = Trigger.ON_DELETE_CASCADE)
 * @ForeignKeyGroup(group = 1, tableName = "Location", trigger = Trigger.ON_DELETE_RESTRICT)
 * data class Employee(
 *     @PrimaryKey val id: Long?,
 *     @ForeignKey(group = 0, reference = "deptId")
 *     @ForeignKey(group = 1, reference = "locId")
 *     val organizationId: Int,  // Used in both foreign keys
 *     @ForeignKey(group = 0, reference = "deptName") val deptName: String,
 *     @ForeignKey(group = 1, reference = "locCode") val locCode: String
 * )
 * // Generated SQL: CREATE TABLE Employee(
 * //   id INTEGER PRIMARY KEY,
 * //   organizationId INT,
 * //   deptName TEXT,
 * //   locCode TEXT,
 * //   FOREIGN KEY (organizationId,deptName) REFERENCES Department(deptId,deptName) ON DELETE CASCADE,
 * //   FOREIGN KEY (organizationId,locCode) REFERENCES Location(locId,locCode) ON DELETE RESTRICT
 * // )
 * ```
 *
 * ### Important Notes
 * - **Corresponding group must exist**: The `group` number must match a [@ForeignKeyGroup] defined at the class level
 * - **Reference column must exist**: The `reference` must be a valid column name in the parent table
 * - **Order matters for composite keys**: When multiple properties belong to the same group, their
 *   order in the class determines the order in the FOREIGN KEY clause
 * - **Nullability with SET NULL triggers**: If the [@ForeignKeyGroup] uses `ON_DELETE_SET_NULL` or
 *   `ON_UPDATE_SET_NULL`, all properties in that group must be nullable
 * - **Enable foreign keys**: Use `PRAGMA_FOREIGN_KEYS(true)` before creating tables
 *
 * @property group The foreign key group number (must match a [@ForeignKeyGroup] annotation)
 * @property reference The column name in the parent table that this property references (cannot be blank)
 *
 * @see ForeignKeyGroup
 * @see References
 * @see Trigger
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
@Repeatable
public annotation class ForeignKey(
    val group: Int,
    val reference: String,
)

/**
 * Defines referential actions (triggers) for foreign key constraints in SQLite.
 *
 * These triggers specify what action SQLite should take when a referenced row in the
 * parent table is deleted or updated. By default, SQLite performs no action (NULL).
 *
 * ### Trigger Types
 *
 * #### DELETE Triggers
 * - **ON_DELETE_CASCADE**: When a parent row is deleted, automatically delete all child rows
 * - **ON_DELETE_SET_NULL**: When a parent row is deleted, set the foreign key column(s) to NULL
 * - **ON_DELETE_SET_DEFAULT**: When a parent row is deleted, set the foreign key column(s) to their default value
 * - **ON_DELETE_RESTRICT**: Prevent deletion of a parent row if child rows exist
 *
 * #### UPDATE Triggers
 * - **ON_UPDATE_CASCADE**: When a parent row's primary key is updated, update all child rows' foreign keys
 * - **ON_UPDATE_SET_NULL**: When a parent row's primary key is updated, set child foreign keys to NULL
 * - **ON_UPDATE_SET_DEFAULT**: When a parent row's primary key is updated, set child foreign keys to their default
 * - **ON_UPDATE_RESTRICT**: Prevent updating a parent row's primary key if child rows exist
 *
 * ### Example: CASCADE Delete
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_CASCADE)
 *     val userId: Long,
 *     val amount: Double
 * )
 * // When a User is deleted, all their Orders are automatically deleted
 * ```
 *
 * ### Example: SET NULL on Delete
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Post(
 *     @PrimaryKey val id: Long?,
 *     @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_NULL)
 *     val authorId: Long?,  // Must be nullable!
 *     val content: String
 * )
 * // When a User is deleted, their Posts remain but authorId becomes NULL
 * ```
 *
 * ### Example: RESTRICT Delete
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class OrderItem(
 *     @PrimaryKey val id: Long?,
 *     @References(tableName = "Order", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_RESTRICT)
 *     val orderId: Long,
 *     val productId: Long
 * )
 * // An Order cannot be deleted if it has OrderItems
 * ```
 *
 * ### Important Notes
 * - **SET_NULL requires nullable columns**: When using `ON_DELETE_SET_NULL` or `ON_UPDATE_SET_NULL`,
 *   the annotated property **must** be nullable (e.g., `Long?`, `String?`)
 * - **SET_DEFAULT requires default values**: SQLite requires a DEFAULT clause in the column definition
 * - **RESTRICT vs no trigger**: RESTRICT explicitly prevents the operation; NULL (default) allows it
 * - **Enable foreign keys**: Foreign key enforcement must be enabled via `PRAGMA_FOREIGN_KEYS(true)`
 *
 * ### SQLite Behavior Summary
 *
 * | Trigger | Parent Deleted/Updated | Child Behavior |
 * |---------|------------------------|----------------|
 * | NULL (default) | Allowed | No change |
 * | CASCADE | Allowed | Child rows deleted/updated |
 * | SET_NULL | Allowed | Foreign key set to NULL |
 * | SET_DEFAULT | Allowed | Foreign key set to DEFAULT |
 * | RESTRICT | **Prevented** | Operation fails |
 *
 * @see ForeignKeyGroup
 * @see ForeignKey
 * @see References
 * @see com.ctrip.sqllin.dsl.DatabaseScope.PRAGMA_FOREIGN_KEYS
 */
public enum class Trigger {
    /**
     * No action is taken when the parent row is deleted or updated.
     * This is the default behavior if no trigger is specified.
     */
    NULL,

    /**
     * When a parent row is deleted, all child rows that reference it are automatically deleted.
     * This maintains referential integrity by removing orphaned child records.
     */
    ON_DELETE_CASCADE,

    /**
     * When a parent row is deleted, the foreign key column(s) in child rows are set to NULL.
     * **Requires the foreign key column(s) to be nullable.**
     */
    ON_DELETE_SET_NULL,

    /**
     * When a parent row is deleted, the foreign key column(s) in child rows are set to their default value.
     * **Requires the column to have a DEFAULT constraint defined.**
     */
    ON_DELETE_SET_DEFAULT,

    /**
     * Prevents deletion of a parent row if any child rows reference it.
     * The DELETE operation will fail with a constraint violation error.
     */
    ON_DELETE_RESTRICT,

    /**
     * When a parent row's primary key is updated, all child rows' foreign keys are updated to match.
     * This maintains referential integrity automatically.
     */
    ON_UPDATE_CASCADE,

    /**
     * When a parent row's primary key is updated, the foreign key column(s) in child rows are set to NULL.
     * **Requires the foreign key column(s) to be nullable.**
     */
    ON_UPDATE_SET_NULL,

    /**
     * When a parent row's primary key is updated, the foreign key column(s) in child rows are set to their default value.
     * **Requires the column to have a DEFAULT constraint defined.**
     */
    ON_UPDATE_SET_DEFAULT,

    /**
     * Prevents updating a parent row's primary key if any child rows reference it.
     * The UPDATE operation will fail with a constraint violation error.
     */
    ON_UPDATE_RESTRICT,
}

/**
 * Specifies a default value for a column in SQLite CREATE TABLE statements.
 *
 * This annotation adds a DEFAULT clause to the column definition, which SQLite uses
 * to automatically populate the column when a new row is inserted without explicitly
 * providing a value for this column. Default values are also critical for foreign key
 * constraints that use `ON DELETE SET DEFAULT` or `ON UPDATE SET DEFAULT` triggers.
 *
 * ### When to Use
 * - To provide fallback values for optional columns
 * - To ensure columns have sensible defaults when not specified
 * - When using `Trigger.ON_DELETE_SET_DEFAULT` or `Trigger.ON_UPDATE_SET_DEFAULT` in foreign keys
 * - To simplify INSERT operations by reducing required fields
 *
 * ### Value Format
 * The `value` parameter must be a valid SQLite literal expression:
 * - **Strings**: Must be enclosed in single quotes: `'default text'`
 * - **Numbers**: Plain numeric literals: `0`, `42`, `3.14`
 * - **Booleans**: Use `0` for false or `1` for true
 * - **NULL**: Use the literal `NULL` (though this is rarely needed for nullable columns)
 * - **Expressions**: SQLite functions like `CURRENT_TIMESTAMP`, `datetime('now')`, etc.
 *
 * ### Example: Basic Default Values
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class User(
 *     @PrimaryKey val id: Long?,
 *     val name: String,
 *     @Default("'active'") val status: String,      // String default
 *     @Default("0") val loginCount: Int,             // Numeric default
 *     @Default("1") val isEnabled: Boolean,          // Boolean default (1 = true)
 *     @Default("CURRENT_TIMESTAMP") val createdAt: String  // SQLite function
 * )
 * // Generated SQL:
 * // CREATE TABLE User(
 * //   id INTEGER PRIMARY KEY,
 * //   name TEXT NOT NULL,
 * //   status TEXT NOT NULL DEFAULT 'active',
 * //   loginCount INT NOT NULL DEFAULT 0,
 * //   isEnabled INT NOT NULL DEFAULT 1,
 * //   createdAt TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
 * // )
 * ```
 *
 * ### Example: With Foreign Key SET DEFAULT Trigger
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Order(
 *     @PrimaryKey val id: Long?,
 *     @References(
 *         tableName = "User",
 *         foreignKeys = ["id"],
 *         trigger = Trigger.ON_DELETE_SET_DEFAULT
 *     )
 *     @Default("0")  // REQUIRED when using ON_DELETE_SET_DEFAULT
 *     val userId: Long,
 *     val amount: Double
 * )
 * // Generated SQL:
 * // CREATE TABLE Order(
 * //   id INTEGER PRIMARY KEY,
 * //   userId BIGINT NOT NULL DEFAULT 0 REFERENCES User(id) ON DELETE SET DEFAULT,
 * //   amount REAL NOT NULL
 * // )
 * // When a User is deleted, their Orders' userId becomes 0
 * ```
 *
 * ### Example: Nullable Column with Default
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Product(
 *     @PrimaryKey val id: Long?,
 *     val name: String,
 *     @Default("'In Stock'") val availability: String?,
 *     @Default("100") val quantity: Int?
 * )
 * // Generated SQL:
 * // CREATE TABLE Product(
 * //   id INTEGER PRIMARY KEY,
 * //   name TEXT NOT NULL,
 * //   availability TEXT DEFAULT 'In Stock',
 * //   quantity INT DEFAULT 100
 * // )
 * ```
 *
 * ### Example: Using SQLite Functions
 * ```kotlin
 * @DBRow
 * @Serializable
 * data class Event(
 *     @PrimaryKey val id: Long?,
 *     val name: String,
 *     @Default("datetime('now')") val timestamp: String,
 *     @Default("(random())") val randomId: Long
 * )
 * // Generated SQL:
 * // CREATE TABLE Event(
 * //   id INTEGER PRIMARY KEY,
 * //   name TEXT NOT NULL,
 * //   timestamp TEXT NOT NULL DEFAULT datetime('now'),
 * //   randomId BIGINT NOT NULL DEFAULT (random())
 * // )
 * ```
 *
 * ### Important Notes
 * - **String values must use single quotes**: `'text'`, not `"text"`
 * - **No type validation**: The annotation processor doesn't verify that the default value
 *   matches the column type - SQLite will handle type coercion or raise runtime errors
 * - **Expressions are passed as-is**: Complex expressions like `(random())` or
 *   `datetime('now', 'localtime')` are valid
 * - **Required for SET_DEFAULT triggers**: When using `ON_DELETE_SET_DEFAULT` or
 *   `ON_UPDATE_SET_DEFAULT` triggers on foreign keys, the column **must** have a default
 *   value or be nullable
 *
 * ### Common Pitfalls
 *
 * #### Wrong: Using double quotes for strings
 * ```kotlin
 * @Default("\"active\"")  // ❌ Wrong - SQLite uses single quotes
 * val status: String
 * ```
 *
 * #### Correct: Using single quotes for strings
 * ```kotlin
 * @Default("'active'")  // ✅ Correct
 * val status: String
 * ```
 *
 * #### Wrong: Forgetting default with SET_DEFAULT trigger
 * ```kotlin
 * @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_DEFAULT)
 * val userId: Long  // ❌ Compile error - needs @Default or must be nullable
 * ```
 *
 * #### Correct: Adding default value
 * ```kotlin
 * @References(tableName = "User", foreignKeys = ["id"], trigger = Trigger.ON_DELETE_SET_DEFAULT)
 * @Default("0")
 * val userId: Long  // ✅ Correct
 * ```
 *
 * ### SQLite Behavior
 * - Default values are evaluated **once** when the CREATE TABLE statement is executed
 * - Functions like `CURRENT_TIMESTAMP` are evaluated **at insertion time**, not at table creation
 * - Default values don't override explicitly provided values in INSERT statements
 * - If a column has both DEFAULT and NOT NULL, you can omit it in INSERT (it won't be NULL)
 *
 * @property value The SQLite default value expression (e.g., `'text'`, `0`, `CURRENT_TIMESTAMP`)
 *
 * @see References
 * @see ForeignKeyGroup
 * @see Trigger.ON_DELETE_SET_DEFAULT
 * @see Trigger.ON_UPDATE_SET_DEFAULT
 * @see DBRow
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.BINARY)
public annotation class Default(val value: String)