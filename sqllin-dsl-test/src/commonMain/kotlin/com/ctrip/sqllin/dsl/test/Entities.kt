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

import com.ctrip.sqllin.dsl.annotation.CompositePrimaryKey
import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
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
    @PrimaryKey val sku: String?,
    val name: String,
    val price: Price,
)

@DBRow("student_with_autoincrement")
@Serializable
data class StudentWithAutoincrement(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
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
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    val title: String,
    val priority: Priority?,
    val description: String,
)