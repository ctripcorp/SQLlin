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

package com.ctrip.sqllin.driver

import kotlinx.cinterop.*
import platform.windows.GetTempPathA
import platform.windows.MAX_PATH

/**
 * Windows platform-related functions
 * @author yaqiao
 */

actual fun getPlatformStringPath(): String = memScoped {
    val pathPtr = alloc<CPointerVar<ByteVarOf<Byte>>>()
    val dwRetVal = GetTempPathA(MAX_PATH, pathPtr.value)
    if (dwRetVal > MAX_PATH.toUInt() || dwRetVal == 0u)
        throw IllegalStateException("Get temp path fail")
    pathPtr.value.toString()
}