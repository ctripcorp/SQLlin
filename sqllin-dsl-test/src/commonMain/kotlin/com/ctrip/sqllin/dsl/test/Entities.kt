/*
 * Copyright (C) 2022 Ctrip.com.
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

package com.ctrip.sqllin.dsl.test

import com.ctrip.sqllin.dsl.annotation.CollateNoCase
import com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
import com.ctrip.sqllin.dsl.annotation.CompositeUnique
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.Unique
import kotlinx.serialization.Serializable

/**
 * Type aliases for testing typealias support in sqllin-processor
 */
typealias Price = Double
typealias PageCount = Int
typealias Age = Int
typealias Grade = Int
typealias StudentId = Long
typealias CourseId = Long
typealias Code = Int

/**
 * Enum types for testing enum support
 */

/**
 * User status enum for testing enum functionality
 */
enum class UserStatus {
    ACTIVE,
    INACTIVE,
    SUSPENDED,
    BANNED
}

/**
 * Priority level enum for testing enum comparisons
 */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Book entity
 * @author Yuang Qiao
 */

@DBRow("book")
@Serializable
data class Book(
    val name: String,
    val author: String,
    val price: Price,
    val pages: PageCount,
)

@DBRow("category")
@Serializable
data class Category(
    val name: String,
    val code: Code,
)

@Serializable
data class Joiner(
    val name: String?,
    val author: String?,
    val price: Double?,
    val pages: Int?,
    val code: Int?,
)

@Serializable
data class CrossJoiner(
    val author: String?,
    val price: Double?,
    val pages: Int?,
    val code: Int?,
)

@DBRow("NullTester")
@Serializable
data class NullTester(
    val paramInt: Int?,
    val paramString: String?,
    val paramDouble: Double?,
)

@DBRow("person_with_id")
@Serializable
data class PersonWithId(
    @PrimaryKey val id: Long?,
    val name: String,
    val age: Age,
)

@DBRow("product")
@Serializable
data class Product(
    @PrimaryKey val sku: String,
    val name: String,
    val price: Price,
)

@DBRow("student_with_autoincrement")
@Serializable
data class StudentWithAutoincrement(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val studentName: String,
    val grade: Grade,
)

@DBRow("enrollment")
@Serializable
data class Enrollment(
    @CompositePrimaryKey val studentId: StudentId,
    @CompositePrimaryKey val courseId: CourseId,
    val semester: String,
)

@DBRow("file_data")
@Serializable
data class FileData(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val fileName: String,
    val content: ByteArray,
    val metadata: String,
) {
    // ByteArray doesn't implement equals/hashCode properly for data class
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FileData

        if (id != other.id) return false
        if (fileName != other.fileName) return false
        if (!content.contentEquals(other.content)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + fileName.hashCode()
        result = 31 * result + content.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * User entity with enum fields for testing enum support
 */
@DBRow("user_account")
@Serializable
data class UserAccount(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val username: String,
    val email: String,
    val status: UserStatus,
    val priority: Priority,
    val notes: String?,
)

/**
 * Task entity with nullable enum for testing nullable enum support
 */
@DBRow("task")
@Serializable
data class Task(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val title: String,
    val priority: Priority?,
    val description: String,
)

/**
 * Test entity for @Unique annotation
 * Tests single-column uniqueness constraints
 */
@DBRow("unique_email_test")
@Serializable
data class UniqueEmailTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val email: String,
    val name: String,
)

/**
 * Test entity for @CollateNoCase annotation
 * Tests case-insensitive text collation
 */
@DBRow("collate_nocase_test")
@Serializable
data class CollateNoCaseTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CollateNoCase val username: String,
    @CollateNoCase @Unique val email: String,
    val description: String,
)

/**
 * Test entity for @CompositeUnique annotation
 * Tests multi-column uniqueness constraints with groups
 */
@DBRow("composite_unique_test")
@Serializable
data class CompositeUniqueTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CompositeUnique(0) val groupA: String,
    @CompositeUnique(0) val groupB: Int,
    @CompositeUnique(1) val groupC: String,
    @CompositeUnique(1) val groupD: String,
    val notes: String?,
)

/**
 * Test entity for multiple @CompositeUnique groups on same property
 * Tests that a property can belong to multiple composite unique constraints
 */
@DBRow("multi_group_unique_test")
@Serializable
data class MultiGroupUniqueTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @CompositeUnique(0, 1) val userId: Int,
    @CompositeUnique(0) val eventType: String,
    @CompositeUnique(1) val timestamp: Long,
    val metadata: String?,
)

/**
 * Test entity combining multiple column modifiers
 * Tests interaction between @Unique, @CollateNoCase, and NOT NULL (non-nullable type)
 */
@DBRow("combined_constraints_test")
@Serializable
data class CombinedConstraintsTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique @CollateNoCase val code: String,
    @Unique val serial: String,
    val value: Int,
)

/**
 * Foreign Key Test Entities
 */

/**
 * Parent table for testing @References annotation
 */
@DBRow("fk_user")
@Serializable
data class FKUser(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val email: String,
    val name: String,
)

/**
 * Child table with CASCADE delete using @References
 */
@DBRow("fk_order")
@Serializable
data class FKOrder(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.References(
        tableName = "fk_user",
        foreignKeys = ["id"],
        trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_CASCADE
    )
    val userId: Long,
    val amount: Double,
    val orderDate: String,
)

/**
 * Child table with SET_NULL delete using @References
 */
@DBRow("fk_post")
@Serializable
data class FKPost(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.References(
        tableName = "fk_user",
        foreignKeys = ["id"],
        trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_SET_NULL
    )
    val authorId: Long?,
    val title: String,
    val content: String,
)

