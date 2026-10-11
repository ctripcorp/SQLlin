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

package com.ctrip.sqllin.dsl.annotation

/**
 * Marks a class as the rows of a SQLite view.
 *
 * sqllin-processor generates a view object for the annotated class, as it generates a table object for a `@DBRow`
 * class: the view of a class `Adult` is the object `AdultView`, with a property for each column, named after the class's
 * properties. A SELECT reads the view like a table, and `CREATE_VIEW(AdultView) AS (select)` creates it from a SELECT of
 * its row type. A view can't be written to, so its properties take none of the column constraint annotations, such as
 * `@PrimaryKey` or `@Unique`.
 *
 * The class must also be annotated with `@Serializable`.
 *
 * Example:
 * ```kotlin
 * @DBView("adults")
 * @Serializable
 * data class Adult(val name: String, val age: Int)
 * ```
 *
 * @property viewName The name of the SQLite view. If not specified or empty, the name of the annotated class is used.
 *
 * @author Yuang Qiao
 */
@ExperimentalDSLDatabaseAPI
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class DBView(val viewName: String = "")
