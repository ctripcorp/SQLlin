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
 * A marker annotation for DSL functions that are considered advanced and require explicit opt-in.
 *
 * This library contains certain powerful APIs that are intended for special use cases and can
 * lead to unexpected behavior or data integrity issues if used improperly. This annotation
 * is used to protect such APIs and ensure they are used intentionally.
 *
 * Any function marked with [AdvancedInsertAPI] is part of this advanced feature set. To call
 * such a function, you must explicitly acknowledge its use by annotating your own calling
 * function or class with `@OptIn(AdvancedInsertAPI::class)`. This acts as a contract,
 * confirming that you understand the implications of the API.
 *
 * A primary example is an API that allows for the manual insertion of a record with a
 * specific primary key ID (e.g., `INSERT_WITH_ID`), which bypasses the database's automatic
 * ID generation. This is useful for data migration but is unsafe for regular inserts.
 *
 * @see OptIn
 * @see RequiresOptIn
 */
@RequiresOptIn(
    message = "This is a special-purpose API for inserting a record with a predefined value for its `INTEGER PRIMARY KEY` (the rowid-backed key). " +
            "It is intended for use cases like data migration or testing. " +
            "For all standard operations where the database should generate the ID, you must use the `INSERT` API instead.",
)
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
public annotation class AdvancedInsertAPI