package com.ctrip.sqllin.driver.platform

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString

/**
 * The tools with Linux implementation
 * @author yqiao
 */

@OptIn(ExperimentalForeignApi::class)
internal actual fun bytesToString(bv: CPointer<ByteVar>): String = bv.toKString()

internal actual inline val separatorChar: Char
    get() = '/'