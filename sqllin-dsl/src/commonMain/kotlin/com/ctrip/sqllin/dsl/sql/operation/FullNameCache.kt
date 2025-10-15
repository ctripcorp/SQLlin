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

package com.ctrip.sqllin.dsl.sql.operation

/**
 * Cache for primitive types' qualified name
 * @author Yuang Qiao
 */

internal object FullNameCache {

    val BYTE = Byte::class.qualifiedName!!
    val SHORT = Short::class.qualifiedName!!
    val INT = Int::class.qualifiedName!!
    val LONG = Long::class.qualifiedName!!

    val UBYTE = UByte::class.qualifiedName!!
    val USHORT = UShort::class.qualifiedName!!
    val UINT = UInt::class.qualifiedName!!
    val ULONG = ULong::class.qualifiedName!!

    val FLOAT = Float::class.qualifiedName!!
    val DOUBLE = Double::class.qualifiedName!!

    val BOOLEAN = Boolean::class.qualifiedName!!

    val CHAR = Char::class.qualifiedName!!
    val STRING = String::class.qualifiedName!!

    val BYTE_ARRAY = ByteArray::class.qualifiedName!!
}