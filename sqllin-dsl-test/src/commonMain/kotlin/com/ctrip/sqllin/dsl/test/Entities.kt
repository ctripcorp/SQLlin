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
 * Book entity
 * @author Yuang Qiao
 */

@DBRow("book")
@Serializable
data class Book(
    val name: String,
    val author: String,
    val price: Double,
    val pages: Int,
)

@DBRow("category")
@Serializable
data class Category(
    val name: String,
    val code: Int,
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
    val age: Int,
)

@DBRow("product")
@Serializable
data class Product(
    @PrimaryKey val sku: String?,
    val name: String,
    val price: Double,
)

@DBRow("student_with_autoincrement")
@Serializable
data class StudentWithAutoincrement(
    @PrimaryKey(isAutoincrement = true) val id: Long?,
    val studentName: String,
    val grade: Int,
)

@DBRow("enrollment")
@Serializable
data class Enrollment(
    @CompositePrimaryKey val studentId: Long,
    @CompositePrimaryKey val courseId: Long,
    val semester: String,
)