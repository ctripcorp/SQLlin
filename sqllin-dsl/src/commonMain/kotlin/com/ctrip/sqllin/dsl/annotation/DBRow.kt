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

package com.ctrip.sqllin.dsl.annotation

/**
 * Marks a data class as a SQLite table representation.
 *
 * This annotation is processed by sqllin-processor at compile time to generate
 * a class that represents a SQLite table. The annotated data class properties
 * are mapped to table columns.
 *
 * @property tableName The name of the SQLite table. If not specified or empty,
 * the name of the annotated class will be used as the table name.
 *
 * @author Yuang Qiao
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
public annotation class DBRow(val tableName: String = "")