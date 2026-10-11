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

package com.ctrip.sqllin.dsl.sql.compiler

/**
 * Returns [value] as it is written to the database: an unsigned value as the number it is, and anything else as it is.
 *
 * SQLite's 64-bit integers hold every `UByte`, `UShort` and `UInt`, so they are stored as their numbers, which SQL
 * compares, orders and sums as Kotlin does. Their serializers, and some drivers, would store their bits as a signed
 * number instead, so that a `UByte` of 200 read back as 200 but compared as -56. A `ULong` above `Long.MAX_VALUE` fits
 * no 64-bit signed integer, so a `ULong` is stored as the `Long` of the same bits.
 */
internal fun storedValue(value: Any?): Any? = when (value) {
    is UByte -> value.toLong()
    is UShort -> value.toLong()
    is UInt -> value.toLong()
    is ULong -> value.toLong()
    else -> value
}
