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

import com.ctrip.sqllin.dsl.annotation.Check
import com.ctrip.sqllin.dsl.annotation.CollateNoCase
import com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
import com.ctrip.sqllin.dsl.annotation.CompositeUnique
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.DBView
import com.ctrip.sqllin.dsl.annotation.ExperimentalDSLDatabaseAPI
import com.ctrip.sqllin.dsl.annotation.Fts3
import com.ctrip.sqllin.dsl.annotation.Fts4
import com.ctrip.sqllin.dsl.annotation.FtsTokenizer
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import com.ctrip.sqllin.dsl.annotation.Unique
import kotlinx.serialization.SerialName
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
) {
    // Computed, so kotlinx.serialization doesn't serialize it: it must not become a column, or every INSERT,
    // which writes only the serialized properties, would leave that column empty
    val title: String get() = "$name by $author"
}

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

/**
 * Projections of [Book]: plain @Serializable types rather than tables, whose properties name the columns a SELECT
 * reads, as in `BookTable SELECT X<BookTitle>()`.
 */
@Serializable
data class BookTitle(val name: String, val author: String)

@Serializable
data class BookAuthor(val author: String)

/**
 * The `name` of a [Book] or a [PersonWithId]: a projection both tables fit, so that a compound SELECT can combine them.
 */
@Serializable
data class NameOnly(val name: String)

/**
 * A projection of [UserAccount] that reads its nullable `notes` column into a nullable property.
 */
@Serializable
data class UserNotes(val username: String, val notes: String?)

/**
 * Projections that don't fit their table, each breaking one of the rules a projection is checked against.
 */
@Serializable
data class BookWithIsbn(val name: String, val isbn: String) // 'isbn' isn't a column of book

@Serializable
data class BookPagesAsText(val pages: String) // 'pages' holds an Int

@Serializable
data class UserNotesNonNull(val notes: String) // 'notes' is nullable

/**
 * Result types of SELECTs with result columns, as in `BookTable SELECT listOf(count(X) AS AuthorStats::books)`: the
 * properties given an expression with AS hold it, and every other property is read from its column.
 */
@Serializable
data class AuthorStats(
    val author: String, // read from its column
    val books: Long,
    val totalPages: Long,
    val maxPrice: Price,
    val firstTitle: String,
)

/**
 * Aggregates of a whole table, not grouped: all but `count` are NULL when no rows match, so they are nullable.
 */
@Serializable
data class BookTotals(val books: Long, val maxPages: PageCount?, val averagePrice: Double?, val totalPrice: Double?)

@Serializable
data class BookCount(val books: Long)

/**
 * Aggregates of the distinct values of the columns of the books, next to those of all values.
 */
@Serializable
data class DistinctBookTotals(
    val authors: Long,
    val books: Long,
    val distinctPrice: Double?,
    val totalPrice: Double?,
    val distinctPages: Long?,
    val averageDistinctPages: Double?,
    val authorNames: String?,
)

/**
 * Scalar functions of the columns of a book, next to its `name`, which is read from its column.
 */
@Serializable
data class BookFunctions(
    val name: String,
    val upperName: String,
    val nameLength: Long,
    val roundedPrice: Double,
    val absPages: PageCount,
)

@Serializable
data class StatusStats(val status: UserStatus, val users: Long, val notes: String?, val highestPriority: Priority)

@Serializable
data class EnabledCount(val enabled: Long?)

@Serializable
data class BookNames(val names: String?)

@Serializable
data class BookLabel(val label: String)

/**
 * Result types that don't fit their query, each breaking one of the rules result columns are checked against.
 */
@Serializable
data class BookCountAndMaxPages(val books: Long, val maxPages: PageCount) // 'maxPages' is NULL when no rows match

@Serializable
data class RenamedBookCount(@SerialName("total") val books: Long) // 'books' isn't serialized under its own name

/**
 * A table of aggregates, filled with `INSERT INTO ... SELECT` from a grouped query of [Book].
 */
@DBRow("author_book_count")
@Serializable
data class AuthorBookCount(val author: String, val books: Long)

/**
 * The `rebuild_person` table before and after a rebuild, which renames `name` to `fullName`, makes it unique, a change
 * `ALTER TABLE` can't make, and drops `legacy`.
 */
