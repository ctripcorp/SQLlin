/*
 * Copyright (C) 2023 Ctrip.com.
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

@DBRow
@Serializable
data class TestPrimitiveTypeForKSP(
    val testInt: Int,
    val testLong: Long,
    val testShort: Short,
    val testByte: Byte,
    val testFloat: Float,
    val testDouble: Double,
    val testUInt: UInt,
    val testULong: ULong,
    val testUShort: UShort,
    val testUByte: UByte,
    val testBoolean: Boolean,
    val testChar: Char,
    val testString: String,
)