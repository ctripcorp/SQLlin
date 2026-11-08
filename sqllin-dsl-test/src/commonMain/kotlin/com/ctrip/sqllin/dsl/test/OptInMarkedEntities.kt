/*
 * Copyright (C) 2026 Ctrip.com.
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

import com.ctrip.sqllin.dsl.annotation.DBRow
import com.ctrip.sqllin.dsl.annotation.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/*
 * Entities that propagate an opt-in requirement, rather than opting in with @OptIn, which CommonConvertedTest tests:
 * their file opts in to nothing, so that the tables generated for them only compile if they opt in to the markers
 * the entities are annotated with.
 */

/**
 * A row of a converted column of an experimental type, `Uuid`, whose class requires the opt-in it needs.
 */
@ExperimentalUuidApi
@Serializable
@DBRow("marked_event")
data class MarkedEvent(@PrimaryKey val id: Uuid, val score: Score)

/**
 * A row of no converted column, whose class requires an opt-in, which the table generated for it, a `Table` of it,
 * needs too.
 */
@ExperimentalUuidApi
@Serializable
@DBRow("marked_basic")
data class MarkedBasic(@PrimaryKey val id: Long?, val name: String)
