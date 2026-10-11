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
 * Marks the APIs that only work with some SQLite versions or compile-time options, which some platforms SQLlin
 * supports don't have.
 *
 * The SQLite SQLlin runs on depends on the platform: Android's system SQLite, whose version depends on the API level
 * (3.9 at API 24, SQLlin's minimum, 3.32 below API 34, and 3.39 from API 34 on) and which lacks options such as the
 * math functions; the SQLite of the JVM driver; Apple's system SQLite, whose version depends on the OS version; and on
 * Linux and Windows, the SQLite the app links. An API with this annotation fails at runtime where the SQLite lacks what
 * it needs, which its documentation describes, so using one has to be accepted, with
 * `@OptIn(PlatformDependentSQLiteAPI::class)` or the compiler argument
 * `-opt-in=com.ctrip.sqllin.dsl.annotation.PlatformDependentSQLiteAPI`, by an app whose platforms all have it.
 *
 * @see OptIn
 * @see RequiresOptIn
 */
@RequiresOptIn(
    message = "This API only works with some SQLite versions or compile-time options, which some platforms don't have, " +
            "such as Android's system SQLite at lower API levels. Its documentation describes what it needs: use it only " +
            "if all your app's platforms have that.",
    level = RequiresOptIn.Level.ERROR
)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.BINARY)
public annotation class PlatformDependentSQLiteAPI
