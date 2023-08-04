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
package com.ctrip.sqllin.dsl

import com.ctrip.sqllin.dsl.annotation.DBRow
import kotlinx.serialization.Serializable

/**
 * Book entity
 * @author yaqiao
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
    val name: String,
    val author: String,
    val price: Double,
    val pages: Int,
    val code: Int,
)

@Serializable
data class CrossJoiner(
    val author: String,
    val price: Double,
    val pages: Int,
    val code: Int,
)