@DBRow("rebuild_person")
@Serializable
data class RebuildPersonV1(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    val name: String,
    val legacy: Int,
)

@DBRow("rebuild_person")
@Serializable
data class RebuildPerson(
    @PrimaryKey(autoIncrement = true) val id: Long?,
    @Unique val fullName: String,
)

/**
 * References `rebuild_person`, so its foreign key shows whether the rebuild left the reference in place.
 */
@DBRow("rebuild_pet")
@Serializable
data class RebuildPet(
    @PrimaryKey val id: Long,
    @com.ctrip.sqllin.dsl.annotation.References(tableName = "rebuild_person", foreignKeys = ["id"])
    val ownerId: Long,
)

/**
 * A view of the [PersonWithId]s who are adults, which `CREATE_VIEW` creates from a SELECT of this type.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("adult_person")
@Serializable
data class AdultPerson(val name: String, val age: Age)

/**
 * Aggregates of the unsigned `testUInt` column of [TestPrimitiveTypeForKSP], which are only right if it holds the
 * numbers, not their bits as signed numbers.
 */
@Serializable
data class UIntTotals(val total: Long?, val highest: UInt?)

/**
 * An FTS4 table: the porter tokenizer, so that "running" matches "run", prefixes of two letters indexed, and a column
 * it stores but doesn't index. Its rowid is assigned by SQLite.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBRow("articles")
@Fts4(tokenizer = FtsTokenizer.PORTER, prefix = [2], notIndexed = ["note"])
@Serializable
data class Article(@PrimaryKey val rowid: Long?, val title: String, val body: String, val note: String?)

/**
 * A match of [Article] with a text that describes it: a snippet, or offsets.
 */
@Serializable
data class ArticleMatch(val title: String, val excerpt: String)

/**
 * A match of [Article] with its matchinfo.
 */
@Serializable
class ArticleMatchInfo(val title: String, val info: ByteArray)

/**
 * An FTS3 table whose tokenizer takes an argument, with its rowid named docid and supplied by the caller.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBRow("notes")
@Fts3(tokenizer = FtsTokenizer.UNICODE61, tokenizerArgs = ["remove_diacritics=1"])
@Serializable
data class Note(@PrimaryKey val docid: Long, val text: String)

/**
 * A table with CHECK constraints: of columns, two of them on one column, written named first to check that the unnamed
 * one comes first, and a named one of the table that compares two columns.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBRow
@Check("endDate IS NULL OR endDate >= startDate", constraintName = "valid_period")
@Serializable
data class CheckedMembership(
    @PrimaryKey val id: Long?,
    @Check("lower(name) = name", constraintName = "lower_case_name") @Check("length(trim(name)) > 0") val name: String,
    @Check("age BETWEEN 0 AND 150") val age: Int?,
    @Check("level IN (0, 1, 2)") val level: Int,
    val startDate: String,
    @Check("endDate IS NULL OR date(endDate) IS endDate") val endDate: String?,
)

/**
 * The number of books of each author, declared as a view to name a derived table of book: no such view is created.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("author_books")
@Serializable
data class AuthorBooks(val author: String, val books: Long)

/**
 * A person with the number of their books, from a join with the derived table [AuthorBooks].
 */
@Serializable
data class PersonBooks(val name: String, val age: Age, val books: Long)

/**
 * The persons under other names, as a derived table that joins person_with_id with itself.
 */
@OptIn(ExperimentalDSLDatabaseAPI::class)
@DBView("elder")
@Serializable
data class Elder(val elderName: String, val elderAge: Age)

/**
 * A person with someone older, from person_with_id joined with itself.
 */
@Serializable
data class YoungerAndElder(val name: String, val elderName: String)

/**
 * The age of the oldest person: a SELECT of a single column, which an IN subquery compares with.
 */
@Serializable
data class PersonAge(val age: Age?)

/**
 * A person with the number of their books and the highest price of one, from person_with_id outer joined with book.
 */
@Serializable
data class PersonBookStats(val name: String, val books: Long, val highest: Double?)

/**
 * The age of a person with the name of one of their books, which an outer join leaves NULL for a person without one.
 */
@Serializable
data class PersonBookTitle(val age: Age, val title: String?)

/**
 * The age of an author with the code of the category of one of their books, from three joined tables.
 */
@Serializable
data class AuthorCode(val age: Age, val code: Code)