/**
 * Child table with RESTRICT delete using @References
 */
@DBRow("fk_profile")
@Serializable
data class FKProfile(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.References(
        tableName = "fk_user",
        foreignKeys = ["id"],
        trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_RESTRICT
    )
    val userId: Long,
    val bio: String,
    val website: String?,
)

/**
 * Parent table with composite primary key for testing composite foreign keys
 */
@DBRow("fk_product")
@Serializable
data class FKProduct(
    @CompositePrimaryKey val categoryId: Int,
    @CompositePrimaryKey val productCode: String,
    val name: String,
    val price: Double,
)

/**
 * Child table with composite foreign key using @ForeignKeyGroup and @ForeignKey annotations
 */
@DBRow("fk_order_item")
@Serializable
@com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup(
    group = 0,
    tableName = "fk_product",
    trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_CASCADE
)
data class FKOrderItem(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.ForeignKey(group = 0, reference = "categoryId")
    val productCategory: Int,
    @com.ctrip.sqllin.dsl.annotation.ForeignKey(group = 0, reference = "productCode")
    val productCode: String,
    val quantity: Int,
    val subtotal: Double,
)

/**
 * Table with multiple foreign keys to different tables
 */
@DBRow("fk_comment")
@Serializable
data class FKComment(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.References(
        tableName = "fk_user",
        foreignKeys = ["id"],
        trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_CASCADE
    )
    val authorId: Long,
    @com.ctrip.sqllin.dsl.annotation.References(
        tableName = "fk_post",
        foreignKeys = ["id"],
        trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_CASCADE
    )
    val postId: Long,
    val content: String,
    val createdAt: String,
)

/**
 * Default Values Test Entities
 */

/**
 * Test entity for @Default annotation with basic types
 * Tests default values for String, Int, Boolean, and SQLite functions
 */
@DBRow("default_values_test")
@Serializable
data class DefaultValuesTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    @com.ctrip.sqllin.dsl.annotation.Default("'active'") val status: String,
    @com.ctrip.sqllin.dsl.annotation.Default("0") val loginCount: Int,
    @com.ctrip.sqllin.dsl.annotation.Default("1") val isEnabled: Boolean,
    @com.ctrip.sqllin.dsl.annotation.Default("CURRENT_TIMESTAMP") val createdAt: String,
)

/**
 * Test entity for @Default annotation with nullable types
 * Tests default values on nullable columns
 */
@DBRow("default_nullable_test")
@Serializable
data class DefaultNullableTest(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    @com.ctrip.sqllin.dsl.annotation.Default("'In Stock'") val availability: String?,
    @com.ctrip.sqllin.dsl.annotation.Default("100") val quantity: Int?,
    @com.ctrip.sqllin.dsl.annotation.Default("0.0") val discount: Double?,
)

/**
 * Parent table for testing @Default with foreign key SET_DEFAULT trigger
 */
@DBRow("default_fk_parent")
@Serializable
data class DefaultFKParent(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
)

/**
 * Child table with @Default and foreign key SET_DEFAULT trigger
 * Tests that default values work with ON_DELETE_SET_DEFAULT
 */
@DBRow("default_fk_child")
@Serializable
@com.ctrip.sqllin.dsl.annotation.ForeignKeyGroup(
    group = 0,
    tableName = "default_fk_parent",
    trigger = com.ctrip.sqllin.dsl.annotation.Trigger.ON_DELETE_SET_DEFAULT
)
data class DefaultFKChild(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @com.ctrip.sqllin.dsl.annotation.ForeignKey(group = 0, reference = "id")
    @com.ctrip.sqllin.dsl.annotation.Default("0")
    val parentId: Long,
    val description: String,
)
/**
 * An `internal` entity, used to verify that the processor propagates the entity's visibility
 * to the generated table object. If it doesn't, the generated `public` object triggers
 * EXPOSED_SUPER_CLASS, EXPOSED_FUNCTION_RETURN_TYPE and EXPOSED_RECEIVER_TYPE errors, and
 * this module fails to compile.
 */
@DBRow("internal_visibility")
@Serializable
internal data class InternalVisibility(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
)

/**
 * The 'alter_target' table in its shape *before* the migration exercised by
 * `testSchemaModification`: it has `name` and `legacy`, and no `nickname`.
 */
@DBRow("alter_target")
@Serializable
data class AlterBefore(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    val legacy: Int,
)

/**
 * The same 'alter_target' table in its shape *after* the migration: `nickname` has been added,
 * `name` has been renamed to `fullName`, and `legacy` has been dropped. Mapping two entities onto
 * one table name is what lets the test read the table back through whichever shape it should
 * currently have, so a migration step that silently does nothing fails the test.
 */
@DBRow("alter_target")
@Serializable
data class AlterAfter(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val fullName: String,
    val nickname: String?,
)

/**
 * The migrated shape plus the `legacy` column, used purely as a probe: selecting it succeeds while
 * `legacy` is still present and fails once DROP COLUMN has removed it.
 */
@DBRow("alter_target")
@Serializable
data class AlterWithLegacy(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val fullName: String,
    val nickname: String?,
    val legacy: Int,
)

/**
 * Supplies the destination table name for the `ALTER_RENAME_TABLE_TO` step; same shape as
 * [AlterAfter].
 */
@DBRow("alter_renamed")
@Serializable
data class AlterRenamed(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val fullName: String,
    val nickname: String?,
)

/**
 * A non-null `Long` primary key: supplied by the caller, as an ID assigned by a remote service would
 * be, yet still an `INTEGER PRIMARY KEY` and so still an alias for SQLite's rowid.
 */
@DBRow("remote_movie")
@Serializable
data class RemoteMovie(
    @PrimaryKey val id: Long,
    val title: String,
)
