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